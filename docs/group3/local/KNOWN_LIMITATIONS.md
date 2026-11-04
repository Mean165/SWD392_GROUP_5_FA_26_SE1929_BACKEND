# Known Limitations

- Pinned Whisper large-v3-turbo, base fallback, and Piper Vietnamese/English voice weights were downloaded to ignored `models/` and loaded in smoke runs. A clean machine remains not-ready until downloads and warm-up complete.
- Whisper is not a native streaming decoder. Partial text is simulated by periodically re-decoding a bounded VAD speech window and applying a common-prefix agreement heuristic; it costs repeated inference and may revise/omit unstable suffix words.
- RTX 3050 Laptop GPU has 4 GiB VRAM. Large-v3-turbo loaded as CUDA `int8_float16` in this run; GPU memory under longer concurrent exam sessions and fallback under actual VRAM pressure still need testing. CPU base int8 loaded, but representative CPU latency is not benchmarked.
- A local Piper server currently produces a complete WAV in its synthesis/cache stage before the Java HTTP response finishes assembling the valid WAV container. The service has a chunk-oriented path, but true first-sentence audio-to-FE streaming through a valid browser-playable container remains to be measured and optimized.
- The current speech analyzer asks an LLM for a follow-up question only; if the separately configured LLM provider is a cloud provider, transcript text can still leave the host through the interview analyzer. This request localizes speech inference, not the LLM. Disable/select a local LLM if the no-data-egress requirement includes analyzer text too.
- Exam WAV retention reuses the existing local audio directory and daily age cleanup. It does not erase DB transcript/audit rows. Verify storage encryption, backups and retention settings for the deployment.
- Mock STT/TTS are available only with Spring `dev` or `test` profile. Their output is synthetic and must not be mistaken for speech recognition or a real voice.
- Only synthetic speech was tested. No fairness/accent/gender WER, human listening result, consented corpus benchmark, or statistically meaningful P50/P95 is available yet.
