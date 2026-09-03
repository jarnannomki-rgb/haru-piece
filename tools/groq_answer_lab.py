import argparse
import json
import random
import sys
from datetime import datetime
from pathlib import Path
from urllib.error import HTTPError, URLError
from urllib.request import Request, urlopen

import answer_engine_lab as local


try:
    sys.stdin.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_LOG = ROOT / "groq_answer_lab_log.jsonl"
FUNCTION_NAME = "polish-diary-sentence"


def call_polisher(url: str, publishable_key: str, question: str, answer: str, draft: str) -> dict:
    endpoint = f"{url.rstrip('/')}/functions/v1/{FUNCTION_NAME}"
    payload = json.dumps(
        {"question": question, "answer": answer, "draft": draft},
        ensure_ascii=False,
    ).encode("utf-8")
    request = Request(
        endpoint,
        data=payload,
        method="POST",
        headers={
            "apikey": publishable_key,
            "Authorization": f"Bearer {publishable_key}",
            "Content-Type": "application/json; charset=utf-8",
        },
    )
    try:
        with urlopen(request, timeout=30) as response:
            return json.loads(response.read().decode("utf-8"))
    except HTTPError as error:
        detail = error.read().decode("utf-8", errors="replace")
        raise RuntimeError(f"HTTP {error.code}: {detail}") from error
    except URLError as error:
        raise RuntimeError(f"연결 실패: {error.reason}") from error


def run(args: argparse.Namespace) -> None:
    config = local.load_supabase_config()
    if not config:
        raise SystemExit("app/build.gradle.kts에서 Supabase 설정을 찾지 못했습니다.")
    supabase_url, publishable_key = config

    def fresh_questions():
        return local.load_lab_questions(args.sql, args.questions)

    def baseline_questions():
        loaded = local.filter_questions(fresh_questions(), args.keyword, args.topic, args.depth)
        for number, question in enumerate(loaded, start=1):
            question.source_number = number
        return loaded

    questions = baseline_questions()
    all_question_count = len(questions)
    if args.shuffle:
        random.shuffle(questions)

    print(f"질문 {len(questions)}개 로딩됨")
    print("기타 입력 1회마다 Groq 요청 1회 사용")
    print("명령: :q 종료 / :n 다음 / :i 번호선택 / :s 단어 검색 / :t 토픽 필터 / :d 1 깊이 / :all 전체")
    print("번호 예시: :i 2,6,7,21")
    print(f"로그: {args.log}")

    index = 0
    while 0 <= index < len(questions):
        question = questions[index]
        source_number = question.source_number or index + 1
        selection = f" 선택={index + 1}/{len(questions)}" if len(questions) != all_question_count else ""
        print()
        print(
            f"[{source_number}/{all_question_count}]{selection} "
            f"depth={question.depth_level} category={question.category} "
            f"type={question.custom_answer_type} key={question.key}"
        )
        print(question.title)
        answer = input("기타 입력> ").strip()

        if answer == ":q":
            break
        if answer in {":n", ""}:
            index += 1
            continue
        if answer.startswith(":i "):
            try:
                numbers = local.parse_number_spec(answer[3:], all_question_count)
            except ValueError:
                print("번호는 2,6,7,21처럼 입력")
                continue
            questions = [item for item in baseline_questions() if item.source_number in numbers]
            index = 0
            print(f"번호 선택 결과 {len(questions)}개: {', '.join(map(str, numbers))}")
            continue
        if answer.startswith(":s "):
            questions = local.filter_questions(baseline_questions(), answer[3:].strip(), "", None)
            index = 0
            print(f"검색 결과 {len(questions)}개")
            continue
        if answer.startswith(":t "):
            questions = local.filter_questions(baseline_questions(), "", answer[3:].strip(), None)
            index = 0
            print(f"토픽 결과 {len(questions)}개")
            continue
        if answer.startswith(":d "):
            try:
                depth = int(answer[3:].strip())
            except ValueError:
                print("깊이는 숫자로 입력")
                continue
            questions = local.filter_questions(fresh_questions(), args.keyword, args.topic, depth)
            for number, item in enumerate(questions, start=1):
                item.source_number = number
            all_question_count = len(questions)
            index = 0
            print(f"depth {depth} 결과 {len(questions)}개")
            continue
        if answer == ":all":
            questions = baseline_questions()
            all_question_count = len(questions)
            index = 0
            print(f"전체 {len(questions)}개")
            continue

        local_draft = local.from_custom_answer(answer, question)
        try:
            result = call_polisher(supabase_url, publishable_key, question.title, answer, local_draft)
            sentence = str(result.get("sentence") or "").strip()
            needs_review = result.get("needsReview") is True
            print(f"Groq 결과> {sentence}")
            if needs_review:
                print("확인 필요> 입력 의미를 확신하지 못했습니다.")
        except RuntimeError as error:
            sentence = ""
            needs_review = True
            print(f"Groq 오류> {error}")

        review = input("판정(엔터=ok / ㅌ=이상함 / 메모)> ").strip()
        status = "bad" if review == "ㅌ" else "ok" if not review else "memo"
        local.append_log(
            args.log,
            {
                "time": datetime.now().isoformat(timespec="seconds"),
                "status": status,
                "memo": "" if review == "ㅌ" else review,
                "question_key": question.key,
                "question": question.title,
                "category": question.category,
                "depth_level": question.depth_level,
                "custom_answer_type": question.custom_answer_type,
                "answer": answer,
                "local_draft": local_draft,
                "sentence": sentence,
                "needs_review": needs_review,
                "provider": "groq",
                "model": result.get("model") if sentence else None,
            },
        )
        index += 1


def main() -> None:
    parser = argparse.ArgumentParser(description="하루조각 Groq 기타 답변 테스트")
    parser.add_argument("--sql", type=Path, default=local.DEFAULT_SQL)
    parser.add_argument("--questions", type=Path, default=None)
    parser.add_argument("--log", type=Path, default=DEFAULT_LOG)
    parser.add_argument("--keyword", default="")
    parser.add_argument("--topic", default="")
    parser.add_argument("--depth", type=int, default=1)
    parser.add_argument("--shuffle", action="store_true")
    run(parser.parse_args())


if __name__ == "__main__":
    main()
