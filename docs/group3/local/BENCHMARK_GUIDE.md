# Local Speech Benchmark Guide

## Dry run

Dry-run validates CSV/Markdown/JSON report creation without loading models and explicitly marks all inference metrics as not measured:

```powershell
docker compose --profile gpu run --rm --no-deps inference-gpu python /benchmarks/benchmark.py --dry-run --models-dir /models --benchmark-data-dir /benchmark_data --report-dir /models/reports
```

The command assumes models volume and image are prepared. A host-only Python 3.11 venv may run it with the pinned local package requirements; model assets remain outside git.

## Real STT corpus

Create local-only `benchmark_data/meta.csv` with columns:

```text
case,audio_file,language,reference,subject,region,gender,device
```

`audio_file` is a relative path beneath `benchmark_data/`, mono or stereo PCM16 WAV at 16 kHz. Do not add real student audio or identities to git. Keep signed consent and pseudonymous sample IDs outside the repository. Add `benchmark_data/glossary.txt` with one subject term per line. Then run:

```powershell
docker compose --profile gpu run --rm --no-deps inference-gpu python /benchmarks/benchmark.py --models-dir /models --benchmark-data-dir /benchmark_data --report-dir /models/reports
```

The current harness compares installed large-v3-turbo and base models with and without hotwords for final decoding. It computes NFC/casefold/punctuation-stripped token WER and CER, glossary-only WER, silence hallucination indicator, audio duration, latency and RTF. Metrics are stratified only after adding grouping aggregation; current report preserves per-row metadata for later analysis. GPU/RAM per-row readings are currently null; do not claim memory measurements until sampling is added.

## TTS benchmark

```powershell
docker compose --profile gpu run --rm --no-deps inference-gpu python /benchmarks/benchmark.py --tts-only --models-dir /models --report-dir /models/reports
```

The `--tts-only` mode runs without an STT corpus. It writes a WAV per fixed Vietnamese/English sample beneath ignored `reports/tts_samples/`, recording first generated chunk latency, total synthesis latency, audio duration, and RTF. Add `--tts` to the real STT command to run both suites. Human scoring fields (clarity, naturalness, terminology, pauses) must be completed by listeners; the harness does not invent subjective ratings.

## End-to-end latency

The pipeline dry-run reports a mock arithmetic/control-path latency under an explicit `dry_run_mock_components_only` status; it is not model/device latency. Actual STT/TTS endpoint-to-endpoint P50/P95 still needs the authenticated student simulator and a loaded model. Reports must identify host/GPU, model revision, quantization, audio duration distribution and whether the path included analyzer/TTS. The product target is P50 <= 2.0 s and P95 <= 3.5 s from answer end through next TTS first byte; failure on CPU or available GPU must be reported honestly.

Report files are local-only and ignored by git. Keep audio/transcripts private and delete test data according to the approved retention policy.
