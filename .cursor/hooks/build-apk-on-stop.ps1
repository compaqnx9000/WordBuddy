# Rebuilds debug APK into outputs/ when Android sources were edited this turn.
$ErrorActionPreference = 'Stop'
$null = [Console]::In.ReadToEnd()

$root = Get-Location
$flag = Join-Path $root '.cursor/apk-build-needed'
if (-not (Test-Path $flag)) {
    Write-Output '{}'
    exit 0
}

Remove-Item -Force $flag -ErrorAction SilentlyContinue

$gradlew = Join-Path $root 'gradlew.bat'
if (-not (Test-Path $gradlew)) {
    Write-Output '{}'
    exit 0
}

& $gradlew :app:assembleDebug --quiet
Write-Output '{}'
exit 0
