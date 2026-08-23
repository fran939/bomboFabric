$version = Select-String -Path "gradle.properties" -Pattern "mod_version=(.*)" | ForEach-Object { $_.Matches[0].Groups[1].Value }
$jarPath = "build/libs/bomboaddons-$version.jar"

Write-Host "Building project for v$version..." -ForegroundColor Yellow
Remove-Item -Path "build\resources" -Recurse -Force -ErrorAction SilentlyContinue
Remove-Item -Path "build\classes" -Recurse -Force -ErrorAction SilentlyContinue
cmd /c 'gradlew.bat compileClientJava processResources jar --no-daemon'
if ($LASTEXITCODE -ne 0) {
    Write-Host "Build failed with exit code $LASTEXITCODE! Aborting upload." -ForegroundColor Red
    exit 1
}

if (-not $env:GH_TOKEN) {
    try {
        $credOutput = @('protocol=https', 'host=github.com', '') | git credential fill
        foreach ($line in $credOutput) {
            if ($line -like 'password=*') {
                $env:GH_TOKEN = $line.Substring(9)
                break
            }
        }
    } catch {}
}

Write-Host "Creating/Uploading GitHub Release for v$version..." -ForegroundColor Cyan
& "C:\Program Files\GitHub CLI\gh.exe" release create "v$version" $jarPath --repo "fran939/bomboFabric" --title "v$version" --notes "Release v${version} - Bestiary highlight GUI check, item hotkeys, texture toggle, and modifier swap fixes."
& "C:\Program Files\GitHub CLI\gh.exe" release upload "v$version" $jarPath --repo "fran939/bomboFabric" --clobber

Write-Host "Successfully uploaded v$version!" -ForegroundColor Green
