# Local STT/TTS Plan

The cloud-provider prompt is superseded. All speech bytes stay in the local AiViva host/runtime. No API key, cloud fallback, browser speech API, or user audio upload is used by the speech stack.

## Decisions

- Keep the existing Java/Spring FE-facing `SpeechToTextProvider`, `TTSProvider`, `/ws/stt`, device-check health, and TTS endpoint contracts. Implement Java adapters that call a private local FastAPI service over loopback/internal container namespace with `X-Internal-Token`.
- Inference stack: Python 3.11, FastAPI, faster-whisper/CTranslate2, Silero VAD, Piper. Pin the exact versions tested in the created `requirements.txt`; interrogate API signatures and run real smoke tests before finalizing.
- STT first candidate: original OpenAI `whisper-large-v3-turbo` weights with verified upstream license, locally converted to CTranslate2, CUDA `int8_float16`, batch 1 on the RTX 3050 4GB. If real load or benchmark fails VRAM/latency acceptance, explicitly fall back to `small`/`base` CPU int8 and report the measured limitation. Do not use unverified third-party converted turbo weights.
- VAD cuts speech segments; the internal WebSocket emits bounded-window partial hypotheses and LocalAgreement stable prefixes, then a final decode on BE `commit`/`end`. This is simulated streaming for a non-streaming Whisper model. BE endpointing is the final authority for answer-turn boundaries.
- TTS: Piper `vi_VN-vais1000-medium` and `en_US-lessac-medium`, local cache and chunked audio. Do a real synthesis smoke test and report limitations; do not claim voice quality without user listening.
- `STT_MODE`: `streaming_only`, `final_only`, `dual`. Default `dual` only if measured latency/resource budget allows; otherwise `streaming_only`. Reconciliation retains raw streaming/final text and stores normalized/correction data separately.
- Preserve existing `InterviewAudioStorageService` and `INTERVIEW_AUDIO_STORAGE_DIRECTORY`; save EXAM audio only when the current configured audio retention path is enabled, and enforce configured `AUDIO_RETENTION_DAYS`. DEVICE_CHECK audio/transcript remain transient and are not saved.
- Preserve Gemini/OpenAI LLM paths; remove only speech-specific OpenAI adapters/settings. Speech never performs grading.
- GPU Docker uses NVIDIA CUDA 12 + cuDNN 9 runtime and `--gpus all`; CPU Docker uses Python 3.11 slim. The inference service binds to loopback/shared backend network namespace; do not publish its port. Local token is required on every service request.

## Milestones and Acceptance

1. L0 survey, license notes, exact deletion log, branch and snapshot completed before removal.
2. L1 remove cloud speech code/config/tests/docs/dependencies and old keys; add a test/CI scan that excludes only `.git` and the deletion log; preserve Mock and unrelated LLM providers; build/tests green.
3. L2 inference-service scaffolding, OpenAPI, auth, health, resource metrics, local TTS, CPU/GPU Docker profiles, Windows scripts, and fake-server tests.
4. L3 VAD/partial/final STT with model warm-up, LocalAgreement, hallucination checks, glossary hotwords, timestamps, and synthetic edge-case tests.
5. L4 Java local adapters, mode coordinator, ring-buffer reconnect, EXAM audio/storage policy, resource tracker, and health aggregation.
6. L5 minimal FE health/privacy/type changes, device-check mic/speaker working locally, and interview integration without altering exam scoring responsibilities.
7. L6 benchmark artifacts/docs. Do not claim numeric latency/accuracy targets unless measured on this machine; state whether P50/P95 goals were met.

## Open Risks / Assumptions

- CPU model/core count was not retrievable in this environment; only x64, 16 logical processors, 32.4 GB RAM and GPU were measured.
- D: has about 6.1 GiB free. Original model conversion may temporarily require source and converted files; setup must check free disk and remove temporary source/cache only after verifying output.
- Piper engine is GPLv3. The voice/model/dataset licenses differ. Distribution obligations need review before deployment.
- Benchmark authentic recordings and geographic/gender metadata are not present. Accuracy/fairness results cannot be certified using synthetic speech alone.
- Docker Compose Windows loopback between separate containers needs a shared network namespace or a loopback-bound inference process in the backend container. The implementation must test the selected arrangement and never publish port 8000 to the LAN.
