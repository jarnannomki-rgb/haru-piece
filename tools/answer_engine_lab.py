import argparse
import json
import random
import re
import sys
from dataclasses import dataclass, field
from datetime import datetime
from pathlib import Path
from urllib.parse import urlencode
from urllib.request import Request, urlopen

try:
    sys.stdin.reconfigure(encoding="utf-8")
    sys.stdout.reconfigure(encoding="utf-8")
    sys.stderr.reconfigure(encoding="utf-8")
except Exception:
    pass


ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SQL = ROOT / "HARU_PIECE_SEED_QUESTIONS_V5.sql"
QUESTION_FILE_NAME = "질문표_2차_질문만_파이프형식_중복제거.txt"
DEFAULT_PIPE_QUESTIONS = ROOT.parent / QUESTION_FILE_NAME
LOCAL_PIPE_QUESTIONS = Path(r"D:\\AndroidStudioProjects\\질문표_2차_질문만_파이프형식_중복제거.txt")
DEFAULT_LOG = ROOT / "answer_engine_lab_log.jsonl"
DEFAULT_OPTION_CACHE = ROOT / "answer_engine_option_cache.json"
APP_BUILD_FILE = ROOT / "app" / "build.gradle.kts"

LOCAL_OPTION_OVERRIDES = {
    "오늘 약속은 어떤 쪽에 가까운가요?": [
        ("사람을 만나는 약속", "오늘은 사람을 만나는 약속이 있었다."),
        ("식사 약속", "오늘은 식사 약속이 있었다."),
        ("업무 관련 약속", "오늘은 업무 관련 약속이 있었다."),
        ("가족 관련 약속", "오늘은 가족 관련 약속이 있었다."),
    ],
}


@dataclass
class AnswerOption:
    label: str
    sentence: str
    value: str = ""


@dataclass
class Question:
    key: str
    title: str
    category: str = ""
    depth_level: int = 1
    custom_answer_type: str = "activity"
    source_topic_code: str = ""
    options: list[AnswerOption] = field(default_factory=list)
    source_number: int = 0


FALLBACK_QUESTIONS = [
    Question("sample_weather", "오늘 날씨는 어떻게 느껴졌나요?", "날씨", 1, "weather", "weather"),
    Question("sample_mood", "오늘 기분은 어땠나요?", "기분", 1, "mood", "mood"),
    Question("sample_food", "오늘 식사는 어땠나요?", "식사", 1, "food", "food"),
    Question("sample_activity", "오늘 무엇을 했나요?", "오늘 한 일", 1, "activity", ""),
    Question("sample_thought", "오늘 정리하고 싶은 생각이 있나요?", "생각", 1, "thought", "thought"),
    Question("sample_reason", "그 일을 하게 된 이유가 있었나요?", "생각", 2, "reason", "thought"),
]


def load_questions(path: Path) -> list[Question]:
    if not path.exists():
        return FALLBACK_QUESTIONS

    text = path.read_text(encoding="utf-8", errors="replace")
    blocks = re.findall(r"jsonb_to_recordset\(\$json\$(.*?)\$json\$::jsonb\)", text, re.S)
    question_block = next((block for block in blocks if '"question_key"' in block), None)
    if not question_block:
        return FALLBACK_QUESTIONS

    raw_items = json.loads(question_block)
    questions: list[Question] = []
    seen: set[str] = set()
    for item in raw_items:
        key = item.get("question_key", "")
        title = item.get("question_text", "")
        if not key or not title or key in seen:
            continue
        seen.add(key)
        questions.append(
            Question(
                key=key,
                title=title,
                category=item.get("category") or "",
                depth_level=int(item.get("depth_level") or 1),
                custom_answer_type=item.get("custom_answer_type") or "activity",
                source_topic_code=item.get("source_topic_code") or "",
            )
        )
    return questions or FALLBACK_QUESTIONS


def load_pipe_questions(path: Path) -> list[Question]:
    if not path.exists():
        return FALLBACK_QUESTIONS

    text = path.read_text(encoding="utf-8-sig", errors="replace")
    questions: list[Question] = []
    seen: set[tuple[str, str, int, str]] = set()
    for line_no, line in enumerate(text.splitlines(), start=1):
        line = line.strip()
        if not line or line.startswith("#") or line.startswith("--"):
            continue

        parts = [part.strip() for part in line.split("|")]
        if len(parts) < 4:
            continue

        category, depth_text, answer_type, title = parts[:4]
        if line_no == 1 and ("카테고리" in category or "질문" in title):
            continue
        if not title:
            continue

        try:
            depth_level = int(depth_text)
        except ValueError:
            depth_level = 1

        source_topic_code = topic_code_for(category)
        key_base = source_topic_code or category or "question"
        key = f"pipe_{key_base}_{len(questions) + 1:04d}"
        marker = (category, title, depth_level, answer_type)
        if marker in seen:
            continue
        seen.add(marker)

        questions.append(
            Question(
                key=key,
                title=title,
                category=category,
                depth_level=depth_level,
                custom_answer_type=answer_type or "activity",
                source_topic_code=source_topic_code,
            )
        )
    return questions or FALLBACK_QUESTIONS


def load_lab_questions(sql_path: Path, questions_path: Path | None) -> list[Question]:
    if questions_path is not None:
        return load_pipe_questions(questions_path)
    for candidate in (DEFAULT_PIPE_QUESTIONS, ROOT / QUESTION_FILE_NAME, LOCAL_PIPE_QUESTIONS):
        if candidate.exists():
            return load_pipe_questions(candidate)
    return load_questions(sql_path)


def load_option_cache(path: Path) -> dict[str, list[dict]]:
    if not path.exists():
        return {}
    try:
        data = json.loads(path.read_text(encoding="utf-8"))
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def save_option_cache(path: Path, cache: dict[str, list[dict]]) -> None:
    path.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8")


def load_supabase_config() -> tuple[str, str] | None:
    if not APP_BUILD_FILE.exists():
        return None
    text = APP_BUILD_FILE.read_text(encoding="utf-8", errors="replace")
    url_match = re.search(r'SUPABASE_URL",\s*"\\\"([^"]+)\\\""', text)
    key_match = re.search(r'SUPABASE_PUBLISHABLE_KEY",\s*"\\\"([^"]+)\\\""', text)
    if not url_match or not key_match:
        return None
    return url_match.group(1), key_match.group(1)


def supabase_get(url: str, key: str, table: str, params: dict[str, str]) -> list[dict]:
    request_url = f"{url}/rest/v1/{table}?{urlencode(params)}"
    request = Request(
        request_url,
        headers={
            "apikey": key,
            "Authorization": f"Bearer {key}",
            "Accept": "application/json",
        },
    )
    with urlopen(request, timeout=8) as response:
        payload = json.loads(response.read().decode("utf-8"))
        return payload if isinstance(payload, list) else []


def hydrate_question_options(
    question: Question,
    cache: dict[str, list[dict]],
    config: tuple[str, str] | None,
    cache_path: Path,
) -> None:
    override = LOCAL_OPTION_OVERRIDES.get(question.title)
    if override:
        question.options = [
            AnswerOption(label=label, sentence=sentence, value=f"local_{index}")
            for index, (label, sentence) in enumerate(override, start=1)
        ]
        return
    cached = cache.get(question.title)
    if cached:
        question.options = [
            AnswerOption(
                label=str(item.get("label") or ""),
                sentence=str(item.get("sentence") or ""),
                value=str(item.get("value") or ""),
            )
            for item in cached
            if item.get("label") and item.get("sentence")
        ]
        return
    if config is None:
        return

    url, key = config
    try:
        rows = supabase_get(
            url,
            key,
            "questions",
            {
                "select": "id",
                "question_text": f"eq.{question.title}",
                "limit": "5",
            },
        )
        for row in rows:
            question_id = str(row.get("id") or "")
            if not question_id:
                continue
            option_rows = supabase_get(
                url,
                key,
                "question_options",
                {
                    "select": "label,diary_sentence,answer_value",
                    "question_id": f"eq.{question_id}",
                    "order": "option_order.asc",
                },
            )
            options = [
                AnswerOption(
                    label=str(item.get("label") or ""),
                    sentence=str(item.get("diary_sentence") or ""),
                    value=str(item.get("answer_value") or ""),
                )
                for item in option_rows
                if item.get("label") and item.get("diary_sentence")
            ]
            if not options:
                continue
            question.options = options
            cache[question.title] = [
                {"label": option.label, "sentence": option.sentence, "value": option.value}
                for option in options
            ]
            save_option_cache(cache_path, cache)
            return
    except Exception as error:
        print(f"보기 불러오기 실패: {error}")


def topic_code_for(category: str) -> str:
    return {
        "식사": "food",
        "기분": "mood",
        "일": "work",
        "일/학교": "work",
        "사람": "people",
        "건강": "health",
        "컨디션": "health",
        "날씨": "weather",
        "소비": "spending",
        "운동": "exercise",
        "가족": "family",
        "집": "home",
        "취미": "hobby",
        "휴식": "rest",
        "공부": "study",
        "이동": "movement",
        "약속": "appointment",
        "생각": "thought",
        "오늘 한 일": "activity",
    }.get(category.strip(), category.strip())


