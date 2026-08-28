# 하루조각

가볍게 하루의 조각을 남기는 Android 기록 습관 앱입니다.

## 빌드 종류

- `localDebug`: 회사 PC 에뮬레이터용. `http://10.0.2.2:8787` 로컬 프록시를 통해 AI 질문을 가져옵니다.
- `phoneDebug`: 실제 테스트폰용. Supabase Edge Function을 직접 호출합니다.

## 회사 PC 에뮬레이터 실행

먼저 로컬 프록시를 켭니다.

```powershell
& "C:\Users\201089\.cache\codex-runtimes\codex-primary-runtime\dependencies\node\bin\node.exe" --use-system-ca "D:\AndroidStudioProjects\DiaryApp\tools\local-ai-proxy.js"
```

그 다음 앱을 빌드/설치/실행합니다.

```powershell
powershell.exe -ExecutionPolicy Bypass -File "D:\AndroidStudioProjects\DiaryApp\run-diaryapp.ps1"
```

## 실제 폰 테스트용 APK

GitHub Actions의 `Build Android debug APK` workflow가 `phoneDebug` APK를 만듭니다.

산출물:

```text
app/build/outputs/apk/phone/debug/app-phone-debug.apk
```

## 보안 메모

- `GEMINI_API_KEY`는 Supabase Secret에만 저장합니다.
- Android 앱에는 Gemini API key와 Supabase service role key를 넣지 않습니다.
- `local.properties`, APK, build 산출물은 Git에 올리지 않습니다.
