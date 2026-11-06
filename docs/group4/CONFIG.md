# Group 4 Configuration

| Environment variable | Default | Meaning |
| --- | --- | --- |
| `GRADING_AI_PROVIDER` | `disabled` | `disabled` or `openai-compatible`; `mock` is allowed only under `test`/`dev` Spring profiles. |
| `GRADING_AI_BASE_URL` | `http://127.0.0.1:11434/v1` | OpenAI-compatible API base; adapter calls `/models` and `/chat/completions`. |
| `GRADING_AI_MODEL` | `local-model` | Must appear in `/models`. |
| `GRADING_AI_API_KEY` | empty | Optional server-side bearer key for compatible local server. Never returned/logged. |
| `GRADING_AI_TIMEOUT_SECONDS` | `90` | Completion timeout; capability probe is capped at 1.5 s. |
| `AI_K_SAMPLES` | `3` | K independent criterion samples, clamped to 1..10. |
| `AI_RERUN_MAX_PER_SUBMISSION` | `3` | Manual rerun cap. |
| `GRADING_ALLOW_REMOTE_LLM` | `false` | Must be explicitly true for non-loopback URLs; emits a privacy warning. Transcript data then leaves the AiViva host. |
| `INCLUDE_SIGNALS_IN_GRADING` | `false` | `true` is rejected; fairness/STT signals are display-only. |
| `AI_GRADING_WORKER_DELAY_MS` | `3000` | Worker poll delay. |
| `AI_GRADING_SCHEDULER_THREADS` | `2` | Spring scheduled pool size; the grading worker claims one run per poll. |
| `GRADING_RECONCILE_DELAY_MS` | `30000` | Completed-attempt scan interval. |
| `GRADING_RECONCILE_INITIAL_DELAY_MS` | `10000` | Initial scan delay. |
| `grading.rounding-step` | `0.25` | Java `BigDecimal` final-score rounding step. |

There is no grading cloud provider/key configured in the default properties. Use a loopback inference server with an OpenAI-compatible chat-completions API; the STT/TTS local inference service does not itself provide this grading chat endpoint.