def from_custom_answer(raw_answer: str, question: Question) -> str:
    input_text = clean_input(raw_answer)
    if not input_text:
        return "오늘은 아무것도 남기지 않은 하루였다."

    phrase = strip_leading_self(normalize_polite_ending(input_text))
    negative = negative_sentence(phrase, question)
    if negative:
        return polish(negative)
    aware = question_aware_sentence(input_text, phrase, question)
    if aware:
        return polish(aware)
    framed = sentence_from_option_frame(input_text, phrase, question)
    if framed:
        return polish(framed)
    if looks_complete(phrase):
        return polish(repair_by_question(raw_answer, question, with_today_prefix_if_needed(phrase)))

    context = detect_context(phrase, question)
    if context == "reason":
        sentence = reason_sentence(phrase)
    elif context == "thought":
        sentence = thought_sentence(phrase)
    elif context == "food":
        sentence = food_sentence(phrase, question)
    elif context == "drink":
        sentence = drink_sentence(phrase)
    elif context == "sleep":
        sentence = sleep_sentence(phrase)
    elif context == "weather":
        sentence = weather_sentence(phrase)
    elif context == "mood":
        sentence = state_sentence("기분", phrase)
    elif context == "condition":
        sentence = state_sentence("컨디션", phrase)
    elif context == "movement":
        sentence = movement_sentence(phrase)
    elif context == "spending":
        sentence = spending_sentence(phrase)
    elif context == "work":
        sentence = work_sentence(phrase)
    elif context == "exercise":
        sentence = exercise_sentence(phrase)
    elif context == "hobby":
        sentence = hobby_sentence(phrase)
    elif context == "rest":
        sentence = rest_sentence(phrase)
    elif context == "study":
        sentence = study_sentence(phrase)
    elif context == "appointment":
        sentence = appointment_sentence(phrase)
    elif context == "family":
        sentence = family_sentence(phrase)
    elif context == "people":
        sentence = people_sentence(phrase)
    elif context == "home":
        sentence = home_sentence(phrase)
    else:
        sentence = activity_sentence(phrase)
    return polish(repair_by_question(raw_answer, question, sentence))


def question_aware_sentence(raw_answer: str, phrase: str, question: Question) -> str | None:
    category = question.category
    answer_type = question.custom_answer_type
    if category == "식사" or answer_type == "food":
        return food_aware_sentence(raw_answer, question)
    if category == "기분" or answer_type == "mood":
        return mood_aware_sentence(raw_answer, question)
    if category == "일" or answer_type == "work":
        return work_aware_sentence(raw_answer, question)
    if category == "사람" or answer_type == "people":
        return people_aware_sentence(raw_answer, question)
    if category == "소비" or answer_type == "spending":
        return spending_aware_sentence(raw_answer, question)
    if category == "운동" or answer_type == "exercise":
        return exercise_aware_sentence(raw_answer, question)
    if category == "건강" or answer_type in {"health", "condition"}:
        return health_aware_sentence(raw_answer, question)
    if category == "날씨" or answer_type == "weather":
        return weather_aware_sentence(raw_answer, question)
    if category == "가족" or answer_type == "family":
        return family_aware_sentence(raw_answer, question)
    if category == "집" or answer_type == "home":
        return home_aware_sentence(raw_answer, question)
    if category == "취미" or answer_type == "hobby":
        return hobby_aware_sentence(raw_answer, question)
    if category == "휴식" or answer_type == "rest":
        return rest_aware_sentence(raw_answer, question)
    if category == "공부" or answer_type == "study":
        return study_aware_sentence(raw_answer, question)
    if category == "이동" or answer_type == "movement":
        return movement_aware_sentence(raw_answer, question)
    if category == "약속" or answer_type == "appointment":
        return appointment_aware_sentence(raw_answer, question)
    if category == "생각" or answer_type == "thought":
        return thought_aware_sentence(raw_answer, question)
    return None


def food_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "식사가", "식사는", "식사량이", "식사량은", "끼니가", "끼니는")
    compact = answer.replace(" ", "")
    if "기억나는" in title or "기억나는 게" in title:
        return f"오늘 기억에 남는 음식은 {as_past_identity(answer)}"
    if "식사량" in title:
        return f"오늘 식사량은 {normalize_amount_predicate(answer)}"
    if "주로 어떻게 해결" in title:
        if has_any(compact, "혼밥", "혼자"):
            return "오늘은 혼자 식사했다"
        if has_any(compact, "배달"):
            return "오늘은 배달로 식사했다"
        if has_any(compact, "외식", "밖"):
            return "오늘은 밖에서 식사했다"
        return f"오늘은 {normalize_action_predicate(answer, '식사했다')}"
    if "어떻게 챙" in title:
        if has_any(compact, "먹", "챙"):
            return f"오늘은 식사를 {normalize_predicate(answer)}"
        return f"오늘은 {normalize_action_predicate(answer, '식사를 챙겼다')}"
    return f"오늘 식사는 {normalize_predicate(answer)}"


def mood_aware_sentence(raw_answer: str, question: Question) -> str:
    state = normalize_predicate(strip_answer_subject(raw_answer, "기분이", "기분은", "마음이", "마음은", "감정이", "감정은"))
    title = question.title
    if "변화" in title and clean_input(raw_answer).replace(" ", "") in {"아니", "아니야", "없어", "없었어"}:
        return "오늘은 기분 변화가 없었다"
    if "가장 오래 이어진" in title:
        return f"오늘 가장 오래 이어진 기분은 {state}"
    if "마음 상태" in title:
        return f"오늘의 마음은 {state}"
    if "감정" in title:
        return f"오늘의 감정은 {state}"
    return f"오늘의 기분은 {state}"


def work_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "일이", "일은", "업무가", "업무는")
    compact = answer.replace(" ", "")
    if "해야 할 일" in title:
        if has_any(compact, "다끝", "전부끝", "모두끝", "다했", "전부했", "모두했"):
            return "오늘은 해야 할 일을 모두 끝냈다"
        if "끝" in compact:
            return "오늘은 해야 할 일을 끝냈다"
        if has_any(compact, "남", "못끝"):
            return f"오늘은 해야 할 일이 {normalize_predicate(answer)}"
        if has_any(compact, "진행", "하고있", "하는중"):
            return "오늘은 해야 할 일을 진행하고 있었다"
        return f"오늘은 해야 할 일을 {normalize_action_predicate(answer, '처리했다')}"
    if "흐름" in title:
        return f"오늘 일의 흐름은 {normalize_predicate(answer)}"
    if "집중" in title:
        if has_any(compact, "집중좋", "잘됐", "잘됨"):
            return "오늘 집중은 잘됐다"
        return f"오늘 집중은 {normalize_predicate(answer)}"
    if has_any(title, "눈에 띄는 일", "기억나는 일"):
        return f"오늘 일에서 기억나는 것은 {as_past_identity(answer)}"
    if has_any(title, "어떤 편", "어땠", "비교"):
        return f"오늘 일은 {normalize_predicate(answer)}"
    return work_sentence(normalize_polite_ending(clean_input(answer)))


def people_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "사람들과의 시간이", "사람들과의 시간은", "사람들과의 분위기가", "사람들과의 분위기는")
    compact = answer.replace(" ", "")
    if "대화나 연락" in title or "연락이나 대화" in title:
        if has_any(compact, "많이함", "많이했"):
            return "오늘은 대화나 연락을 많이 했다"
        if has_any(compact, "조금함", "조금했"):
            return "오늘은 대화나 연락을 조금 했다"
        return f"오늘 대화나 연락은 {normalize_predicate(answer)}"
    if "사람을 만나는 일" in title:
        if has_any(compact, "바빴", "바빠"):
            return "오늘은 사람을 만나느라 바빴다"
        return f"오늘 사람을 만나는 일은 {normalize_predicate(answer)}"
    if "분위기" in title:
        return f"오늘 사람들과의 분위기는 {normalize_predicate(answer)}"
    if "기억나는 일" in title:
        if compact in {"아니", "아니야", "없어", "없었어", "없음"}:
            return "오늘은 사람 때문에 특별히 기억나는 일이 없었다"
        return f"오늘 사람 때문에 기억나는 일은 {as_past_identity(answer)}"
    if "시간" in title:
        return f"오늘 사람들과의 시간은 {normalize_predicate(answer)}"
    return f"오늘 사람들과의 일은 {normalize_predicate(answer)}"


def spending_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "소비가", "소비는", "지출이", "지출은")
    compact = answer.replace(" ", "")
    if has_any(title, "눈에 띄는 소비", "기억나는 소비"):
        return f"오늘 가장 눈에 띈 소비는 {as_past_identity(answer)}"
    if has_any(title, "돈을 쓴 곳", "지출한 곳"):
        return f"오늘은 {with_location_particle(normalize_nominal_input(answer))} 돈을 썼다"
    if "계획한 범위" in title:
        if has_any(compact, "아니", "벗어", "초과", "충동"):
            return "오늘 지출은 계획한 범위를 벗어났다"
        if has_any(compact, "맞", "안", "범위"):
            return "오늘 지출은 계획한 범위 안이었다"
        return f"오늘 지출은 {normalize_predicate(answer)}"
    if has_any(title, "평소와 비교", "어떤 편", "소비를 평소"):
        if has_any(compact, "많이샀", "많이썼"):
            return "오늘은 평소보다 소비를 많이 했다"
        if has_any(compact, "적게샀", "적게썼"):
            return "오늘은 평소보다 소비를 적게 했다"
        return f"오늘 소비는 {normalize_predicate(answer)}"
    if has_any(title, "무엇을 샀", "구매한 것"):
        return f"오늘은 {with_object_particle(normalize_nominal_input(answer))} 샀다"
    return f"오늘 소비는 {normalize_predicate(answer)}"


