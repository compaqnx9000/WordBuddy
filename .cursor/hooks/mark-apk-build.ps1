# Marks that a debug APK rebuild is needed when Android app sources change.
$ErrorActionPreference = 'Stop'
$inputJson = [Console]::In.ReadToEnd()
if ([string]::IsNullOrWhiteSpace($inputJson)) {
    Write-Output '{}'
    exit 0
}

try {
    $payload = $inputJson | ConvertFrom-Json
} catch {
    Write-Output '{}'
    exit 0
}

$path = @(
    $payload.file_path,
    $payload.filePath,
    $payload.path,
    $payload.uri
) | Where-Object { $_ } | Select-Object -First 1

if (-not $path) {
    Write-Output '{}'
    exit 0
}

$normalized = ($path -replace '\\', '/').ToLowerInvariant()
$needsBuild =
    $normalized -match '/app/src/' -or
    $normalized -match '/app/build\.gradle(\.kts)?$' -or
    $normalized -match '/gradle/libs\.versions\.toml$' -or
    $normalized -match '/settings\.gradle(\.kts)?$'

if ($needsBuild) {
    $flag = Join-Path (Get-Location) '.cursor/apk-build-needed'
    New-Item -ItemType File -Force -Path $flag | Out-Null
}

Write-Output '{}'
exit 0
