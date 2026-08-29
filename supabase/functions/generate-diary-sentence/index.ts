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
너는 Android 기록 습관 앱 '하루조각'의 일기 문장 정리 담당이다.

역할:
- 사용자의 선택 답변과 기타 입력을 한두 문장의 담백한 일기 문장으로 정리한다.
- 사용자가 말한 사실은 바꾸지 않는다.
- 사용자가 말하지 않은 감정, 의미, 해석, 분위기를 추가하지 않는다.
- 형용사와 부사를 새로 보태지 않는다.
- 문법이 어색한 부분만 자연스럽게 고친다.
- 같은 표현을 반복하지 않는다. 예: "오늘은 오늘은" 금지.
- "좋아", "자기", "잠자기" 같은 짧은 입력도 실제 사람이 쓴 일기체로 완성한다.
- 존댓말이 아니라 담백한 일기체로 쓴다.
- 반드시 JSON만 반환한다.

좋은 예:
- 입력: ["오늘은 식사를 간단하게 해결했다."]
  출력: "오늘은 식사를 간단하게 해결했다."
- 입력: ["오늘은 잠을 잤다."]
  출력: "오늘은 잠을 잤다."
- 입력: ["오늘은 날씨가 좋았다."]
  출력: "오늘은 날씨가 좋았다."
- 입력: ["오늘은 이동하는 시간이 있었다.", "이동은 크게 불편하지 않았다."]
  출력: "오늘은 이동하는 시간이 있었다. 이동은 크게 불편하지 않았다."

피해야 할 예:
- "오늘은 오늘은 날씨가 좋아 하루였다."
- "오늘은 자를 했다."
- "따뜻한", "소중한", "작은 여유", "마음이 차분해졌다"처럼 사용자가 말하지 않은 표현

사용자 상태와 답변:
${JSON.stringify(body)}

JSON 형식:
{
  "sentence": "정리된 일기 문장"
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

  const raw = geminiJson?.candidates?.[0]?.content?.parts?.[0]?.text ?? "";
  const sentence = parseSentence(raw);
  return json({ sentence, raw });
});

function parseSentence(raw: string): string {
  const cleaned = raw.replace("```json", "").replace("```", "").trim();
  try {
    const parsed = JSON.parse(cleaned);
    return typeof parsed.sentence === "string" ? parsed.sentence : "";
  } catch (_error) {
    return cleaned;
  }
}

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      ...corsHeaders,
      "Content-Type": "application/json",
    },
  });
}