def exercise_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "활동량이", "활동량은", "운동이", "운동은")
    compact = answer.replace(" ", "")
    if "기분" in compact and has_any(compact, "좋", "최고"):
        return "오늘은 운동해서 기분이 좋았다"
    if has_any(title, "방식", "어떤 운동"):
        return activity_sentence(normalize_polite_ending(clean_input(answer)))
    if "운동 계획" in title:
        if has_any(compact, "못", "안함", "실패"):
            return "오늘은 운동 계획을 지키지 못했다"
        if has_any(compact, "잘", "완료", "지킴"):
            return "오늘은 운동 계획을 잘 지켰다"
        return f"오늘 운동 계획은 {normalize_predicate(answer)}"
    if has_any(title, "활동량", "얼마나 움직"):
        if has_any(compact, "하루죙일", "하루종일", "종일"):
            return "오늘은 하루 종일 몸을 움직였다"
        if has_any(compact, "많이움직", "많이활동"):
            return "오늘은 몸을 많이 움직였다"
        return f"오늘 활동량은 {normalize_amount_predicate(answer)}"
    return f"오늘 운동은 {normalize_predicate(answer)}"


def health_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "몸이", "몸은", "컨디션이", "컨디션은")
    compact = answer.replace(" ", "")
    if has_any(title, "불편한 곳", "아픈 곳", "통증"):
        if compact in {"이곳저곳", "여기저기", "온몸"}:
            return "오늘은 몸 이곳저곳이 불편했다"
        if "없" in compact:
            return "오늘은 몸에 불편한 곳이 없었다"
        return f"오늘은 {with_subject_particle(normalize_nominal_input(answer))} 불편했다"
    if "몸을 챙기는 일" in title and has_any(compact, "활기참", "활기차", "활기"):
        return "오늘 몸 상태는 활기찼다"
    return f"오늘 컨디션은 {normalize_predicate(answer)}"


def weather_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "날씨가", "날씨는")
    compact = answer.replace(" ", "")
    if "영향" in title:
        if "없" in compact or has_any(compact, "아니", "그렇지않"):
            return "오늘은 날씨의 영향을 받지 않았다"
        if has_any(compact, "많이줌", "많이받", "큰영향"):
            return "오늘은 날씨의 영향을 많이 받았다"
        if has_any(compact, "조금줌", "조금받"):
            return "오늘은 날씨의 영향을 조금 받았다"
        if has_any(compact, "행복", "기분좋", "즐거"):
            return "오늘은 날씨 덕분에 행복했다" if "행복" in compact else "오늘은 날씨 덕분에 기분이 좋았다"
        return f"오늘은 날씨의 영향을 {normalize_predicate(answer)}"
    if "계절감" in title:
        return f"오늘은 {normalize_nominal_input(answer)}다운 날씨였다"
    return f"오늘 날씨는 {normalize_predicate(answer)}"


def family_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "가족과의 시간이", "가족과의 시간은", "가족과의 분위기가", "가족과의 분위기는")
    compact = answer.replace(" ", "")
    if "어떤 일이" in title:
        if has_any(compact, "밥먹", "식사"):
            return "오늘은 가족과 밥을 먹었다"
        return f"오늘은 가족과 {action_clause(answer)}"
    if "분위기" in title:
        return f"오늘 가족과의 분위기는 {normalize_predicate(answer)}"
    if "관련된 일정" in title:
        if "없" in compact:
            return "오늘은 가족과 관련된 일정이 없었다"
        if compact in {"가족식사", "식사", "같이식사"}:
            return "오늘은 가족 식사 일정이 있었다"
        if compact in {"산책", "가족산책"}:
            return "오늘은 가족과 산책했다"
        if compact in {"즐거움", "즐거웠어", "즐거워"}:
            return "오늘은 가족과 즐거운 시간을 보냈다"
        return f"오늘은 가족과 {as_past_identity(answer)} 일정이 있었다"
    if "평소와 비교" in title:
        return f"오늘 가족과의 시간은 {normalize_predicate(answer)}"
    return f"오늘 가족과 보낸 시간은 {normalize_predicate(answer)}"


def home_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "집에서의 시간이", "집에서의 시간은", "집안일이", "집안일은")
    compact = answer.replace(" ", "")
    if "어떻게 보내" in title:
        return f"오늘은 집에서 {adverbial_or_predicate(answer, '보냈다')}"
    if "집안일" in title:
        if compact in {"빨래", "세탁"}:
            return "오늘은 빨래를 했다"
        return f"오늘은 집안일을 {normalize_action_predicate(answer, '했다')}"
    if "가장 가까운 모습" in title:
        return f"오늘 집에서의 모습은 {as_past_identity(answer)}"
    if "보낸 시간" in title:
        return f"오늘 집에서 보낸 시간은 {normalize_predicate(answer)}"
    if "평소와 다른 일" in title:
        if "없" in compact:
            return "오늘은 집에서 평소와 다른 일이 없었다"
        return f"오늘 집에서 평소와 달랐던 일은 {as_past_identity(answer)}"
    return f"오늘은 집에서 {normalize_predicate(answer)}"


def hobby_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "취미 시간이", "취미 시간은", "취미가", "취미는")
    compact = answer.replace(" ", "")
    if has_any(title, "취미로 한 일", "여유 시간에 무엇"):
        return f"오늘은 {action_clause(answer)}"
    if "평소와 비교" in title:
        if has_any(compact, "더많은시간", "오래보냈", "더오래"):
            return "오늘은 평소보다 취미에 더 많은 시간을 보냈다"
        if has_any(compact, "더적은시간", "짧게보냈", "덜했"):
            return "오늘은 평소보다 취미에 적은 시간을 보냈다"
        return f"오늘 취미 시간은 {normalize_predicate(answer)}"
    if "콘텐츠나 활동" in title:
        if "없" in compact:
            return "오늘은 관심이 간 콘텐츠나 활동이 없었다"
        return f"오늘 관심이 간 활동은 {as_past_identity(answer)}"
    if "즐긴 방식" in title:
        if compact in {"혼자즐김", "혼자", "혼자서"}:
            return "오늘은 혼자 취미를 즐겼다"
        if has_any(compact, "쉬", "보", "했", "즐"):
            return f"오늘은 {action_clause(answer)}"
        return f"오늘은 {normalize_nominal_input(answer)} 방식으로 취미를 즐겼다"
    return f"오늘 취미 시간은 {normalize_predicate(answer)}"


def rest_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "휴식 시간이", "휴식 시간은")
    compact = answer.replace(" ", "")
    if "어떻게 쉬" in title:
        return f"오늘은 {adverbial_or_predicate(answer, '쉬었다')}"
    if "휴식 시간" in title:
        return f"오늘 휴식 시간은 {normalize_duration_predicate(answer)}"
    if "쉬는 방식" in title:
        if compact in {"집에서휴식", "집에서쉼", "집에서쉬기"}:
            return "오늘은 집에서 쉬었다"
        return f"오늘은 {action_clause(answer, rest_mode=True)}"
    if "잘 쉬고" in title:
        if has_any(compact, "평소보다좋", "더잘쉼", "잘쉼"):
            return "오늘은 평소보다 잘 쉬었다"
        if compact in {"편하게", "푹", "느긋하게"}:
            return f"오늘은 {compact} 쉬었다"
        return f"오늘은 {normalize_predicate(answer)}"
    if "멈추는 시간" in title:
        if "없" in compact:
            return "오늘은 잠깐 멈춰 쉬는 시간이 없었다"
        if re.search(r"\d+\s*(?:시간|분)", answer):
            return f"오늘은 {answer} 정도 쉬었다"
        return "오늘은 잠깐 멈춰 쉬는 시간이 있었다"
    return f"오늘은 {normalize_predicate(answer)}"


def study_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "공부가", "공부는", "집중도가", "집중도는")
    compact = answer.replace(" ", "")
    if "공부나 배움" in title:
        if "없" in compact:
            return "오늘은 공부하거나 새로 배운 내용이 없었다"
        return f"오늘 공부는 {normalize_predicate(answer)}"
    if "쓴 시간" in title:
        return f"오늘 공부한 시간은 {normalize_duration_identity(answer)}"
    if "진행되고" in title:
        if has_any(compact, "잘되고있", "순조롭", "잘진행"):
            return "오늘 공부는 잘 진행되고 있었다"
        if has_any(compact, "막혔", "막혀"):
            return "오늘 공부는 막혀 있었다"
        return f"오늘 공부는 {normalize_predicate(answer)}"
    if "배운 내용" in title:
        content = normalize_study_content(answer)
        return f"오늘은 {with_object_particle(content)} 배웠다"
    if "집중도" in title:
        if has_any(compact, "잘됐", "잘됨", "집중잘"):
            return "오늘 공부 집중도는 좋았다"
        return f"오늘 공부 집중도는 {normalize_predicate(answer)}"
    return f"오늘 공부는 {normalize_predicate(answer)}"


