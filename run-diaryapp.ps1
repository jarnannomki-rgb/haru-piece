$ErrorActionPreference = "Stop"

$ProjectDir = "D:\AndroidStudioProjects\DiaryApp"
$JavaHome = "C:\Program Files\Android\Android Studio\jbr"
$env:JAVA_HOME = $JavaHome
$env:PATH = "$JavaHome\bin;$env:PATH"

$Adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$Apk = Join-Path $ProjectDir "app\build\outputs\apk\local\debug\app-local-debug.apk"
$B64 = Join-Path $ProjectDir "app-debug.b64"
$RemoteB64 = "/data/local/tmp/diaryapp-debug.b64"
$RemoteApk = "/data/local/tmp/diaryapp-debug.apk"
$Package = "com.example.diaryapp"

Write-Host "[1/5] Building APK..."
Push-Location $ProjectDir
try {
    & ".\gradlew.bat" ":app:assembleLocalDebug" "-Dorg.gradle.java.home=$JavaHome"
    if ($LASTEXITCODE -ne 0) { throw "Gradle build failed with exit code $LASTEXITCODE" }
}
finally {
    Pop-Location
}

Write-Host "[2/5] Encoding APK for adb shell transfer..."
[IO.File]::WriteAllText($B64, [Convert]::ToBase64String([IO.File]::ReadAllBytes($Apk)))

Write-Host "[3/5] Sending APK through adb shell stdin..."
Get-Content -Raw -Path $B64 | & $Adb shell "cat > $RemoteB64"
if ($LASTEXITCODE -ne 0) { throw "ADB transfer failed with exit code $LASTEXITCODE" }

Write-Host "[4/5] Installing inside emulator..."
& $Adb shell "base64 -d $RemoteB64 > $RemoteApk && pm install -r $RemoteApk"
if ($LASTEXITCODE -ne 0) { throw "APK install failed with exit code $LASTEXITCODE" }

Write-Host "[5/5] Launching app..."
& $Adb shell "monkey -p $Package 1"
if ($LASTEXITCODE -ne 0) { throw "App launch failed with exit code $LASTEXITCODE" }

Write-Host "Done."

