$version = Select-String -Path "gradle.properties" -Pattern "mod_version=(.*)" | ForEach-Object { $_.Matches[0].Groups[1].Value }
$mcVersion = Select-String -Path "gradle.properties" -Pattern "minecraft_version=(.*)" | ForEach-Object { $_.Matches[0].Groups[1].Value }

$modJsonPath = "src/main/resources/fabric.mod.json"
if (Test-Path $modJsonPath) {
    $content = Get-Content $modJsonPath -Raw
    $content = $content -replace '"version":\s*"[^"]*"', "`"version`": `"$version`""
    Set-Content -Path $modJsonPath -Value $content -NoNewline
}

Write-Host "Building project cleanly..." -ForegroundColor Yellow
.\gradlew clean build

$jarPath = "build/libs/bomboaddons-$version.jar"
$releaseJarPath = "build/libs/bomboaddons-$mcVersion-$version.jar"

if (Test-Path $jarPath) {
    Copy-Item -Path $jarPath -Destination $releaseJarPath -Force
    Write-Host "Copied $jarPath to $releaseJarPath" -ForegroundColor Magenta
} else {
    Write-Error "Could not find built jar at $jarPath"
    exit 1
}

if (-not (Get-Command gh -ErrorAction SilentlyContinue)) {
    $wingetGhDir = "$env:USERPROFILE\AppData\Local\Microsoft\WinGet\Packages\GitHub.cli_Microsoft.Winget.Source_8wekyb3d8bbwe\bin"
    if (Test-Path $wingetGhDir) {
        $env:PATH += ";$wingetGhDir"
    }
}

Write-Host "Checking GitHub Release for v$version..." -ForegroundColor Cyan
$env:GITHUB_TOKEN = $null
$gitCred = "url=https://github.com" | git credential fill 2>$null
$tokenLine = $gitCred | Where-Object { $_ -like "password=*" }
if ($tokenLine) {
    $env:GITHUB_TOKEN = $tokenLine.Substring(9)
}

$releaseExists = $false
gh release view "v$version" >$null 2>&1
if ($LASTEXITCODE -eq 0) {
    $releaseExists = $true
}

if ($releaseExists) {
    Write-Host "Release v$version already exists. Uploading asset to existing release..." -ForegroundColor Yellow
    gh release upload "v$version" $releaseJarPath --clobber
} else {
    Write-Host "Release v$version does not exist. Creating new release..." -ForegroundColor Yellow
    gh release create "v$version" $releaseJarPath --title "v$version" --notes "Bump version to v${version} for Minecraft ${mcVersion}: add Count Items hotkey (with Enchanted Book formatting), and auto-disable default IRC chat on channel change or private message commands/events."
}

Write-Host "Successfully uploaded v$version!" -ForegroundColor Green