def movement_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "이동이", "이동은", "이동 시간이", "이동 시간은")
    compact = answer.replace(" ", "")
    if "이동 방식" in title:
        return movement_method_sentence(answer)
    if "이동 시간" in title:
        if has_any(compact, "바빴", "바빠", "바빳"):
            return "오늘은 이동하느라 바빴다"
        return f"오늘 이동 시간은 {normalize_duration_predicate(answer)}"
    if "편한 편" in title:
        if compact in {"완전", "완전히", "매우", "엄청"}:
            return "오늘 이동은 완전히 편했다"
        return f"오늘 이동은 {normalize_predicate(answer)}"
    if "평소와 다른 이동" in title:
        if "없" in compact:
            return "오늘은 평소와 다른 이동이 없었다"
        return f"오늘 평소와 달랐던 이동은 {as_past_identity(answer)}"
    return f"오늘 이동은 {normalize_predicate(answer)}"


def appointment_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "약속이", "약속은", "일정이", "일정은")
    compact = answer.replace(" ", "")
    if "계획대로" in title:
        if compact in {"응", "네", "예", "맞아", "그래"}:
            return "오늘 약속 일정은 계획대로 진행됐다"
        if has_any(compact, "일정대로", "계획대로", "가고있", "진행"):
            return "오늘 약속 일정은 계획대로 진행되고 있었다"
        if has_any(compact, "아니", "변경", "취소"):
            return "오늘 약속 일정은 계획과 달라졌다"
        return f"오늘 약속 일정은 {normalize_predicate(answer)}"
    if "예정에 없던 만남" in title:
        if "없" in compact:
            return "오늘은 예정에 없던 만남이 생기지 않았다"
        return "오늘은 예정에 없던 만남이 생겼다"
    if "사람을 만나는 일정" in title:
        return f"오늘 사람을 만나는 일정은 {normalize_predicate(answer)}"
    return f"오늘 약속이나 만남은 {normalize_predicate(answer)}"


def thought_aware_sentence(raw_answer: str, question: Question) -> str:
    title = question.title
    answer = strip_answer_subject(raw_answer, "생각이", "생각은")
    compact = answer.replace(" ", "")
    if "많은 편" in title:
        if has_any(compact, "별로없", "많지않", "거의없"):
            return "오늘은 생각이 많지 않았다"
        return f"오늘은 생각이 {normalize_amount_predicate(answer)}"
    if "자주 떠오르는" in title:
        if "없" in compact:
            return "오늘 머릿속에 자주 떠오른 것은 없었다"
        return f"오늘 머릿속에 자주 떠오른 것은 {as_past_identity(answer)}"
    if "정리하고 싶은" in title:
        if "없" in compact:
            return "오늘은 특별히 정리하고 싶은 생각이 없었다"
        return f"오늘은 {normalize_nominal_input(answer)}에 대한 생각을 정리하고 싶었다"
    if "새로운 생각" in title:
        if "없" in compact:
            return "오늘은 새롭게 떠오른 생각이 없었다"
        return f"오늘 새롭게 떠오른 생각은 {as_past_identity(answer)}"
    if "평소와 비교" in title:
        return f"오늘 생각은 평소와 {normalize_comparison_predicate(answer)}"
    return f"오늘은 생각이 {normalize_predicate(answer)}"


def strip_answer_subject(raw: str, *subjects: str) -> str:
    text = clean_input(raw)
    text = strip_leading_self(text)
    for subject in sorted(subjects, key=len, reverse=True):
        if text.startswith(subject):
            return text[len(subject):].strip()
    return text.strip()


def normalize_predicate(raw: str) -> str:
    text = strip_leading_self(normalize_polite_ending(clean_input(raw)))
    direct = {
        "최고": "최고였다",
        "최고야": "최고였다",
        "최고였어": "최고였다",
        "쓸쓸함": "쓸쓸했다",
        "외로움": "외로웠다",
        "즐거움": "즐거웠다",
        "행복함": "행복했다",
        "평균적": "평균적이었다",
        "평균적이야": "평균적이었다",
        "평소와 같아": "평소와 같았다",
        "나쁘지 않음": "나쁘지 않았다",
        "다 끝냄": "모두 끝냈다",
        "많이함": "많이 했다",
        "그닥": "그다지 좋지 않았다",
        "많다": "많았다",
        "적다": "적었다",
        "좋다": "좋았다",
        "힘들다": "힘들었다",
        "바쁘다": "바빴다",
        "괜찮다": "괜찮았다",
        "같다": "같았다",
        "높다": "높았다",
        "낮다": "낮았다",
        "있다": "있었다",
        "없다": "없었다",
        "좋았지": "좋았다",
        "편했지": "편했다",
        "즐거웠어": "즐거웠다",
        "빡셌어": "빡셌다",
        "길었어": "길었다",
        "어려웠어": "어려웠다",
        "바빳어": "바빴다",
        "많네": "많았다",
        "없었네": "없었다",
        "비슷해": "비슷했다",
        "크게 다르지 않아": "크게 다르지 않았다",
        "어려워": "어려웠다",
        "쉬워": "쉬웠다",
        "재미있어": "재미있었다",
        "재밌어": "재미있었다",
        "편해": "편했다",
        "불편해": "불편했다",
        "편안해": "편안했다",
        "지루해": "지루했다",
        "심심해": "심심했다",
        "복잡해": "복잡했다",
        "어색해": "어색했다",
        "화목해": "화목했다",
        "행복해": "행복했다",
        "우울해": "우울했다",
        "피곤해": "피곤했다",
        "부족해": "부족했다",
        "막혔어": "막혀 있었다",
        "오래 걸렸어": "오래 걸렸다",
        "취소됐어": "취소됐다",
        "부담스러워": "부담스러웠다",
        "더워": "더웠다",
        "추워": "추웠다",
        "흐려": "흐렸다",
        "맑아": "맑았다",
        "무거워": "무거웠다",
        "보냈어": "보냈다",
        "좋음": "좋았다",
        "안좋음": "좋지 않았다",
        "많음": "많았다",
        "적음": "적었다",
        "어려움": "어려웠다",
        "쉬움": "쉬웠다",
        "밝아": "밝았다",
    }
    if text in direct:
        return direct[text]
    replacements = (
        ("보냈어", "보냈다"),
        ("샀어", "샀다"),
        ("썼어", "썼다"),
        ("바빴어", "바빴다"),
        ("바빠", "바빴다"),
        ("힘들어", "힘들었다"),
        ("쓸쓸해", "쓸쓸했다"),
        ("외로워", "외로웠다"),
        ("즐거워", "즐거웠다"),
        ("같아", "같았다"),
        ("많아", "많았다"),
        ("적어", "적었다"),
        ("좋네", "좋았다"),
        ("우중충하네", "우중충했다"),
        ("좋았지", "좋았다"),
        ("편했지", "편했다"),
        ("즐거웠어", "즐거웠다"),
        ("빡셌어", "빡셌다"),
        ("길었어", "길었다"),
        ("어려웠어", "어려웠다"),
        ("바빳어", "바빴다"),
        ("많네", "많았다"),
        ("없었네", "없었다"),
        ("비슷해", "비슷했다"),
        ("다르지 않아", "다르지 않았다"),
    )
    for ending, replacement in replacements:
        if text.endswith(ending):
            return text[:-len(ending)] + replacement
    if text.endswith("함") and len(text) > 1:
        return text[:-1].rstrip() + "했다"
    if looks_complete(text):
        return text
    return as_past_identity(text)


def normalize_amount_predicate(raw: str) -> str:
    text = clean_input(raw)
    compact = text.replace(" ", "")
    if has_any(compact, "엄청많", "아주많"):
        return "엄청 많았다"
    if compact in {"많이", "많음"}:
        return "많았다"
    if compact in {"조금", "적게", "적음"}:
        return "적었다"
    return normalize_predicate(text)


def normalize_action_predicate(raw: str, fallback: str) -> str:
    text = normalize_predicate(raw)
    if looks_complete(text):
        return text
    return fallback


def action_clause(raw: str, rest_mode: bool = False) -> str:
    text = clean_input(raw).strip()
    compact = text.replace(" ", "")
    direct = {
        "밥먹기": "밥을 먹었다",
        "밥먹었어": "밥을 먹었다",
        "식사": "식사했다",
        "종이접기": "종이 접기를 했다",
        "집에서쉬었지": "집에서 쉬었다",
        "집에서쉬었어": "집에서 쉬었다",
        "잠자기": "잠을 잤다",
        "자기": "잠을 잤다",
        "산책": "산책했다",
        "운동": "운동했다",
        "내개인취미활동": "개인 취미 활동을 했다",
        "개인취미활동": "개인 취미 활동을 했다",
        "노래부르기": "노래를 불렀다",
    }
    if compact in direct:
        return direct[compact]
    if rest_mode and has_any(compact, "편하게", "가만히", "푹"):
        return adverbial_or_predicate(text, "쉬었다")
    predicate = normalize_predicate(text)
    if looks_complete(predicate):
        return predicate
    noun = normalize_nominal_input(text)
    return f"{with_object_particle(noun)} 했다"


