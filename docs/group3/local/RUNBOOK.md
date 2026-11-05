# Windows Local Speech Runbook

## One-time preparation

Prerequisites observed on the development host: Windows 11 x64, Docker Desktop with WSL2, NVIDIA driver 566.07, RTX 3050 Laptop GPU 4 GiB, about 6.1 GiB free on D:, Java 21 and Docker Compose. For GPU profile, verify Docker GPU passthrough with `docker run --rm --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi`. CPU profile does not need NVIDIA Container Toolkit.

From PowerShell in the backend repo:

```powershell
.scripts\setup-inference.ps1 -Profile gpu
.scripts\download-models.ps1 -Preset all
```

Downloads are revision-pinned. Primary STT source is Whisper large-v3-turbo converted to CTranslate2 int8_float16 under `models/stt/large-v3-turbo-ct2`; fallback is the pinned CTranslate2 base/int8 model under `models/stt/base`; Piper voices are the Vietnamese `vais1000-medium` and English `lessac-medium` files. Downloaded model/voice manifests include revision and SHA-256. The scripts check disk before writing. They do not check in weights.

The first setup requires Internet to download package wheels and model/voice files. After the model files are present, inference performs no external speech requests. The default large-model conversion may use most of the current 6 GiB free disk (source checkpoint, converted model, base fallback and voices). Preserve a further disk safety margin; if the preflight stops, run the base/voices presets first and free disk before the large model.

## Run AiViva

```powershell
.scripts\dev-up.ps1 -Profile gpu
docker compose ps
docker compose logs --tail=80 backend
```

CPU fallback:

```powershell
.scripts\dev-up.ps1 -Profile cpu
```

Inference binds to loopback in the backend's shared container network namespace; Compose does not publish port 8000. The backend and inference service use the same random `LOCAL_INFERENCE_TOKEN` stored only in ignored `.env`; it is never returned to FE. The setup script removes old speech-provider key lines by variable name without printing their values. It does not alter LLM/database/mail settings.

Check readiness through an authenticated browser session at `GET /api/student/device-check/stt/health`. Start only when both `stt.ready` and `tts.ready` are true for a complete mic/speaker check. Reasons include missing model files, loading, model load error, missing voices, local service unavailable, or internal token not configured. The API never reports ready simply because a provider name is selected.

Inspect hardware selection without exposing secrets:

```powershell
docker compose exec -T inference-gpu nvidia-smi
docker compose logs --tail=100 inference-gpu
```

Model logs report loaded model, device, compute type and load duration, not audio or transcript text. The health response reports actual device/compute type. If CUDA model loading fails, the service explicitly tries the same local model with a lighter GPU compute type and then the configured base CPU model; health shows the resulting model/device. If all attempts fail, STT remains not-ready.

## Mock-only UI testing

Mock providers are restricted to Spring `dev` and `test` profiles. Use a local dev profile and `STT_PROVIDER=mock`, `TTS_PROVIDER=mock`; mock transcripts and tones are fixtures and are not speech recognition/natural voice. Do not enable mock in production.

## Data handling

- DEVICE_CHECK PCM is session memory only and is discarded on stop/disconnect; neither its audio nor transcript is persisted.
- EXAM PCM is buffered only to the configured turn limit and stored locally as WAV through the existing audio storage service when the exam stream is submitted. `AUDIO_RETENTION_DAYS` cleanup removes matching stored audio files after the configured age; DB audit/transcript rows are retained.
- Never enable debug logging that serializes request bodies, frame bytes, transcript strings or tokens. Internal token must not appear in command output or logs.

## Local endpoints

Internal service endpoints and request/event schemas are in `INFERENCE_API.md` and `inference-service/openapi.json`. For local diagnosis, use the authenticated BE health response; do not publish the internal service port to the LAN.

## Troubleshooting

| Symptom | Action |
|---|---|
| Health reason says model missing | Run `scripts/download-models.ps1 -Preset all`; verify `models/stt` and `models/tts` manifests. |
| GPU profile does not see GPU | Check host `nvidia-smi`, Docker Desktop WSL2 GPU passthrough, and `docker run --gpus all ... nvidia-smi`; use CPU profile if unavailable. |
| CUDA/cuDNN load failure | Rebuild GPU profile; health will show explicit fallback. Check container logs for exception class only. |
| Health says internal auth not configured | Run `scripts/setup-inference.ps1`; it writes a random token to ignored `.env`, then recreate backend and inference services together. |
| Health says inference unreachable | Run `scripts/dev-up.ps1 -Profile gpu` or `cpu`; backend and inference must be recreated together because inference shares the backend container's loopback namespace. Do not recreate backend alone and keep the old inference container. |
| Audio is silent/poor | Check selected mic and FE meter, consent, 16 kHz PCM contract and language; then test with a consented known sample, not an unconsented recording. |
| Disk is low | Keep only the chosen model and Piper voices; do not delete source/converted files while a conversion is active. Do not delete saved exam recordings manually without following retention policy. |
| Windows/Python setup fails | Recommended serving path uses Docker Python 3.11, avoiding host Python 3.14 ML wheel compatibility. Use PowerShell scripts from repo root; paths with spaces are handled via volume arguments. |

## Security maintenance

If a speech key was ever committed, revoke it in the issuing account and inspect repository history. No history rewrite is performed by this change. This branch snapshot did not stage `.env`; `.env` is ignored and untracked.
