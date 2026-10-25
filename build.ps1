# CodeBridge Android — Gradle build (AGP 8.5.2 / Gradle 8.10.2 wrapper)
# Output: ..\codebridge apk\codebridge.apk (signed via app/build.gradle.kts)
$ErrorActionPreference = "Stop"
$proj = $PSScriptRoot
$out  = Join-Path (Split-Path $proj -Parent) "codebridge apk\codebridge.apk"

if (-not (Test-Path "$proj\keys\codebridge.keystore")) { throw "keystore missing: $proj\keys\codebridge.keystore" }

& "$proj\gradlew.bat" -p "$proj" assembleRelease --console=plain
if ($LASTEXITCODE -ne 0) { throw "gradle assembleRelease failed" }

New-Item -ItemType Directory -Force -Path $out | Out-Null
Copy-Item "$proj\app\build\outputs\apk\release\app-release.apk" $out -Force
$sdk = $env:ANDROID_HOME; if (-not $sdk) { $sdk = "C:\Android\android-sdk" }
& (Join-Path $sdk "build-tools\34.0.0\apksigner.bat") verify $out
Write-Host "APK built -> $out"