def adverbial_or_predicate(raw: str, verb: str) -> str:
    text = clean_input(raw).strip()
    predicate = normalize_predicate(text)
    if looks_complete(predicate) and predicate != as_past_identity(text):
        return predicate
    compact = text.replace(" ", "")
    adverbs = {
        "편하게": "편하게",
        "그냥편하게": "그냥 편하게",
        "푹": "푹",
        "느긋하게": "느긋하게",
        "조용히": "조용히",
    }
    if compact in adverbs:
        return f"{adverbs[compact]} {verb}"
    return f"{text} {verb}"


def normalize_duration_predicate(raw: str) -> str:
    text = clean_input(raw).strip()
    compact = text.replace(" ", "")
    if re.search(r"\d+\s*(?:시간|분)", text):
        return normalize_duration_identity(text)
    if has_any(compact, "엄청길", "아주길"):
        return "엄청 길었다"
    return normalize_predicate(text)


def normalize_duration_identity(raw: str) -> str:
    text = clean_input(raw).strip()
    text = re.sub(r"^(?:한|약)\s*", "약 ", text)
    if not text.startswith("약 ") and re.search(r"\d", text):
        text = "약 " + text
    return as_past_identity(text)


def normalize_study_content(raw: str) -> str:
    text = clean_input(raw).strip().replace("화하", "화학")
    text = re.sub(r"(?:이었어|였어|이야|야)$", "", text).strip()
    parts = [part.strip() for part in re.split(r"[,/]", text) if part.strip()]
    if len(parts) == 2:
        return f"{parts[0]}와 {parts[1]}"
    return text


def movement_method_sentence(raw: str) -> str:
    text = clean_input(raw).strip()
    compact = text.replace(" ", "")
    if compact in {"자차", "자가용", "자동차", "차"}:
        return "오늘은 자차로 이동했다"
    if compact in {"도보", "걸어서", "걷기"}:
        return "오늘은 걸어서 이동했다"
    if compact in {"대중교통", "버스", "지하철", "택시"}:
        return f"오늘은 {text}{'로' if not has_final_consonant(text[-1]) else '으로'} 이동했다"
    return f"오늘은 {text}{'로' if not has_final_consonant(text[-1]) else '으로'} 이동했다"


def normalize_comparison_predicate(raw: str) -> str:
    text = clean_input(raw).strip()
    compact = text.replace(" ", "")
    if has_any(compact, "크게다르지않", "별로다르지않"):
        return "크게 다르지 않았다"
    if has_any(compact, "비슷"):
        return "비슷했다"
    return normalize_predicate(text)


def as_past_identity(raw: str) -> str:
    noun = normalize_nominal_input(raw)
    if not noun:
        return "특별한 것이 없었다"
    if looks_complete(noun):
        return noun
    return noun + ("이었다" if has_final_consonant(noun[-1]) else "였다")


def with_location_particle(value: str) -> str:
    value = value.strip()
    if not value:
        return value
    if value.endswith(("에", "에서")):
        return value
    return value + "에"


def sentence_from_option_frame(raw_input: str, phrase: str, question: Question) -> str | None:
    frame = infer_sentence_frame(question.options)
    if frame is None:
        return None
    prefix, suffix, _sample_slot, _support = frame
    context = detect_context(phrase, question)
    copula_slot = ("이었다" in suffix or "였다" in suffix) and phrase.endswith("이다")
    if suffix and looks_complete(phrase) and not copula_slot:
        slot = phrase
        suffix = ""
    elif suffix:
        slot = normalize_nominal_input(raw_input)
    else:
        slot = clause_for_frame(phrase, context)
    slot = slot.strip()
    if not slot:
        return None
    return prefix + slot + adjust_leading_particle(suffix, slot)


def sentence_from_option(option: AnswerOption, question: Question) -> str:
    original = clean_input(option.sentence)
    sanitized = sanitize_option_sentence(original, option)
    frame = infer_sentence_frame(question.options)
    if sanitized != original and frame and has_meaningful_subject(frame[0]):
        phrase = strip_leading_self(normalize_polite_ending(clean_input(option.label)))
        framed = sentence_from_option_frame(option.label, phrase, question)
        if framed:
            return polish(framed)
    return polish(sanitized)


def infer_sentence_frame(options: list[AnswerOption]) -> tuple[str, str, str, int] | None:
    frames: list[tuple[str, str, str]] = []
    for option in options:
        sentence = sanitize_option_sentence(clean_input(option.sentence), option)
        if is_malformed_option_sentence(sentence):
            continue
        candidates: list[str] = []
        for answer in (option.label, option.value):
            if not answer:
                continue
            cleaned = clean_input(answer)
            normalized = strip_leading_self(normalize_polite_ending(cleaned))
            candidates.extend((cleaned, normalize_nominal_input(cleaned), normalized, normalize_state_word(normalized)))
        candidates = sorted({item for item in candidates if len(item) >= 2}, key=len, reverse=True)
        slot = next((item for item in candidates if item in sentence), "")
        if not slot:
            continue
        index = sentence.index(slot)
        frames.append((sentence[:index], sentence[index + len(slot):], slot))
    if not frames:
        return None

    grouped: dict[tuple[str, str], list[tuple[str, str, str]]] = {}
    for frame in frames:
        grouped.setdefault((frame[0], frame[1]), []).append(frame)
    matches = max(
        grouped.values(),
        key=lambda items: frame_score(items[0][0], items[0][1], len(items)),
    )
    prefix, suffix, sample = matches[0]
    return prefix, suffix, sample, len(matches)


def frame_score(prefix: str, suffix: str, support: int) -> int:
    subject = prefix.replace("오늘은", "").replace("오늘", "").replace("나는", "").strip()
    return len(subject) * 100 + support * 10 + len(suffix)


def has_meaningful_subject(prefix: str) -> bool:
    subject = prefix.replace("오늘은", "").replace("오늘", "").replace("나는", "").strip()
    return bool(subject)


def sanitize_option_sentence(raw_sentence: str, option: AnswerOption) -> str:
    sentence = raw_sentence
    for answer in (option.label, option.value):
        cleaned = clean_input(answer)
        declarative = normalize_state_word(strip_leading_self(normalize_polite_ending(cleaned)))
        noun = normalize_nominal_input(answer)
        if len(noun) >= 2:
            sentence = sentence.replace(f"{noun}이다를", with_object_particle(noun))
            sentence = sentence.replace(f"{noun}이다을", with_object_particle(noun))
            sentence = sentence.replace(f"{noun}이다가", with_subject_particle(noun))
            sentence = sentence.replace(f"{noun}이다는", noun + ("은" if has_final_consonant(noun[-1]) else "는"))
            sentence = sentence.replace(f"{noun}였다", noun + ("이었다" if has_final_consonant(noun[-1]) else "였다"))
        if not declarative.endswith("이다"):
            for form in (noun, declarative):
                if form:
                    sentence = sentence.replace(f"{form}를 먹었다", declarative)
                    sentence = sentence.replace(f"{form}을 먹었다", declarative)
    return sentence.replace("오늘은 오늘은", "오늘은")


def is_malformed_option_sentence(sentence: str) -> bool:
    if "오늘은 오늘은" in sentence:
        return True
    return re.search(r"(?:했다|었다|았다|였다|이다|해|어|아)(?:을|를|이|가|은|는)(?:\s|$)", sentence) is not None


def clause_for_frame(phrase: str, context: str) -> str:
    if looks_complete(phrase):
        return phrase
    if context in {"mood", "condition", "weather"}:
        return normalize_state_word(phrase)
    if context == "exercise":
        if has_any(phrase, "많", "적", "비슷", "평소", "보통"):
            return normalize_state_word(phrase)
        return activity_sentence(phrase).removeprefix("오늘은 ")
    if context == "food":
        if has_any(phrase, "많", "적", "비슷", "평소", "보통", "든든", "부족", "간단"):
            return normalize_state_word(phrase)
        return f"{with_object_particle(normalize_nominal_input(phrase))} 먹었다"
    if context == "reason":
        if phrase.replace(" ", "") in NO_REASON_WORDS:
            return "특별한 이유는 없었다"
        return f"{with_subject_particle(phrase)} 이유였다"
    if context == "thought":
        return f"{normalize_nominal_input(phrase)}에 대해 생각했다"

    builders = {
        "sleep": sleep_sentence,
        "drink": drink_sentence,
        "movement": movement_sentence,
        "spending": spending_sentence,
        "work": work_sentence,
        "hobby": hobby_sentence,
        "rest": rest_sentence,
        "study": study_sentence,
        "appointment": appointment_sentence,
        "family": family_sentence,
        "people": people_sentence,
        "home": home_sentence,
    }
    builder = builders.get(context, activity_sentence)
    return builder(phrase).removeprefix("오늘은 ")


def normalize_nominal_input(raw: str) -> str:
    text = clean_input(raw)
    for prefix in ("오늘은 ", "오늘 "):
        if text.startswith(prefix):
            text = text[len(prefix):]
    for suffix in ("이에요", "예요", "이요", "요"):
        if text.endswith(suffix):
            text = text[:-len(suffix)]
            break
    return text.strip()


