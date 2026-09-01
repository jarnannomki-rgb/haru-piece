import argparse
import json
import random
import re
import sys
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path

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


@dataclass
class Question:
    key: str
    title: str
    category: str = ""
    depth_level: int = 1
    custom_answer_type: str = "activity"
    source_topic_code: str = ""


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
    else:
        sentence = activity_sentence(phrase)
    return polish(repair_by_question(raw_answer, question, sentence))


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
    if looks_suspicious(raw_answer):
        return True
    phrase = strip_leading_self(normalize_polite_ending(clean_input(raw_answer)))
    compact = phrase.replace(" ", "")
    if not (2 <= len(compact) <= 4):
        return False
    context = detect_context(phrase, question)
    if context == "weather":
        return not has_any(compact, "좋", "별로", "맑", "흐", "비", "눈", "더", "추", "바람", "습", "선선", "쌀쌀", "따뜻", "덥", "춥")
    if context == "mood":
        return not has_any(compact, "좋", "나쁘", "별로", "행복", "우울", "평온", "그럭", "짜증", "화", "걱정", "괜찮", "기쁨", "슬픔")
    if context == "condition":
        return not has_any(compact, "좋", "별로", "피곤", "힘들", "괜찮", "아픔", "아파", "무거", "가벼", "졸림")
    return False


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


def append_log(path: Path, record: dict) -> None:
    with path.open("a", encoding="utf-8") as file:
        file.write(json.dumps(record, ensure_ascii=False) + "\n")


def run_lab(args: argparse.Namespace) -> None:
    def fresh_questions() -> list[Question]:
        return load_lab_questions(args.sql, args.questions)

    questions = fresh_questions()
    questions = filter_questions(questions, args.keyword, args.topic, args.depth)
    if args.shuffle:
        random.shuffle(questions)

    print(f"질문 {len(questions)}개 로딩됨")
    print("명령: :q 종료 / :n 다음 / :s 단어 검색 / :t 토픽 필터 / :d 1 깊이 / :all 전체")
    print(f"로그: {args.log}")
    index = 0
    while 0 <= index < len(questions):
        q = questions[index]
        print()
        print(f"[{index + 1}/{len(questions)}] depth={q.depth_level} category={q.category} type={q.custom_answer_type} key={q.key}")
        print(q.title)
        answer = input("기타 입력> ").strip()

        if answer == ":q":
            break
        if answer == ":n" or answer == "":
            index += 1
            continue
        if answer.startswith(":s "):
            keyword = answer[3:].strip()
            questions = filter_questions(fresh_questions(), keyword, "", args.depth)
            index = 0
            print(f"검색 결과 {len(questions)}개")
            continue
        if answer.startswith(":t "):
            topic = answer[3:].strip()
            questions = filter_questions(fresh_questions(), "", topic, args.depth)
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
            index = 0
            print(f"depth {depth} 결과 {len(questions)}개")
            continue
        if answer == ":all":
            questions = fresh_questions()
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
