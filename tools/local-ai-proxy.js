const http = require("http");

const PORT = 8787;
const SUPABASE_FUNCTION_URL =
  "https://lhtnpawocnyknfvgcjph.supabase.co/functions/v1/generate-diary-question";

function sendJson(res, status, data) {
  res.writeHead(status, {
    "Content-Type": "application/json; charset=utf-8",
  });
  res.end(JSON.stringify(data));
}

const server = http.createServer(async (req, res) => {
  if (req.method === "GET" && req.url === "/health") {
    sendJson(res, 200, { ok: true });
    return;
  }

  if (req.method !== "POST" || req.url !== "/generate-diary-question") {
    sendJson(res, 404, { error: "not found" });
    return;
  }

  let body = "";
  req.setEncoding("utf8");
  req.on("data", (chunk) => {
    body += chunk;
  });

  req.on("end", async () => {
    try {
      console.log("[proxy] request received");
      const upstream = await fetch(SUPABASE_FUNCTION_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
      });
      const text = await upstream.text();
      console.log("[proxy] supabase status", upstream.status);

      res.writeHead(upstream.status, {
        "Content-Type": upstream.headers.get("content-type") || "application/json; charset=utf-8",
      });
      res.end(text);
    } catch (error) {
      console.error("[proxy] failed", String(error?.message || error));
      sendJson(res, 502, { error: "proxy failed" });
    }
  });
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`[proxy] listening on http://127.0.0.1:${PORT}`);
  console.log("[proxy] emulator URL: http://10.0.2.2:8787/generate-diary-question");
});
