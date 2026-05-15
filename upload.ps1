$version = Select-String -Path "gradle.properties" -Pattern "mod_version=(.*)" | ForEach-Object { $_.Matches[0].Groups[1].Value }
$jarPath = "build/libs/bomboaddons-$version.jar"

if (-not (Test-Path $jarPath)) {
    Write-Host "JAR not found at $jarPath. Building project..." -ForegroundColor Yellow
    .\gradlew build
}

Write-Host "Creating GitHub Release for v$version..." -ForegroundColor Cyan
& "C:\Program Files\GitHub CLI\gh.exe" release create "v$version" $jarPath --title "v$version" --notes "Expanded playtime tracking to include all major SkyBlock areas: Crimson Isle, Spider's Den, The End, The Park, Deep Caverns, Gold Mine, The Barn, and Mushroom Desert."

Write-Host "Successfully uploaded v$version!" -ForegroundColor Green
