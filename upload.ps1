$version = Select-String -Path "gradle.properties" -Pattern "mod_version=(.*)" | ForEach-Object { $_.Matches[0].Groups[1].Value }
$jarPath = "build/libs/bomboaddons-$version.jar"

if (-not (Test-Path $jarPath)) {
    Write-Host "JAR not found at $jarPath. Building project..." -ForegroundColor Yellow
    .\gradlew build
}

Write-Host "Creating GitHub Release for v$version..." -ForegroundColor Cyan
& "C:\Program Files\GitHub CLI\gh.exe" release create "v$version" $jarPath --title "v$version" --notes "Fixed Base64 IllegalArgumentException in configuration persistence, resolved Pest Tracer 2D rendering issues, and refined Hotbar Swapper virtual inventory logic."

Write-Host "Successfully uploaded v$version!" -ForegroundColor Green