def adjust_leading_particle(suffix: str, slot: str) -> str:
    if not suffix or not slot:
        return suffix
    has_batchim = has_final_consonant(slot[-1])
    if suffix.startswith(("이었다", "였다")):
        replacement = "이었다" if has_batchim else "였다"
        return replacement + suffix[(3 if suffix.startswith("이었다") else 2):]
    pairs = (
        (("을", "를"), "을" if has_batchim else "를"),
        (("이", "가"), "이" if has_batchim else "가"),
        (("은", "는"), "은" if has_batchim else "는"),
        (("과", "와"), "과" if has_batchim else "와"),
    )
    for particles, replacement in pairs:
        if suffix.startswith(particles):
            return replacement + suffix[1:]
    if suffix.startswith(("으로", "로")):
        rieul_batchim = "가" <= slot[-1] <= "힣" and ((ord(slot[-1]) - ord("가")) % 28 == 8)
        replacement = "으로" if has_batchim and not rieul_batchim else "로"
        return replacement + suffix[(2 if suffix.startswith("으로") else 1):]
    return suffix


def repair_by_question(raw_answer: str, question: Question, sentence: str) -> str:
    phrase = strip_leading_self(normalize_polite_ending(clean_input(raw_answer)))
    compact = phrase.replace(" ", "")
    context = detect_context(phrase, question)
    state = normalize_state_word(phrase)

    if context == "mood" and "행복" in compact:
        return f"오늘의 기분은 {state}"
    if context == "mood" and has_any(state, "좋", "나쁘", "괜찮", "평온", "우울", "그럭", "피곤"):
        return f"오늘의 기분은 {state}"
    if context == "condition" and looks_complete(state):
        return f"오늘은 컨디션이 {state}"
    if context == "exercise" and has_any(state, "비슷", "많", "적", "평소"):
        return f"오늘의 활동량은 {state}"
    if context == "hobby" and compact in {"종이접기", "종이접"}:
        return "오늘은 종이 접기를 했다"
    if context == "weather" and sentence.startswith("오늘은 날씨가 오늘"):
        return f"오늘은 날씨가 {state}"
    return sentence


def negative_sentence(phrase: str, question: Question) -> str | None:
    compact = phrase.replace(" ", "")
    if compact not in NEGATIVE_WORDS:
        return None
    if "예정에 없던 만남" in question.title:
        return "오늘은 예정에 없던 만남이 생기지 않았다"
    context = detect_context(phrase, question)
    return {
        "food": "오늘은 식사를 따로 남기지 않았다",
        "drink": "오늘은 따로 마신 것을 남기지 않았다",
        "sleep": "오늘은 잠에 대해 특별히 남길 내용이 없었다",
        "weather": "오늘은 날씨에 대해 특별히 남길 내용이 없었다",
        "mood": "오늘은 기분 변화가 특별히 없었다",
        "condition": "오늘은 컨디션에 대해 특별히 남길 내용이 없었다",
        "movement": "오늘은 이동에 대해 특별히 남길 내용이 없었다",
        "spending": "오늘은 따로 소비한 일이 없었다",
        "work": "오늘은 일과 관련해 특별히 남길 일이 없었다",
        "exercise": "오늘은 운동을 따로 하지 않았다",
        "hobby": "오늘은 취미로 한 일이 없었다",
        "rest": "오늘은 따로 쉰 시간이 없었다",
        "study": "오늘은 공부를 따로 하지 않았다",
        "appointment": "오늘은 약속이 없었다",
        "family": "오늘은 가족과 관련된 일정이 없었다",
        "people": "오늘은 사람들과 특별히 남길 일이 없었다",
        "home": "오늘은 집과 관련해 특별히 남길 일이 없었다",
        "thought": "오늘은 정리하고 싶은 생각이 특별히 없었다",
        "reason": "특별한 이유는 없었다",
    }.get(context, "오늘은 특별히 남길 일이 없었다")


def polish(raw: str) -> str:
    text = re.sub(r"\s+", " ", raw.strip().rstrip(".!?"))
    replacements = {
        "오늘은 오늘은": "오늘은",
        "오늘은그냥": "오늘은 그냥",
        "오늘은 그냥을 했다": "특별한 이유는 없었다",
        "오늘은 내일을 했다": "오늘은 내일에 대해 생각했다",
        "오늘은 자기를 했다": "오늘은 잠을 잤다",
        "오늘은 잠자기를 했다": "오늘은 잠을 잤다",
        "오늘은 잠을 했다": "오늘은 잠을 잤다",
        "오늘은 수면을 했다": "오늘은 잠을 잤다",
        "오늘은 맛있게 먹었다 하루였다": "오늘은 맛있게 먹었다",
        "오늘은 기분이 좋았다 하루였다": "오늘은 기분이 좋았다",
        "오늘은 날씨가 좋았다 하루였다": "오늘은 날씨가 좋았다",
        "가족였다": "가족이었다",
        "계획였다": "계획이었다",
        "고민였다": "고민이었다",
        "일였다": "일이었다",
    }
    for old, new in replacements.items():
        text = text.replace(old, new)

    match = re.match(r"오늘은\s+(.+?)\s+하루였다$", text)
    if match and looks_complete(match.group(1)):
        text = f"오늘은 {match.group(1)}"
    return ensure_period(text or "오늘은 아무것도 남기지 않은 하루였다.")


def looks_suspicious(raw_answer: str) -> bool:
    compact = raw_answer.replace(" ", "").strip()
    if not compact:
        return False
    if re.search(r"[ㄱ-ㅎㅏ-ㅣ]", compact):
        return True
    return len(compact) >= 2 and not re.search(r"[가-힣A-Za-z0-9]", compact)


def looks_suspicious_for_question(raw_answer: str, question: Question) -> bool:
    return looks_suspicious(raw_answer)


def detect_context(phrase: str, question: Question) -> str:
    title = question.title
    category = question.category
    answer_type = question.custom_answer_type
    compact = phrase.replace(" ", "")

    if has_any(title, "이유", "왜", "때문", "계기") or "reason" in answer_type:
        return "reason"
    if has_any(title, "생각", "고민", "정리", "마음에 남") or "thought" in answer_type or category == "생각":
        return "thought"
    if compact in SLEEP_WORDS or has_any(compact, "잠", "수면", "낮잠", "졸림"):
        return "sleep"
    if has_any(compact, "커피", "라떼", "아메리카노", "음료", "술", "맥주"):
        return "drink"
    if has_any(compact, "김치찌개", "된장찌개", "제육", "밥", "라면", "치킨", "점심", "저녁", "아침", "식사"):
        return "food"
    if has_any(title, "식사", "끼니", "점심", "저녁", "아침", "먹") or "food" in answer_type or category == "식사":
        return "food"
    if has_any(title, "날씨", "하늘", "계절") or "weather" in answer_type or category == "날씨":
        return "weather"
    if has_any(title, "기분", "마음") or "mood" in answer_type or category == "기분":
        return "mood"
    if has_any(title, "컨디션", "건강", "몸", "피곤") or "condition" in answer_type or "health" in answer_type or category == "건강":
        return "condition"
    if has_any(title, "이동", "출근", "퇴근", "운전", "길") or "movement" in answer_type or category == "이동":
        return "movement"
    if has_any(title, "소비", "돈", "샀", "지출") or "spending" in answer_type or category == "소비":
        return "spending"
    if has_any(title, "운동", "움직", "활동량") or "exercise" in answer_type or category == "운동":
        return "exercise"
    if has_any(title, "취미") or "hobby" in answer_type or category == "취미":
        return "hobby"
    if has_any(title, "휴식", "쉬", "여유") or "rest" in answer_type or category == "휴식":
        return "rest"
    if has_any(title, "공부", "학습") or "study" in answer_type or category == "공부":
        return "study"
    if has_any(title, "가족") or "family" in answer_type or category == "가족":
        return "family"
    if has_any(title, "약속", "일정") or "appointment" in answer_type or category == "약속":
        return "appointment"
    if has_any(title, "사람", "대화", "연락") or "people" in answer_type or category == "사람":
        return "people"
    if has_any(title, "집", "청소", "정리", "집안") or "home" in answer_type or category == "집":
        return "home"
    if has_any(title, "일", "업무", "회사") or "work" in answer_type or category == "일":
        return "work"
    return "activity"


def reason_sentence(phrase: str) -> str:
    compact = phrase.replace(" ", "")
    if compact in NO_REASON_WORDS or has_any(compact, "그냥", "딱히"):
        return "특별한 이유는 없었다"
    if has_any(compact, "모름", "몰라", "잘모르"):
        return "이유는 잘 모르겠다"
    if phrase.endswith(("서", "어서", "아서")):
        return f"{phrase}였다"
    if phrase.endswith("때문"):
        return f"{phrase}이었다"
    if phrase.endswith("때문에"):
        return phrase
    if has_any(compact, "피곤", "지침"):
        return "피곤해서였다"
    if has_any(compact, "시간"):
        return "시간이 부족해서였다"
    return f"이유는 {with_subject_particle(phrase)} 있었다"


def thought_sentence(phrase: str) -> str:
    compact = phrase.replace(" ", "")
    if compact in NO_REASON_WORDS:
        return "오늘은 정리하고 싶은 생각이 특별히 없었다"
    if compact == "내일":
        return "오늘은 내일에 대해 생각했다"
    if compact in {"일", "회사"}:
        return "오늘은 일에 대해 생각했다"
    if "걱정" in compact:
        return "오늘은 걱정되는 생각이 있었다"
    if "계획" in compact:
        return "오늘은 계획에 대해 생각했다"
    if "고민" in compact:
        return "오늘은 고민이 있었다"
    if "아이디어" in compact:
        return "오늘은 아이디어가 떠올랐다"
    if phrase.endswith("생각"):
        return f"오늘은 {with_subject_particle(phrase)} 있었다"
    return f"오늘은 {phrase}에 대해 생각했다"


