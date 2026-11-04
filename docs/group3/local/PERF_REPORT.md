# Performance Report

Status: **synthetic model-backed smoke tests ran on CPU and GPU**. Values below are single samples, not a corpus benchmark or product P50/P95; they do not establish speech accuracy or voice quality.

## Host

- Windows 11 Pro x64; Intel family 6 model 154, 16 logical processors (~2.5 GHz reported; exact CPU model inaccessible).
- 32.4 GB physical RAM; 6.4 GB available at initial survey.
- NVIDIA GeForce RTX 3050 Laptop GPU, 4 GiB VRAM; driver 566.07. Docker `--gpus all` passthrough works.
- D: 30.0 GiB total; 3.47 GiB free after model and image installation. C: 15.75 GiB free at measurement.

## Runtime Versions and Tests

- CPU image: Python 3.11, PyTorch CPU 2.14.1, faster-whisper 1.2.1, CTranslate2 4.8.5, Silero VAD 6.2.3, Piper 1.8.0, FastAPI 0.162.0, Uvicorn 0.54.0.
- GPU image: Python 3.10, CUDA 12.3.2/cuDNN 9, PyTorch CPU 2.14.1 for Silero VAD; CTranslate2 used CUDA for STT.
- Python fake-engine contract tests: 6 passed. Java backend suite: 149 tests passed, including the local HTTP adapter contract tests. FE device-check focused tests/build: 8 passed; production build passed with an existing bundle-size warning.

## Synthetic Smoke Samples

| Path | Result |
|---|---|
| CPU Piper Vietnamese synthesis | HTTP 200; 112-114 KB WAV, about 2.54 s audio; first measured call 965 ms. |
| CPU Whisper base int8 batch on that generated WAV | HTTP 200; 829 ms; transcript 52 characters; confidence 0.614; no warning. |
| GPU Piper Vietnamese synthesis | HTTP 200; about 2.54 s audio; first measured call 1,319 ms. |
| GPU Whisper large-v3-turbo int8_float16 batch on generated WAV | HTTP 200; 1,150 ms; transcript 51 characters; confidence 0.967; no warning. |
| GPU WebSocket synthetic utterance | `ready`, partials, committed, closed observed on the successful run; a separate generated long utterance was filtered without a final hypothesis and now exercises recoverable `TRANSCRIPT_UNRELIABLE`. |
| Cold model load | large-v3-turbo loaded on CUDA in 5.6-6.1 s; Piper voices in about 1.9 s in the GPU image. |
| CPU Piper-only 4-case benchmark | First chunk 113-197 ms; audio duration 3.2-5.2 s; RTF 0.035-0.045. Synthetic WAV samples and CSV/Markdown are in ignored `reports/`. |

These samples are Piper-generated speech, not human speakers or a test corpus. They only verify that models load and interfaces return data. Confidence is not calibrated from this sample.

## Acceptance Metrics Still Pending

- Product target from end-of-answer to first byte of the next TTS: P50 <= 2.0 s / P95 <= 3.5 s. **Not measured end to end.** The sample timings exclude the analyzer and FE playback path; the current Java response may buffer a complete WAV.
- Vietnamese domain WER/CER, glossary accuracy, silence/noise hallucination rate, and demographic/accent breakdown: **not measured**; no consented human-transcribed corpus was supplied.
- Sustained GPU VRAM, long-session thermal latency, CPU RTF, true first-byte streaming, and human listening approval for both voices: **not measured**.

Run `docs/group3/local/BENCHMARK_GUIDE.md` with consented recordings and human transcripts for meaningful distributions and accuracy. Do not use generated smoke samples as acceptance evidence.
