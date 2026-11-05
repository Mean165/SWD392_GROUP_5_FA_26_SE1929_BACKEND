param(
    [ValidateSet('all', 'large-v3-turbo', 'base', 'voices', 'korva')]
    [string]$Preset = 'all',
    [switch]$Force
)

$ErrorActionPreference = 'Stop'
$repoRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
$modelsPath = Join-Path $repoRoot 'models'
$driveRoot = [System.IO.Path]::GetPathRoot($modelsPath)
$drive = [System.IO.DriveInfo]::new($driveRoot)
$largeReady = Test-Path (Join-Path $modelsPath 'stt\large-v3-turbo-ct2\model.bin')
$baseReady = (Test-Path (Join-Path $modelsPath 'stt\base\model.bin')) -and
    (Test-Path (Join-Path $modelsPath 'stt\base\vocabulary.txt')) -and
    (Test-Path (Join-Path $modelsPath 'stt\base\model-manifest.json'))
$voicesReady = (Test-Path (Join-Path $modelsPath 'tts\vi_VN-vais1000-medium.manifest.json')) -and
    (Test-Path (Join-Path $modelsPath 'tts\en_US-lessac-medium.manifest.json'))
$korvaReady = (Test-Path (Join-Path $modelsPath 'tts\korvatts\model-manifest.json')) -and
    (Test-Path (Join-Path $modelsPath 'tts\korvatts\onnx\vocoder.onnx')) -and
    (Test-Path (Join-Path $modelsPath 'tts\korvatts\voice_styles\khanh_vy.json'))
$requiredFreeBytes = switch ($Preset) {
    'large-v3-turbo' { if ($largeReady -and -not $Force) { 0 } else { 4GB } }
    'base' { if ($baseReady -and -not $Force) { 0 } else { 512MB } }
    'voices' { if ($voicesReady -and -not $Force) { 0 } else { 256MB } }
    'korva' { if ($korvaReady -and -not $Force) { 0 } else { 512MB } }
    'all' {
        if ($Force -or -not $largeReady) { 4GB }
        else { [long]0 + $(if (-not $baseReady) { 512MB } else { 0 }) + $(if (-not $voicesReady) { 256MB } else { 0 }) + $(if (-not $korvaReady) { 512MB } else { 0 }) }
    }
}
if ($drive.AvailableFreeSpace -lt $requiredFreeBytes) {
    $requiredGiB = [math]::Round($requiredFreeBytes / 1GB, 2)
    throw "At least $requiredGiB GiB must be free on $driveRoot for preset '$Preset'."
}

$servicePath = Join-Path $repoRoot 'inference-service'
$mountModels = "$modelsPath`:/models"
$mountService = "$servicePath`:/service:ro"
$forceArg = if ($Force) { '--force' } else { '' }
if ($Preset -in @('all', 'large-v3-turbo')) {
    $install = 'python -m pip install --quiet --index-url https://download.pytorch.org/whl/cpu torch==2.14.1+cpu && python -m pip install --quiet -r /service/requirements-convert.txt'
} else {
    $install = 'python -m pip install --quiet huggingface-hub==2.4.0'
}
$command = "$install && python /service/scripts/prepare_assets.py --models-dir /models --preset $Preset $forceArg"
& docker run --rm -v $mountModels -v $mountService -w /service python:3.11-slim sh -c $command
if ($LASTEXITCODE -ne 0) { throw 'Model/voice download or conversion failed. See the sanitized converter error above.' }
Write-Host "Requested model assets are prepared under $modelsPath. Their SHA-256 values are recorded in model-manifest.json files."
