param(
    [string]$JdkPath,
    [string]$SdkPath,
    [switch]$Development
)
$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
[xml]$manifest = Get-Content -Raw -LiteralPath 'app\src\main\AndroidManifest.xml'
$releaseVersion = $manifest.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
$releaseApk = "dist\PageTurner-$releaseVersion.apk"
$jdk = $JdkPath
if (!$jdk -and (Test-Path -LiteralPath '.\toolchain\jdk')) { $jdk = (Get-ChildItem -LiteralPath '.\toolchain\jdk' -Filter javac.exe -Recurse | Select-Object -First 1).Directory.Parent.FullName }
if (!$jdk) { $jdk = $env:JAVA_HOME }
$sdk = $SdkPath
if (!$sdk -and !(Test-Path -LiteralPath '.\toolchain\build-tools')) { $sdk = $env:ANDROID_HOME; if (!$sdk) { $sdk = $env:ANDROID_SDK_ROOT } }
if ($sdk) {
    $bt = Join-Path $sdk 'build-tools\35.0.0'
    $platform = Join-Path $sdk 'platforms\android-35\android.jar'
} else {
    $bt = (Get-ChildItem -LiteralPath '.\toolchain\build-tools' -Filter aapt2.exe -Recurse | Select-Object -First 1).Directory.FullName
    $platform = (Get-ChildItem -LiteralPath '.\toolchain\platform' -Filter android.jar -Recurse | Select-Object -First 1).FullName
}
if (!$jdk -or !(Test-Path -LiteralPath "$jdk\bin\javac.exe") -or !(Test-Path -LiteralPath "$bt\aapt2.exe") -or !(Test-Path -LiteralPath $platform)) { throw 'Install JDK 17, Android SDK platform 35 and build-tools 35.0.0; pass -JdkPath and -SdkPath. See BUILDING.md.' }
function Run-Tool([string]$Exe, [string[]]$ToolArgs) { & $Exe @ToolArgs; if ($LASTEXITCODE -ne 0) { throw "Tool failed: $Exe (exit $LASTEXITCODE)" } }
# Use a new directory each build so stale classes cannot enter an APK.
$build = Join-Path $PSScriptRoot ('build\' + (Get-Date -Format 'yyyyMMdd-HHmmss'))
New-Item -ItemType Directory -Force -Path "$build\classes", "$build\generated", "$build\dex", "$build\tests", '.\dist' | Out-Null
Run-Tool "$bt\aapt2.exe" @('compile','--dir','app\src\main\res','-o',"$build\resources.zip")
Run-Tool "$bt\aapt2.exe" @('link','-o',"$build\base.apk",'-I',$platform,'--manifest','app\src\main\AndroidManifest.xml','--java',"$build\generated",'--auto-add-overlay',"$build\resources.zip")
$sources = @(Get-ChildItem -LiteralPath 'app\src\main\java',"$build\generated" -Filter '*.java' -Recurse | ForEach-Object FullName)
Run-Tool "$jdk\bin\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$platform,'-d',"$build\classes") + $sources)
Run-Tool "$jdk\bin\javac.exe" @('-encoding','UTF-8','-d',"$build\tests",'app\src\main\java\local\pageturner\Rules.java','app\src\main\java\local\pageturner\UpdatePolicy.java','app\src\main\java\local\pageturner\SetupState.java','app\src\main\java\local\pageturner\ReadingRules.java','app\src\main\java\local\pageturner\ScrollWatch.java','app\src\main\java\local\pageturner\ScrollPlan.java','app\src\main\java\local\pageturner\ProgressWatch.java','app\src\main\java\local\pageturner\LanguagePolicy.java','tests\RulesTest.java','tests\UpdatePolicyTest.java','tests\SetupStateTest.java','tests\ReadingRulesTest.java','tests\ScrollTest.java','tests\LanguagePolicyTest.java','tests\LocalizationTest.java')
Run-Tool "$jdk\bin\java.exe" @('-cp',"$build\tests",'RulesTest')
Run-Tool "$jdk\bin\java.exe" @('-cp',"$build\tests",'UpdatePolicyTest')
Run-Tool "$jdk\bin\java.exe" @('-cp',"$build\tests",'SetupStateTest')
Run-Tool "$jdk\bin\java.exe" @('-cp',"$build\tests",'ReadingRulesTest')
Run-Tool "$jdk\bin\java.exe" @('-cp',"$build\tests",'ScrollTest')
Run-Tool "$jdk\bin\java.exe" @('-cp',"$build\tests",'LanguagePolicyTest')
Run-Tool "$jdk\bin\java.exe" @('-cp',"$build\tests",'LocalizationTest')
Run-Tool "$jdk\bin\jar.exe" @('--create','--file',"$build\classes.jar",'-C',"$build\classes",'.')
Run-Tool "$jdk\bin\java.exe" @('-cp',"$bt\lib\d8.jar",'com.android.tools.r8.D8','--lib',$platform,'--min-api','26','--output',"$build\dex", "$build\classes.jar")
Copy-Item -LiteralPath "$build\base.apk" -Destination "$build\unsigned.apk"
Run-Tool "$jdk\bin\jar.exe" @('uf',"$build\unsigned.apk",'-C',"$build\dex",'classes.dex')
Run-Tool "$bt\zipalign.exe" @('-f','4',"$build\unsigned.apk", "$build\aligned.apk")
# Official updates must retain the original private key. A public checkout builds with its own development key.
$signingKey = 'prototype.keystore'
if ($Development -or !(Test-Path -LiteralPath $signingKey)) {
    $signingKey = 'development.keystore'
    $releaseApk = "dist\PageTurner-$releaseVersion-dev.apk"
    if (!(Test-Path -LiteralPath $signingKey)) {
        Run-Tool "$jdk\bin\keytool.exe" @('-genkeypair','-keystore',$signingKey,'-storepass','android','-keypass','android','-alias','prototype','-dname','CN=PageTurner Development','-keyalg','RSA','-validity','10000')
    }
}
Run-Tool "$jdk\bin\java.exe" @('-jar',"$bt\lib\apksigner.jar",'sign','--ks',$signingKey,'--ks-key-alias','prototype','--ks-pass','pass:android','--key-pass','pass:android','--out',$releaseApk,"$build\aligned.apk")
Run-Tool "$jdk\bin\java.exe" @('-jar',"$bt\lib\apksigner.jar",'verify','--verbose',$releaseApk)
Run-Tool "$bt\aapt2.exe" @('dump','badging',$releaseApk)
Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256
} finally { Pop-Location }
