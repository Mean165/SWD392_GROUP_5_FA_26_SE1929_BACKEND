# Performance Report

Status: **synthetic model-backed smoke tests ran on CPU and GPU**. Vietnamese TTS now uses KorvaTTS; English uses Piper. Values below are single samples, not a corpus benchmark or product P50/P95; listening quality still requires human confirmation.

## Host

- Windows 11 Pro x64; Intel family 6 model 154, 16 logical processors (~2.5 GHz reported; exact CPU model inaccessible).
- 32.4 GB physical RAM; 6.4 GB available at initial survey.
- NVIDIA GeForce RTX 3050 Laptop GPU, 4 GiB VRAM; driver 566.07. Docker `--gpus all` passthrough works.
- D: 30.0 GiB total; 3.47 GiB free after model and image installation. C: 15.75 GiB free at measurement.

## Runtime Versions and Tests

- CPU image: Python 3.11, PyTorch CPU 2.14.1, faster-whisper 1.2.1, CTranslate2 4.8.5, Silero VAD 6.2.3, KorvaTTS 0.1.3, Piper 1.8.0, FastAPI 0.162.0, Uvicorn 0.54.0.
- GPU image: Python 3.10, CUDA 12.3.2/cuDNN 9, PyTorch CPU 2.14.1 for Silero VAD; CTranslate2 used CUDA for STT.
- Python inference tests: 9 passed, including VAD-missed speech fallback, Vietnamese promotional-audio filtering, and Korva 44.1 kHz WAV contract. Java backend suite: 153 tests passed, including question-plan repair and local HTTP adapter contracts (one token-dependent runtime test is skipped in the full suite). FE device-check tests: 8 passed; interview plan UI tests: 3 passed; production build passed with an existing bundle-size warning.

## Synthetic Smoke Samples

| Path | Result |
|---|---|
| Korva Vietnamese fixed-sentence benchmark | Two Vietnamese samples: 5.04-5.42 s WAV took 4.41-8.13 s in a CPU-only benchmark container (RTF 0.88-1.50). Full utterance synthesis means first audio waits for the entire sentence. |
| CPU Whisper base int8 batch on that generated WAV | HTTP 200; 829 ms; transcript 52 characters; confidence 0.614; no warning. |
| Previous Piper Vietnamese sample | HTTP 200; about 2.54 s audio; first measured call 1,319 ms. Voice was reported by user as mispronouncing Vietnamese; it is no longer the default. |
| GPU Whisper large-v3-turbo int8_float16 batch on generated WAV | HTTP 200; 1,150 ms; transcript 51 characters; confidence 0.967; no warning. |
| GPU WebSocket synthetic utterance | `ready`, partials, committed, closed observed on the successful run; a separate generated long utterance was filtered without a final hypothesis and now exercises recoverable `TRANSCRIPT_UNRELIABLE`. |
| Cold model load | large-v3-turbo loaded on CUDA in 5.6-6.1 s; Piper voices in about 1.9 s in the GPU image. |
| Piper English samples | First chunk 198-214 ms, audio duration 3.56-5.21 s, RTF 0.041-0.055. |

These are generated samples, not human speakers or a test corpus. They verify model load and interface shape; they do not establish pronunciation quality. Korva WAV samples are available under ignored `reports/tts_samples/` for direct listening; benchmark outputs are in `reports/tts_benchmark_20261105.md`. The newest benchmark was variable and reached RTF 1.50 for one sample, so Vietnamese exam-question latency may be noticeable on CPU. Confidence is not calibrated from this sample.

## Acceptance Metrics Still Pending

- Product target from end-of-answer to first byte of the next TTS: P50 <= 2.0 s / P95 <= 3.5 s. **Not measured end to end.** The sample timings exclude the analyzer and FE playback path; the current Java response may buffer a complete WAV.
- Vietnamese domain WER/CER, glossary accuracy, silence/noise hallucination rate, and demographic/accent breakdown: **not measured**; no consented human-transcribed corpus was supplied.
- Sustained GPU VRAM, long-session thermal latency, broad CPU RTF distribution, true first-byte streaming, and human listening approval for Korva/Piper voices: **not measured**.

Run `docs/group3/local/BENCHMARK_GUIDE.md` with consented recordings and human transcripts for meaningful distributions and accuracy. Do not use generated smoke samples as acceptance evidence.
