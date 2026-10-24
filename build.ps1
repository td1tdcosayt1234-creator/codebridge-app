# CodeBridge Android — one-shot build pipeline
# javac -> d8 -> aapt2 -> jar (add dex) -> zipalign -> apksigner
# Keystore: keys/codebridge.keystore  (store/key pass: codebridge2026, alias: codebridge)
$ErrorActionPreference = "Stop"

$sdk  = $env:ANDROID_HOME; if (-not $sdk) { $sdk = "C:\Android\android-sdk" }
$proj = $PSScriptRoot
$bt   = Join-Path $sdk "build-tools\34.0.0"
$plat = Join-Path $sdk "platforms\android-34\android.jar"
$out  = Join-Path (Split-Path $proj -Parent) "codebridge apk\codebridge.apk"

if (-not (Test-Path $plat)) { throw "platform android-34 missing: $plat" }
if (-not (Test-Path "$proj\keys\codebridge.keystore")) { throw "keystore missing: $proj\keys\codebridge.keystore" }

Remove-Item "$proj\build" -Recurse -Force -ErrorAction SilentlyContinue
New-Item -ItemType Directory -Force -Path "$proj\build", "$proj\obj" | Out-Null

# 1. Compile
javac -encoding UTF-8 -source 8 -target 8 -bootclasspath $plat -d "$proj\obj" "$proj\java\com\codebridge\app\MainActivity.java"

# 2. DEX
& "$bt\d8.bat" --release --lib $plat --min-api 21 --output "$proj\build" (Get-ChildItem "$proj\obj\com\codebridge\app\*.class" | ForEach-Object { $_.FullName })

# 3. Resources + manifest -> base APK
& "$bt\aapt2.exe" compile --dir "$proj\res" -o "$proj\build\res.zip"
& "$bt\aapt2.exe" link -o "$proj\build\base.apk" -I $plat --min-sdk-version 21 --target-sdk-version 34 --manifest "$proj\AndroidManifest.xml" "$proj\build\res.zip"

# 4. Add classes.dex into APK
jar -uf "$proj\build\base.apk" -C "$proj\build" classes.dex

# 5. Align
& "$bt\zipalign.exe" -p -f 4 "$proj\build\base.apk" "$proj\build\aligned.apk"

# 6. Sign -> final APK
& "$bt\apksigner.bat" sign --ks "$proj\keys\codebridge.keystore" --ks-key-alias codebridge --ks-pass pass:codebridge2026 --key-pass pass:codebridge2026 --out $out "$proj\build\aligned.apk"
& "$bt\apksigner.bat" verify $out
Write-Host "APK built -> $out"
