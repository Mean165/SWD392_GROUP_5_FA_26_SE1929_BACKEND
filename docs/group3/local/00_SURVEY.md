# Group 3 Local STT/TTS Survey

Survey date: 2026-11-04. Branch: `chore/local-inference`. Pre-removal snapshot: `a0df337`.

## Machine and Toolchain

| Item | Observed value |
|---|---|
| Host OS | Windows 11 Pro, build 26200, x64 |
| CPU | Intel64 Family 6 Model 154, 16 logical processors. Windows `systeminfo` denied the processor model line, so the marketing model/core count could not be verified. |
| RAM | `systeminfo`: 32,387 MB total, 6,381 MB available at measurement time (about 31.6 GiB total / 6.2 GiB available). |
| GPU | NVIDIA GeForce RTX 3050 Laptop GPU, 4,096 MiB VRAM, driver 566.07, host CUDA compatibility 12.7, WDDM. |
| GPU in Docker | Confirmed: `docker run --gpus all nvidia/cuda:12.4.1-base-ubuntu22.04 nvidia-smi` sees the RTX 3050 and driver. The test container reports CUDA compatibility 12.7. |
| Disk D: | NTFS, 32,235,835,392 bytes total, 6,533,935,104 bytes free when measured. `fsutil volume diskfree` was denied; .NET DriveInfo succeeded. |
| Python on Windows | Python 3.14.3 only. For this service use a containerized Python 3.11 runtime because the installed ML packages were verified there, not on host Python 3.14. |
| Python package probe | In a disposable `python:3.11-slim` container: `faster-whisper 1.2.1`, `ctranslate2 4.8.5`, `silero-vad 6.2.3`, `piper-tts 1.8.0`. Actual callable signatures are summarized below. |
| Java/Maven | Host Java 21.0.8; Docker build uses Eclipse Temurin Java 21 and Maven 3.9.6. Spring Boot 3.3.4, Maven project. |
| Docker/WSL | Docker Engine 29.8.2, Compose 5.5.1, Docker Desktop WSL2 backend. GPU passthrough was exercised successfully. |
| Network/model hosts | HTTPS HEAD to `huggingface.co` returned 200. Official sherpa model artifact URL returned a downloadable attachment response. No model weight was committed. |

## Existing App Survey

- Backend live STT boundary: `SpeechToTextProvider` and `/ws/stt`; the handler accepts authenticated student JWT in `access_token`, expects start/consent, then mono PCM16LE 16 kHz binary frames, and emits `partial`, `final`, `error`, `ended`.
- Existing live provider beans include third-party cloud streaming implementations and a deterministic Mock provider. The old one-shot multipart/interview audio path also uses a cloud transcription adapter.
- Existing TTS boundary is `TTSProvider`; device-check uses `GET /api/student/device-check/tts?language=vi|en`; interview question/turn code uses `TextToSpeechService` and its stream/cache path. Existing providers include cloud and Mock implementations.
- Health is `GET /api/student/device-check/stt/health`. The React `DeviceCheckPage` calls `getDeviceCheckSttHealth`; it disables start when `ready` is false and uses the two-step mic/speaker workflow. FE microphone capture already produces PCM16LE 16 kHz with AudioWorklet and sends through `/ws/stt`.
- FE has dirty pre-existing changes in its worktree at survey time. Preserve them and limit FE edits to local-provider health/privacy strings and cloud-only package/test references. Its current dependency list contains `@elevenlabs/elevenlabs-js`.
- Existing transcript audit table/entity: `stt_result` / `SttResultEntity`; current exam transcript is also stored in `interaction_turn.transcript_text`. Session event entities exist. Existing interview audio storage uses `InterviewAudioStorageService` and `INTERVIEW_AUDIO_STORAGE_DIRECTORY=./uploads/audio`; live WebSocket STT currently does not store the raw frame stream.
- Existing endpointing is primarily client stop and provider finish. `STTTurnSession` contains an in-memory PCM ring buffer and reconciliation utilities but is not yet the production WebSocket orchestrator. Existing enum values `realtime_only`, `batch_only`, `dual` will be replaced with local names required by this task.
- The shared LLM config is also consumed by unrelated question-generation paths. Remove speech-only fields/adapters but retain LLM configuration and keys unless a reference proves a setting is speech-only.
- Database migrations are manual numbered SQL files; no Flyway/Liquibase runner is configured. Preserve audit rows and use a new forward migration if schema needs changes.

