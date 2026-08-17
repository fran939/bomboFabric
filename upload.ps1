$version = Select-String -Path "gradle.properties" -Pattern "mod_version=(.*)" | ForEach-Object { $_.Matches[0].Groups[1].Value }
$jarPath = "build/libs/bomboaddons-$version.jar"

if (-not (Test-Path $jarPath)) {
    Write-Host "JAR not found at $jarPath. Building project..." -ForegroundColor Yellow
    cmd /c 'gradlew.bat compileClientJava jar -x processResources'
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

Write-Host "Creating GitHub Release for v$version..." -ForegroundColor Cyan
& "C:\Program Files\GitHub CLI\gh.exe" release create "v$version" $jarPath --repo "fran939/bomboFabric" --title "v$version" --notes "Release v${version} - Bestiary highlight GUI check, item hotkeys, texture toggle, and modifier swap fixes."

Write-Host "Successfully uploaded v$version!" -ForegroundColor Green
