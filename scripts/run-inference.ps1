param([ValidateSet('cpu', 'gpu')][string]$Profile = 'gpu')
$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
& docker compose --profile $Profile up -d --force-recreate backend "inference-$Profile"
if ($LASTEXITCODE -ne 0) { throw "Could not start local inference profile '$Profile'." }
Write-Host "Inference service started in the backend's private network namespace on loopback only."