## Hardware-Based Model Choice

The laptop has a 4 GiB display GPU with roughly 0.7 GiB already used by Windows at survey time. Start with `openai/whisper-large-v3-turbo` converted locally to CTranslate2, using CUDA `int8_float16`, batch size 1, and beam size 1 or 2. This is the quality-first configuration that may fit the remaining VRAM; verify with an actual load and record peak memory. Do not use faster-whisper's current `large-v3-turbo` alias until its converted model repository provenance/license is pinned and verified.

If GPU load fails or memory headroom is insufficient, explicitly fall back to `small`/`base` CTranslate2 `int8` on CPU and expose the actual model/device in health. Do not silently substitute. Benchmark both on available audio before selecting a default. The fastest-whisper project documents CUDA 12 + cuDNN 9 requirements and `int8_float16`/CPU `int8`; its published GPU figures are for other hardware and are not a substitute for this laptop's measurements.

Streaming approach: Silero VAD on 16 kHz PCM, partial re-decode on a bounded active-speech window at a measured interval, LocalAgreement for stable prefixes, then a final decode at endpointing. This is simulated streaming around Whisper, not a streaming neural decoder. The 1-second partial interval and 25-second max segment are initial tunables, not measured guarantees.

## TTS Choice

Start with Piper's `piper-tts` Python API and the Vietnamese `vi_VN-vais1000-medium` and English `en_US-lessac-medium` voices. The installed API was introspected: `PiperVoice.load(model_path, config_path=None, use_cuda=False, ...)`, `PiperVoice.synthesize(text, syn_config=None, ...) -> Iterable[AudioChunk]`, and `synthesize_wav(...)`. The streaming chunk interface exists, but generated audio still requires an actual listening test before the voice can be accepted for exams. Keep WAV output available to current FE.

Piper is a separate local inference dependency, not a cloud API. Its current Python engine distribution is GPLv3; the selected Vietnamese voice card references a CC-BY-4.0 source dataset, while the English Lessac voice card points to the Blizzard 2013 dataset license. See `LICENSES.md`; distribution/commercial rights must be reviewed before deployment.

## API Introspection Performed

- `WhisperModel.__init__` accepts `model_size_or_path`, `device`, `compute_type`, `cpu_threads`, `download_root`, `local_files_only`, `revision`, etc.
- `WhisperModel.transcribe` accepts `language`, `task`, `beam_size`, `condition_on_previous_text`, `initial_prompt`, `word_timestamps`, `vad_filter`, `vad_parameters`, `hotwords`, and returns a segment generator plus `TranscriptionInfo`. The segment generator must be iterated to execute inference.
- Silero 6.2.3 `load_silero_vad(onnx=False, opset_version=16, sequence=False, sampling_rate=16000, max_frames=512)`; `VADIterator(model, threshold=0.5, sampling_rate=16000, min_silence_duration_ms=100, speech_pad_ms=30)`; official implementation supports 8/16 kHz.
- Piper 1.8.0 signatures and chunk API are above. All package versions were imported/introspected in a disposable Python 3.11 container. Model weights and actual audio inference are still pending at this survey stage.

## Known Environment Constraints

- Windows host Python 3.14.3 is not selected for model serving; inference uses Python 3.11 in Docker.
- NVIDIA Docker passthrough works. The GPU's 4 GiB limit and 6.5 GB disk free constrain model choice and duplicate downloads.
- The user-provided benchmark dataset and authentic SV recordings are absent. Do not invent WER/CER or fairness results. Initial smoke benchmarks may use generated/synthetic speech and must be labeled synthetic; real accuracy/latency acceptance requires a consented test corpus.
- User-facing CPU/GPU measurements, thermal sustained latency, Vietnamese accent/domain WER, Piper listening quality, and commercial license clearance are not yet measured.
