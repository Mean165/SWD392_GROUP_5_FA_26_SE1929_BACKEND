param(
    [ValidateSet('cpu', 'gpu')]
    [string]$Profile = 'gpu',
    [switch]$InstallHostVenv
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$modelsPath = Join-Path $repoRoot 'models'
$dotenvPath = Join-Path $repoRoot '.env'

docker info *> $null
if ($LASTEXITCODE -ne 0) { throw 'Docker Desktop must be running.' }
New-Item -ItemType Directory -Force -Path $modelsPath | Out-Null

if (-not (Test-Path $dotenvPath)) {
    $examplePath = Join-Path $repoRoot '.env.example'
    if (Test-Path $examplePath) { Copy-Item $examplePath $dotenvPath }
    else { [System.IO.File]::WriteAllText($dotenvPath, '', [System.Text.UTF8Encoding]::new($false)) }
}

$content = [System.IO.File]::ReadAllText($dotenvPath)

foreach ($entry in @{ STT_PROVIDER = 'local'; TTS_PROVIDER = 'local'; STT_MODE = 'streaming_only' }.GetEnumerator()) {
    $pattern = '(?m)^\s*' + [regex]::Escape($entry.Key) + '\s*=.*(?:\r?\n|$)'
    if ([regex]::IsMatch($content, $pattern)) {
        $content = [regex]::Replace($content, $pattern, "$($entry.Key)=$($entry.Value)`r`n")
    } else {
        $content += "$($entry.Key)=$($entry.Value)`r`n"
    }
}

if (-not [regex]::IsMatch($content, '(?m)^LOCAL_INFERENCE_TOKEN=(?!generate-with-scripts/setup-inference\.ps1$)\S+')) {
    $tokenBytes = New-Object byte[] 32
    $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $random.GetBytes($tokenBytes)
    $random.Dispose()
    $token = [Convert]::ToBase64String($tokenBytes).TrimEnd('=').Replace('+', '-').Replace('/', '_')
    $content += "LOCAL_INFERENCE_TOKEN=$token`r`n"
    $token = $null
    [Array]::Clear($tokenBytes, 0, $tokenBytes.Length)
}
[System.IO.File]::WriteAllText($dotenvPath, $content, [System.Text.UTF8Encoding]::new($false))

if ($InstallHostVenv) {
    $python311 = Get-Command py -ErrorAction SilentlyContinue
    if (-not $python311) { throw 'Python 3.11 launcher was not found. Install Python 3.11 or omit -InstallHostVenv; Docker uses Python 3.11.' }
    & py -3.11 -m venv (Join-Path $repoRoot 'inference-service\.venv')
    if ($LASTEXITCODE -ne 0) { throw 'Could not create the Python 3.11 virtual environment.' }
    $venvPython = Join-Path $repoRoot 'inference-service\.venv\Scripts\python.exe'
    & $venvPython -m pip install --index-url 'https://download.pytorch.org/whl/cpu' 'torch==2.14.1+cpu'
    if ($LASTEXITCODE -ne 0) { throw 'Could not install the pinned PyTorch CPU runtime.' }
    & $venvPython -m pip install -r (Join-Path $repoRoot 'inference-service\requirements-dev.txt')
    if ($LASTEXITCODE -ne 0) { throw 'Could not install local inference development requirements.' }
}

if ($Profile -eq 'gpu') {
    if (-not (Get-Command nvidia-smi -ErrorAction SilentlyContinue)) { throw 'NVIDIA driver/nvidia-smi was not found. Use -Profile cpu.' }
    & docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi *> $null
    if ($LASTEXITCODE -ne 0) { throw 'Docker cannot access the NVIDIA GPU. Enable WSL2 GPU passthrough or use -Profile cpu.' }
}

& docker compose --profile $Profile build "inference-$Profile"
if ($LASTEXITCODE -ne 0) { throw "Could not build the local inference $Profile image." }
Write-Host "Local inference $Profile setup is prepared. Model download: .\scripts\download-models.ps1"
