const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  const body = await req.json().catch(() => ({}));
  const apiKey = Deno.env.get("GEMINI_API_KEY");

  if (!apiKey) {
    return json({ error: "GEMINI_API_KEY is missing" }, 500);
  }

  const prompt = `
너는 Android 기록 습관 앱 '하루조각'의 질문 작가다.

목표:
- 사용자가 1분 안에 기록하게 만든다.
- 초반 질문은 가볍고 직관적이어야 한다.
- 과하게 서정적이거나 상담 같은 질문은 피한다.
- 1주차는 식사, 수면, 컨디션, 기분, 날씨, 한 일 중심으로 묻는다.
- 2번째 질문부터는 앞 답변에 자연스럽게 이어지는 이유 질문을 한다.
- 3번째 질문부터는 반복/패턴을 묻되 무겁게 만들지 않는다.
- 사용자가 말하지 않은 감정이나 의미를 기록 문장에 추가하지 않는다.
- 기록 문장은 실제 사람이 자기 일기에 쓸 법한 담백한 일기체로 만든다.
- 선택지는 4개를 만든다.
- 기타 입력은 앱에서 항상 제공한다.
- 2번째 질문부터 여기까지는 앱에서 제공한다.
- 반드시 JSON만 반환한다.

반복 방지:
- 사용자 상태의 rotationSeed, weekday, avoidCategories를 참고한다.
- avoidCategories에 있는 소재는 가능하면 이번 질문에서 피한다.
- 같은 질문 문장을 반복하지 않는다.
- 1번째 질문은 식사, 수면, 컨디션, 오늘 한 일, 날씨 중 하나를 고르되 매번 같은 소재로 고정하지 않는다.

사용자 상태:
${JSON.stringify(body)}

JSON 형식:
{
  "question": "질문",
  "options": [
    { "label": "선택지", "sentence": "기록 문장" }
  ],
  "allowOther": true,
  "allowStopHere": false
}
`;

  const geminiRes = await fetch(
    "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent",
    {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        "x-goog-api-key": apiKey,
      },
      body: JSON.stringify({
        contents: [{ parts: [{ text: prompt }] }],
      }),
    },
  );

  const geminiJson = await geminiRes.json();

  if (!geminiRes.ok) {
    return json({ error: "Gemini API error", status: geminiRes.status, detail: geminiJson }, 500);
  }

  const text = geminiJson?.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
  return json({ raw: text });
});

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}
