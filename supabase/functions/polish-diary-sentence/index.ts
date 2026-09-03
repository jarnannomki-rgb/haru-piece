const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
};

const systemPrompt = `너는 한국어 일기 문장 교정기다.
질문과 사용자의 기타 입력을 바탕으로 담백하고 자연스러운 일기 문장 한 개를 만든다.
규칙:
- 사용자가 말하지 않은 사실, 감정, 이유, 의미를 추가하지 않는다.
- 질문의 대상과 사용자 답변이 문장에 드러나야 한다.
- 맞춤법, 띄어쓰기, 조사, 시제만 자연스럽게 고친다.
- 과장, 비유, 감성 표현을 넣지 않는다.
- 문장은 가급적 "오늘은"으로 시작하고 "-다."로 끝낸다.
- 입력 의미를 확신할 수 없거나 질문과 무관하면 needsReview를 true로 한다.
- JSON만 반환한다: {"sentence":"...","needsReview":false}
예:
질문: 오늘은 평소보다 잘 쉬고 있나요? / 입력: 엄청잘쉬고있음
답: {"sentence":"오늘은 평소보다 아주 잘 쉬었다.","needsReview":false}
질문: 오늘 가족과 관련된 일정이 있었나요? / 입력: 가족모임
답: {"sentence":"오늘은 가족 모임이 있었다.","needsReview":false}
질문: 오늘 집안일은 어느 정도 했나요? / 입력: 빨래
답: {"sentence":"오늘은 빨래를 했다.","needsReview":false}`;

function json(data: unknown, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json; charset=utf-8" },
  });
}

Deno.serve(async (req) => {
  if (req.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (req.method !== "POST") {
    return json({ error: "Method not allowed" }, 405);
  }

  const apiKey = Deno.env.get("GROQ_API_KEY");
  if (!apiKey) {
    return json({ error: "GROQ_API_KEY is missing" }, 500);
  }

  const body = await req.json().catch(() => null);
  const question = typeof body?.question === "string" ? body.question.trim() : "";
  const answer = typeof body?.answer === "string" ? body.answer.trim() : "";
  const draft = typeof body?.draft === "string" ? body.draft.trim() : "";

  if (!question || !answer) {
    return json({ error: "question and answer are required" }, 400);
  }
  if (question.length > 300 || answer.length > 200 || draft.length > 500) {
    return json({ error: "Input is too long" }, 400);
  }

  const userPrompt = [
    `질문: ${question}`,
    `사용자 입력: ${answer}`,
    draft ? `기존 초안(틀릴 수 있음): ${draft}` : "",
  ].filter(Boolean).join("\n");

  try {
    const groqResponse = await fetch("https://api.groq.com/openai/v1/chat/completions", {
      method: "POST",
      headers: {
        "Authorization": `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        model: "qwen/qwen3.6-27b",
        messages: [
          { role: "system", content: systemPrompt },
          { role: "user", content: userPrompt },
        ],
        reasoning_effort: "none",
        response_format: { type: "json_object" },
        temperature: 0.2,
        max_completion_tokens: 100,
      }),
    });

    const groqJson = await groqResponse.json().catch(() => null);
    if (!groqResponse.ok) {
      console.error("Groq API error", groqResponse.status, groqJson?.error?.message);
      return json({ error: "Groq API error", status: groqResponse.status }, 502);
    }

    const content = groqJson?.choices?.[0]?.message?.content;
    if (typeof content !== "string" || !content.trim()) {
      return json({ error: "Groq returned an empty response" }, 502);
    }

    const result = JSON.parse(content);
    const sentence = typeof result?.sentence === "string" ? result.sentence.trim() : "";
    if (!sentence) {
      return json({ error: "Groq returned an invalid sentence" }, 502);
    }

    return json({
      sentence,
      needsReview: result.needsReview === true,
      model: "qwen/qwen3.6-27b",
    });
  } catch (error) {
    console.error("Sentence polishing failed", error);
    return json({ error: "Sentence polishing failed" }, 500);
  }
});
