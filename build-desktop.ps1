param([string]$NodePath)
$ErrorActionPreference='Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    if(!$NodePath) { $NodePath=(Get-Command node -ErrorAction Stop).Source }
    foreach($script in @('common.js','background.js','content.js','popup.js')) {
        & $NodePath --check "desktop/$script"
        if($LASTEXITCODE -ne 0) { throw "JavaScript syntax error: $script" }
    }
    & $NodePath tests/desktop-rules.cjs
    if($LASTEXITCODE -ne 0) { throw 'Desktop rule checks failed' }
    $manifest=Get-Content desktop/manifest.json -Raw | ConvertFrom-Json
    [xml]$android=Get-Content app/src/main/AndroidManifest.xml -Raw
    $version=$android.manifest.GetAttribute('versionName','http://schemas.android.com/apk/res/android')
    $expected=if(($version -split '\.').Count -eq 2) { "$version.0" } else { $version }
    if($manifest.version -ne $expected) { throw 'Android and extension versions differ' }
    $stage=Join-Path $PSScriptRoot ('build/desktop-'+[Guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Force -Path $stage,dist | Out-Null
    $files=@('manifest.json','common.js','background.js','content.js','popup.html','popup.css','popup.js','icon.png','README.md','README.en.md','README.ja.md')
    foreach($file in $files) { Copy-Item -LiteralPath "desktop/$file" -Destination $stage }
    Copy-Item -LiteralPath LICENSE -Destination $stage
    Compress-Archive -Path "$stage/*" -DestinationPath dist/PageTurner-PC.zip -Force
    Get-FileHash -LiteralPath dist/PageTurner-PC.zip -Algorithm SHA256
} finally { Pop-Location }
