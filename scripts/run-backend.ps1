# Runs the local backend on http://localhost:8080 for testing, including from the live GitHub Pages app.
#   powershell -ExecutionPolicy Bypass -File scripts/run-backend.ps1
# - JAVA_HOME: kept if it is JDK 17+ (Gradle 9 needs it), else the newest JDK 21 found, else the newest 17+.
# - RATE_LIMITS=off unless already set.
# - ALLOWED_ORIGINS gets https://stygiansummer.github.io, so the live site can call this backend
#   (its config.json has no API, so it falls back to http://localhost:8080).
# Extra arguments go to Gradle.

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pagesOrigin = 'https://stygiansummer.github.io'

function Get-JdkMajor([string]$jdkHome) {
    if (-not $jdkHome) { return 0 }
    $release = Join-Path $jdkHome 'release'
    if (-not (Test-Path $release)) { return 0 }
    $line = Select-String -Path $release -Pattern '^JAVA_VERSION="(\d+)' | Select-Object -First 1
    if ($line) { return [int]$line.Matches[0].Groups[1].Value }
    return 0
}

if ((Get-JdkMajor $env:JAVA_HOME) -lt 17) {
    $bases = @("$env:ProgramFiles\Java", "$env:ProgramFiles\Eclipse Adoptium", "$env:ProgramFiles\Microsoft",
               "$env:ProgramFiles\Zulu", "$env:ProgramFiles\Amazon Corretto", "$env:USERPROFILE\.jdks")
    $jdks = foreach ($b in $bases) {
        if (Test-Path $b) {
            Get-ChildItem -Directory $b | ForEach-Object {
                [pscustomobject]@{ Path = $_.FullName; Major = (Get-JdkMajor $_.FullName) }
            }
        }
    }
    $pick = $jdks | Where-Object { $_.Major -eq 21 } | Select-Object -First 1
    if (-not $pick) { $pick = $jdks | Where-Object { $_.Major -ge 17 } | Sort-Object Major -Descending | Select-Object -First 1 }
    if (-not $pick) { throw 'No JDK 17+ found. Install JDK 21 and set JAVA_HOME to it.' }
    $env:JAVA_HOME = $pick.Path
}
Write-Host "JAVA_HOME=$env:JAVA_HOME"

if (-not $env:RATE_LIMITS) { $env:RATE_LIMITS = 'off' }
if (-not $env:ALLOWED_ORIGINS) { $env:ALLOWED_ORIGINS = $pagesOrigin }
elseif ($env:ALLOWED_ORIGINS -notmatch [regex]::Escape($pagesOrigin)) { $env:ALLOWED_ORIGINS += ",$pagesOrigin" }
Write-Host "RATE_LIMITS=$env:RATE_LIMITS ALLOWED_ORIGINS=$env:ALLOWED_ORIGINS"

if (Get-NetTCPConnection -LocalPort 8080 -State Listen -ErrorAction SilentlyContinue) {
    Write-Warning 'Port 8080 is already in use (an older backend?). Stop it first, or this run will fail.'
}

Set-Location $root
& .\gradlew.bat :server:run @args
exit $LASTEXITCODE
