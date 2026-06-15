$version = Select-String -Path "gradle.properties" -Pattern "mod_version=(.*)" | ForEach-Object { $_.Matches[0].Groups[1].Value }

$modJsonPath = "src/main/resources/fabric.mod.json"
if (Test-Path $modJsonPath) {
    $content = Get-Content $modJsonPath -Raw
    $content = $content -replace '"version":\s*"[^"]*"', "`"version`": `"$version`""
    Set-Content -Path $modJsonPath -Value $content -NoNewline
}

$jarPath = "build/libs/bomboaddons-$version.jar"
Write-Host "Building project cleanly..." -ForegroundColor Yellow
.\gradlew clean build

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    $wingetGhDir = "$env:USERPROFILE\AppData\Local\Microsoft\WinGet\Packages\GitHub.cli_Microsoft.Winget.Source_8wekyb3d8bbwe\bin"
    if (Test-Path $wingetGhDir) {
        $env:PATH += ";$wingetGhDir"
    }
}

Write-Host "Creating GitHub Release for v$version..." -ForegroundColor Cyan
$env:GITHUB_TOKEN = $null
$gitCred = "url=https://github.com" | git credential fill 2>$null
$tokenLine = $gitCred | Where-Object { $_ -like "password=*" }
if ($tokenLine) {
    $env:GITHUB_TOKEN = $tokenLine.Substring(9)
}
gh release delete "v$version" --yes --cleanup-tag 2>$null
gh release create "v$version" $jarPath --title "v$version" --notes "Bump version to v${version}: add Dungeon Big Hitbox feature for levers and buttons."

Write-Host "Successfully uploaded v$version!" -ForegroundColor Green