def drink_sentence(phrase: str) -> str:
    if looks_complete(phrase):
        return with_today_prefix_if_needed(phrase)
    if has_any(phrase, "마셨", "마심", "마시"):
        return f"오늘은 {phrase}"
    return f"오늘은 {with_object_particle(phrase)} 마셨다"


def food_sentence(phrase: str, question: Question) -> str:
    state = normalize_state_word(phrase)
    if looks_complete(state):
        return with_today_prefix_if_needed(state)
    if has_any(question.title, "식사량", "양") and has_any(state, "많", "적", "보통", "평소"):
        return f"오늘 식사량은 {state}"
    if has_any(state, "많", "적", "보통", "든든", "부족", "간단"):
        return f"오늘 식사는 {state}"
    if has_any(state, "맛있게", "챙겨", "해결"):
        return f"오늘은 {state}"
    return f"오늘은 {with_object_particle(phrase)} 먹었다"


def sleep_sentence(phrase: str) -> str:
    compact = phrase.replace(" ", "")
    state = normalize_state_word(phrase)
    if compact in SLEEP_WORDS or compact in {"잠자기", "자기"}:
        return "오늘은 잠을 잤다"
    if looks_complete(state):
        return with_today_prefix_if_needed(state)
    if has_any(state, "많", "적", "설침", "설쳤", "늦"):
        return f"오늘은 잠을 {state}"
    return "오늘은 잠을 잤다"


def weather_sentence(phrase: str) -> str:
    state = normalize_state_word(phrase)
    if has_any(state, "비", "눈"):
        return f"오늘은 {state}"
    return f"오늘은 날씨가 {state}"


def state_sentence(subject: str, phrase: str) -> str:
    state = normalize_state_word(phrase)
    if looks_complete(state):
        return f"오늘은 {subject}이 {state}"
    if subject in phrase:
        return f"오늘은 {state}"
    return f"오늘은 {subject}이 {state}"


def movement_sentence(phrase: str) -> str:
    compact = phrase.replace(" ", "")
    mapped = {
        "출근": "출근했다",
        "퇴근": "퇴근했다",
        "운전": "운전을 했다",
        "이동": "이동하는 시간이 있었다",
        "산책": "산책을 했다",
        "걷기": "산책을 했다",
    }.get(compact)
    return f"오늘은 {mapped}" if mapped else activity_sentence(phrase)


def spending_sentence(phrase: str) -> str:
    if looks_complete(phrase):
        return with_today_prefix_if_needed(phrase)
    if has_any(phrase, "샀", "구매", "결제", "썼"):
        return f"오늘은 {phrase}"
    return f"오늘은 {with_object_particle(phrase)} 샀다"


def work_sentence(phrase: str) -> str:
    compact = phrase.replace(" ", "")
    mapped = {
        "회의": "회의를 했다",
        "야근": "야근을 했다",
        "업무": "업무를 했다",
        "작업": "작업을 했다",
        "회사일": "회사 일을 했다",
        "일": "회사 일을 했다",
    }.get(compact)
    return f"오늘은 {mapped}" if mapped else activity_sentence(phrase)


def exercise_sentence(phrase: str) -> str:
    state = normalize_state_word(phrase)
    if has_any(state, "비슷", "많", "적", "평소"):
        return f"오늘의 활동량은 {state}"
    return activity_sentence(phrase)


def hobby_sentence(phrase: str) -> str:
    compact = phrase.replace(" ", "")
    mapped = {
        "종이접기": "종이 접기를 했다",
        "종이접": "종이 접기를 했다",
        "게임": "게임을 했다",
        "독서": "책을 읽었다",
        "영화": "영화를 봤다",
        "드라마": "드라마를 봤다",
    }.get(compact)
    return f"오늘은 {mapped}" if mapped else activity_sentence(phrase)


def rest_sentence(phrase: str) -> str:
    compact = phrase.replace(" ", "")
    if has_any(compact, "하루종일", "종일"):
        return "오늘은 하루 종일 쉬었다"
    if has_any(compact, "잠", "낮잠"):
        return "오늘은 잠을 자며 쉬었다"
    if has_any(compact, "영상", "유튜브"):
        return "오늘은 영상을 보며 쉬었다"
    return activity_sentence(phrase)


def study_sentence(phrase: str) -> str:
    compact = phrase.replace(" ", "")
    mapped = {
        "영어": "영어 공부를 했다",
        "한국사": "한국사 공부를 했다",
        "자격증": "자격증 공부를 했다",
    }.get(compact)
    return f"오늘은 {mapped}" if mapped else activity_sentence(phrase)


def appointment_sentence(phrase: str) -> str:
    compact = phrase.replace(" ", "")
    if "친구" in compact:
        return "오늘은 친구와 약속이 있었다"
    if "가족" in compact:
        return "오늘은 가족과 약속이 있었다"
    if has_any(compact, "회사", "업무"):
        return "오늘은 업무 관련 약속이 있었다"
    return f"오늘은 {phrase}와 약속이 있었다"


def family_sentence(phrase: str) -> str:
    return f"오늘 가족과의 시간은 {normalize_state_word(phrase)}"


def people_sentence(phrase: str) -> str:
    if looks_complete(phrase):
        return with_today_prefix_if_needed(phrase)
    return f"오늘 사람들과의 일은 {normalize_state_word(phrase)}"


def home_sentence(phrase: str) -> str:
    compact = phrase.replace(" ", "")
    mapped = {
        "청소": "청소를 했다",
        "정리": "정리를 했다",
        "요리": "요리를 했다",
        "빨래": "빨래를 했다",
    }.get(compact)
    return f"오늘은 {mapped}" if mapped else activity_sentence(phrase)


def activity_sentence(phrase: str) -> str:
    compact = phrase.replace(" ", "")
    mapped = {
        "잠": "잠을 잤다",
        "자기": "잠을 잤다",
        "잠자기": "잠을 잤다",
        "수면": "잠을 잤다",
        "낮잠": "잠을 잤다",
        "휴식": "쉬는 시간을 가졌다",
        "쉬기": "쉬는 시간을 가졌다",
        "청소": "청소를 했다",
        "정리": "정리를 했다",
        "요리": "요리를 했다",
        "운동": "운동을 했다",
        "헬스": "헬스를 했다",
        "산책": "산책을 했다",
        "독서": "책을 읽었다",
        "공부": "공부를 했다",
        "종이접기": "종이 접기를 했다",
        "종이접": "종이 접기를 했다",
    }.get(compact)
    if mapped:
        return f"오늘은 {mapped}"
    if looks_complete(phrase):
        return with_today_prefix_if_needed(phrase)
    if phrase.endswith("하기") and len(phrase) > 2:
        return f"오늘은 {with_object_particle(phrase[:-2])} 했다"
    if phrase.endswith("기") and len(phrase) > 1:
        return f"오늘은 {with_object_particle(phrase[:-1])} 했다"
    return f"오늘은 {with_object_particle(phrase)} 했다"


def clean_input(raw: str) -> str:
    return re.sub(r"\s+", " ", raw.strip().rstrip(".!?"))


def strip_leading_self(text: str) -> str:
    for prefix in ("오늘은 ", "오늘 ", "나는 ", "제가 "):
        if text.startswith(prefix):
            return text[len(prefix):].strip()
    return text.strip()


def normalize_polite_ending(text: str) -> str:
    direct = {
        "좋아": "좋았다",
        "좋아요": "좋았다",
        "괜찮아요": "괜찮았다",
        "괜찮아": "괜찮았다",
        "맛있어요": "맛있었다",
        "맛있어": "맛있었다",
        "많아요": "많았다",
        "많아": "많았다",
        "적어요": "적었다",
        "적어": "적었다",
        "없어요": "없었다",
        "없어": "없었다",
        "있어요": "있었다",
        "있어": "있었다",
        "그럭저럭": "그럭저럭이었다",
        "없었어": "없었다",
        "없었어요": "없었다",
        "행복": "행복했다",
        "늘 행복": "늘 행복했다",
        "비슷": "평소와 비슷했다",
        "비슷비슷해": "평소와 비슷했다",
        "평소와 비슷": "평소와 비슷했다",
        "평소와 비슷비슷해": "평소와 비슷했다",
    }
    if text in direct:
        return direct[text]

    endings = [
        ("했어요", "했다"),
        ("했어", "했다"),
        ("먹었어요", "먹었다"),
        ("먹었어", "먹었다"),
        ("였어요", "였다"),
        ("였어", "였다"),
        ("이었어요", "이었다"),
        ("이었어", "이었다"),
        ("았어요", "았다"),
        ("았어", "았다"),
        ("었어요", "었다"),
        ("었어", "었다"),
        ("예요", "이다"),
        ("이에요", "이다"),
    ]
    for suffix, replacement in endings:
        if text.endswith(suffix):
            return text[: -len(suffix)] + replacement
    return text[:-1] if text.endswith("요") else text


