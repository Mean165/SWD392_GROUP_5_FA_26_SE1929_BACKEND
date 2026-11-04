# Group 3 FE ↔ Backend Contract

The browser communicates only with Spring Boot. It must not call the private inference-service or use browser speech-recognition APIs.

## Health

`GET /api/student/device-check/stt/health` requires the student's JWT in the normal Authorization header. Spring returns HTTP 200 even when local inference is down; readiness is reported in JSON so the page can render a reason instead of a generic HTTP failure:

```json
{
  "stt": {
    "ready": true,
    "provider": "local",
    "engine": "faster-whisper/CTranslate2 + Silero VAD",
    "model": "large-v3-turbo-ct2",
    "device": "cuda",
    "compute_type": "int8_float16",
    "reason": "",
    "languages": ["vi-VN", "en-US"]
  },
  "tts": {
    "ready": true,
    "provider": "local",
    "engine": "piper",
    "model": "vi_VN-vais1000-medium.onnx+en_US-lessac-medium.onnx",
    "device": "cpu",
    "reason": "",
    "languages": ["vi-VN", "en-US"]
  },
  "resources": {
    "gpu_mem_used_mb": 1200,
    "gpu_mem_total_mb": 4096,
    "cpu_percent": 5.0,
    "ram_used_mb": 2048,
    "ram_total_mb": 16000
  },
  "versions": {},
  "sampleRate": 16000,
  "encoding": "pcm_s16le",
  "maxDurationSeconds": 60
}
```

Example values above are illustrative; use the actual response. The FE may begin a microphone check only when `stt.ready === true`. `reason` may be a technical identifier or short message; map known reasons to localized copy and retain an unknown-reason fallback.

## Device Check Speaker

`GET /api/student/device-check/tts?language=vi|en` uses the regular JWT and returns a playable WAV response when local Piper is ready. The browser should report TTS failures as a speaker-step issue and allow the optional device check to be skipped, consistent with the existing page flow.

## Exam STT

The same authenticated `/ws/stt` endpoint is used for `DEVICE_CHECK` and `EXAM`. Exam `start` additionally requires `attemptId` and `turnId`. When the student sends `stop`, Spring finalizes the transcript and submits it to the existing interview turn flow; the AI analyzer's role is to select a follow-up question, not score the student. Original EXAM audio is retained only through the existing configured interview-audio storage path and retention setting.

The browser never receives internal inference URL/token, local resource paths, model files, model prompts, analyzer internals or another student's metadata.
