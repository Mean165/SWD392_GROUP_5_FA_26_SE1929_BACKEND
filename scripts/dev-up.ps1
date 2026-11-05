param([ValidateSet('cpu', 'gpu')][string]$Profile = 'gpu')
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& docker compose --profile $Profile up -d --build --force-recreate backend "inference-$Profile"
if ($LASTEXITCODE -ne 0) { throw "Could not start AiViva with local inference profile '$Profile'." }
Write-Host 'AiViva backend and local speech inference are starting. Poll /api/student/device-check/stt/health until both engines are ready.'
