# 하루조각

가볍게 하루의 조각을 남기는 Android 기록 습관 앱입니다.

## 빌드 종류

- `localDebug`: 회사 PC 에뮬레이터용 빌드입니다. 질문은 Supabase DB를 먼저 조회하고, 실패하거나 데이터가 없으면 앱 내부 기본 질문을 사용합니다.
- `phoneDebug`: 실제 테스트폰용 빌드입니다. GitHub Actions에서 APK 산출물로 받을 수 있습니다.

## 회사 PC 에뮬레이터 실행

Android Studio 없이 에뮬레이터만 켜둔 상태에서 앱을 빌드/설치/실행합니다.

```powershell
powershell.exe -ExecutionPolicy Bypass -File "D:\AndroidStudioProjects\DiaryApp\run-diaryapp.ps1"
```

## 실제 폰 테스트용 APK

GitHub Actions의 `Build Android debug APK` workflow가 `phoneDebug` APK를 만듭니다.

산출물:

```text
app/build/outputs/apk/phone/debug/app-phone-debug.apk
```

## 질문 데이터

질문은 Supabase REST API로 `question_groups`, `questions`, `question_options`를 조회합니다.
앱에는 publishable key만 들어가며, DB 조회가 실패하면 로컬 기본 질문으로 조용히 전환됩니다.

## 보안 메모

- Android 앱에는 Supabase service role key를 넣지 않습니다.
- `local.properties`, APK, build 산출물은 Git에 올리지 않습니다.
- 배포 전 `custom_answer_samples`의 공개 관리 권한을 닫고, 관리자용 경로를 별도로 정리합니다.