def normalize_state_word(phrase: str) -> str:
    text = phrase.strip().replace(" 모 ", " ").replace("뭐 ", "").strip()
    if "비슷" in text:
        return "평소와 비슷했다"
    direct = {
        "좋": "좋았다",
        "좋음": "좋았다",
        "좋았다": "좋았다",
        "안 좋": "좋지 않았다",
        "안좋": "좋지 않았다",
        "안 좋았다": "좋지 않았다",
        "안좋았다": "좋지 않았다",
        "별로": "좋지 않았다",
        "피곤": "피곤했다",
        "피곤함": "피곤했다",
        "피곤했다": "피곤했다",
        "힘듦": "힘들었다",
        "힘들": "힘들었다",
        "힘들었다": "힘들었다",
        "괜찮": "괜찮았다",
        "괜찮았다": "괜찮았다",
        "많": "많았다",
        "많았다": "많았다",
        "적": "적었다",
        "적었다": "적었다",
        "보통": "평소와 비슷했다",
        "평소": "평소와 비슷했다",
        "평소처럼": "평소와 비슷했다",
        "맛있게": "맛있게 먹었다",
        "맛있게 먹었다": "맛있게 먹었다",
        "비": "비가 왔다",
        "비옴": "비가 왔다",
        "비가 왔다": "비가 왔다",
        "눈": "눈이 왔다",
        "눈옴": "눈이 왔다",
        "눈이 왔다": "눈이 왔다",
        "맑음": "맑았다",
        "맑았다": "맑았다",
        "흐림": "흐렸다",
        "흐렸다": "흐렸다",
        "행복": "행복했다",
        "행복했다": "행복했다",
        "늘 행복": "늘 행복했다",
        "늘 행복했다": "늘 행복했다",
        "비슷": "평소와 비슷했다",
        "비슷비슷": "평소와 비슷했다",
        "비슷비슷했다": "평소와 비슷했다",
        "평소와 비슷했다": "평소와 비슷했다",
    }
    if text in direct:
        return direct[text]
    return (
        re.sub(r"좋아$", "좋았다", text)
        .replace("많아", "많았다")
        .replace("적어", "적었다")
        .replace("피곤해", "피곤했다")
        .replace("힘들어", "힘들었다")
        .replace("괜찮아", "괜찮았다")
        .replace("맑아", "맑았다")
        .replace("흐려", "흐렸다")
        .replace("행복", "행복했다")
        .replace("비슷비슷해", "평소와 비슷했다")
        .replace("비슷해", "평소와 비슷했다")
        .replace("더워", "더웠다")
        .replace("추워", "추웠다")
    )


def looks_complete(value: str) -> bool:
    text = value.strip().rstrip(".!?")
    return text.endswith(("다", "였다", "이었다", "있었다", "없었다"))


def with_today_prefix_if_needed(sentence: str) -> str:
    text = sentence.strip()
    return text if text.startswith(("오늘", "이번")) else f"오늘은 {text}"


def with_object_particle(value: str) -> str:
    value = value.strip()
    if not value:
        return value
    return value + ("을" if has_final_consonant(value[-1]) else "를")


def with_subject_particle(value: str) -> str:
    value = value.strip()
    if not value:
        return value
    return value + ("이" if has_final_consonant(value[-1]) else "가")


def has_final_consonant(char: str) -> bool:
    return "가" <= char <= "힣" and ((ord(char) - ord("가")) % 28 != 0)


def has_any(text: str, *words: str) -> bool:
    return any(word in text for word in words)


def ensure_period(text: str) -> str:
    text = text.strip()
    return text if text.endswith((".", "!", "?")) else text + "."


def filter_questions(questions: list[Question], keyword: str, topic: str, depth: int | None) -> list[Question]:
    result = questions
    if keyword:
        result = [q for q in result if keyword in q.title or keyword in q.category or keyword in q.source_topic_code]
    if topic:
        result = [q for q in result if topic in q.category or topic in q.source_topic_code]
    if depth is not None:
        result = [q for q in result if q.depth_level == depth]
    return result


def parse_number_spec(spec: str, maximum: int) -> list[int]:
    selected: set[int] = set()
    for token in re.split(r"[\s,]+", spec.strip()):
        if not token:
            continue
        if "-" in token:
            start_text, end_text = token.split("-", 1)
            start, end = int(start_text), int(end_text)
            if start > end:
                start, end = end, start
            selected.update(range(start, end + 1))
        else:
            selected.add(int(token))
    return sorted(number for number in selected if 1 <= number <= maximum)


def append_log(path: Path, record: dict) -> None:
    with path.open("a", encoding="utf-8") as file:
        file.write(json.dumps(record, ensure_ascii=False) + "\n")


def run_lab(args: argparse.Namespace) -> None:
    def fresh_questions() -> list[Question]:
        return load_lab_questions(args.sql, args.questions)

    def baseline_questions() -> list[Question]:
        loaded = filter_questions(fresh_questions(), args.keyword, args.topic, args.depth)
        for source_number, question in enumerate(loaded, start=1):
            question.source_number = source_number
        return loaded

    questions = baseline_questions()
    all_question_count = len(questions)
    if args.shuffle:
        random.shuffle(questions)
    option_cache = load_option_cache(args.option_cache)
    supabase_config = load_supabase_config()

    print(f"질문 {len(questions)}개 로딩됨")
    print("보기 기반 엔진: Supabase 선택지 자동 연결")
    print("명령: :q 종료 / :n 다음 / :i 번호선택 / :s 단어 검색 / :t 토픽 필터 / :d 1 깊이 / :all 전체")
    print("번호 예시: :i 25,29,44,47,58,59,73-75")
    print(f"로그: {args.log}")
    index = 0
    while 0 <= index < len(questions):
        q = questions[index]
        hydrate_question_options(q, option_cache, supabase_config, args.option_cache)
        print()
        source_number = q.source_number or index + 1
        selection = f" 선택={index + 1}/{len(questions)}" if len(questions) != all_question_count else ""
        print(f"[{source_number}/{all_question_count}]{selection} depth={q.depth_level} category={q.category} type={q.custom_answer_type} key={q.key}")
        print(f"보기 문장 {len(q.options)}개 참고")
        print(q.title)
        answer = input("기타 입력> ").strip()

        if answer == ":q":
            break
        if answer == ":n" or answer == "":
            index += 1
            continue
        if answer.startswith(":i "):
            try:
                numbers = parse_number_spec(answer[3:], all_question_count)
            except ValueError:
                print("번호는 25,29,73-75처럼 입력")
                continue
            questions = [q for q in baseline_questions() if q.source_number in numbers]
            index = 0
            print(f"번호 선택 결과 {len(questions)}개: {', '.join(map(str, numbers))}")
            continue
        if answer.startswith(":s "):
            keyword = answer[3:].strip()
            questions = filter_questions(baseline_questions(), keyword, "", None)
            index = 0
            print(f"검색 결과 {len(questions)}개")
            continue
        if answer.startswith(":t "):
            topic = answer[3:].strip()
            questions = filter_questions(baseline_questions(), "", topic, None)
            index = 0
            print(f"토픽 결과 {len(questions)}개")
            continue
        if answer.startswith(":d "):
            try:
                depth = int(answer[3:].strip())
            except ValueError:
                print("깊이는 숫자로 입력")
                continue
            questions = filter_questions(fresh_questions(), args.keyword, args.topic, depth)
            for source_number, question in enumerate(questions, start=1):
                question.source_number = source_number
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

        suspicious = looks_suspicious_for_question(answer, q)
        sentence = from_custom_answer(answer, q)
        if suspicious:
            print("오타 의심: 자음/모음만 분리된 입력일 수 있음")
        print(f"결과> {sentence}")
        review = input("판정(엔터=ok / ㅌ=이상함 / 메모)> ").strip()
        status = "bad" if review.lower() == "x" or review == "ㅌ" else "ok" if not review else "memo"
        append_log(
            args.log,
            {
                "time": datetime.now().isoformat(timespec="seconds"),
                "status": status,
                "memo": "" if review.lower() == "x" else review,
                "question_key": q.key,
                "question": q.title,
                "category": q.category,
                "depth_level": q.depth_level,
                "custom_answer_type": q.custom_answer_type,
                "answer": answer,
                "option_count": len(q.options),
                "option_frame_used": infer_sentence_frame(q.options) is not None,
                "suspicious": suspicious,
                "sentence": sentence,
            },
        )
        index += 1


def main() -> None:
    parser = argparse.ArgumentParser(description="하루조각 기타 답변 문장 엔진 로컬 테스트")
    parser.add_argument("--sql", type=Path, default=DEFAULT_SQL)
    parser.add_argument("--questions", type=Path, default=None)
    parser.add_argument("--log", type=Path, default=DEFAULT_LOG)
    parser.add_argument("--option-cache", type=Path, default=DEFAULT_OPTION_CACHE)
    parser.add_argument("--keyword", default="")
    parser.add_argument("--topic", default="")
    parser.add_argument("--depth", type=int, default=1)
    parser.add_argument("--shuffle", action="store_true")
    args = parser.parse_args()
    run_lab(args)


SLEEP_WORDS = {"잠", "자기", "잠자기", "수면", "낮잠", "자는 것", "자는거"}
NO_REASON_WORDS = {"그냥", "없음", "없어", "없어요", "딱히", "특별히없음", "잘모름", "모름"}
NEGATIVE_WORDS = {"없다", "없었다", "없어", "없었어", "없어요", "없었어요", "없음", "안함", "안했다", "못함", "못했다"}


if __name__ == "__main__":
    main()
