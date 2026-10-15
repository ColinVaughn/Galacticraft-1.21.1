$ErrorActionPreference = 'Stop'
$codeql = (Get-Command codeql -ErrorAction Stop).Source
$repoRoot = Split-Path -Parent $PSScriptRoot
$outputDir = Join-Path $repoRoot '.gradle/codeql'
$database = Join-Path $outputDir 'java'
$results = Join-Path $outputDir 'results.sarif'

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null
Push-Location $repoRoot
try {
    # Recompile every module so CodeQL observes sources even with warm Gradle caches.
    & $codeql database create $database --language=java-kotlin "--source-root=$repoRoot" `
        '--command=cmd.exe /c gradlew.bat --no-daemon --no-build-cache --no-configuration-cache --rerun-tasks compileJava' `
        --threads=4 --ram=8192 --overwrite
    if ($LASTEXITCODE -ne 0) { throw "CodeQL database creation failed (exit $LASTEXITCODE)." }

    & $codeql database analyze $database 'codeql/java-queries:codeql-suites/java-security-extended.qls' `
        --format=sarif-latest "--output=$results" --sarif-category=java-kotlin --threads=4 --ram=8192
    if ($LASTEXITCODE -ne 0) { throw "CodeQL analysis failed (exit $LASTEXITCODE)." }

    Write-Output "CodeQL results: $results"
}
finally {
    Pop-Location
}