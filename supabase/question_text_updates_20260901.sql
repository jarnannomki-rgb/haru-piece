-- 하루조각 질문/선택지 문구 수정
-- 다른 구조 변경 없음. 해당 문구만 update.

begin;

-- 1. 오늘 식사는 주로 어떻게 해결하셨나요?
--    4. 여러 가지였어요. -> 끼니마다 달랐어요.
update public.question_options as o
set label = '끼니마다 달랐어요.'
from public.questions as q
where o.question_id = q.id
  and q.question_text = '오늘 식사는 주로 어떻게 해결하셨나요?'
  and o.option_order = 4
  and o.label = '여러 가지였어요.';

-- 2. 늘 몸을 움직인 방식은 어떤 쪽인가요? -> 오늘은 얼마나 움직였나요?
update public.questions
set question_text = '오늘은 얼마나 움직였나요?',
    name = '오늘은 얼마나 움직였나요?'
where question_text = '늘 몸을 움직인 방식은 어떤 쪽인가요?';

-- 3. 오늘 약속은 어떤 쪽에 가까운가요? -> 오늘 약속은 누구와 약속인가요?
update public.questions
set question_text = '오늘 약속은 누구와 약속인가요?',
    name = '오늘 약속은 누구와 약속인가요?'
where question_text = '오늘 약속은 어떤 쪽에 가까운가요?';

commit;

-- 실행 후 확인용
select question_text, count(*)
from public.questions
where question_text in (
  '오늘은 얼마나 움직였나요?',
  '오늘 약속은 누구와 약속인가요?'
)
group by question_text;

select q.question_text, o.option_order, o.label
from public.question_options o
join public.questions q on q.id = o.question_id
where q.question_text = '오늘 식사는 주로 어떻게 해결하셨나요?'
  and o.option_order = 4;
