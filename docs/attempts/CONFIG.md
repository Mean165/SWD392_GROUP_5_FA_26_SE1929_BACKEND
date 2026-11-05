# Attempts Configuration

These values are stored per exam in `exam_attempt_settings`; no API keys or new environment variables are required.

| Field | Default | Rules |
| --- | --- | --- |
| `maxAttempts` | `1` | Integer >= 1. Effective allowance adds `student_attempt_allowance.extra_attempts`; if already used exceeds a lowered max, effective limit is clamped to used. |
| `scorePolicy` | `LECTURER_CHOOSES` | `LATEST`, `HIGHEST`, `FIRST`, `AVERAGE`, or `LECTURER_CHOOSES`. |
| `avoidRepeatQuestions` | `true` | Excludes same-student earlier counted attempt questions; falls back with `QUESTION_REPEATED_DUE_TO_BANK_SIZE`. |
| `cooldownMinutes` | `0` | Nonnegative wait after last counted completion. |
| `attemptWindowStart` / `attemptWindowEnd` | null | Optional absolute window; end must be after start. Per-attempt slot window also applies. |
| `countOnStartEvent` | `FIRST_QUESTION_ASKED` | Fixed server behavior; device check and route entry do not count. |
| `allowLecturerExtraAttempts` | `true` | Controls grant/revoke. |
| `countAbandoned` | `true` | An abandoned attempt only counts if its first question was already asked. |
| `countExpired` | `false` | Scheduled attempts expiring before start do not count. |

The attempt settings API is protected by LE/AD role and exam creator/assigned-subject checks. Every changed configuration is audited with before/after values and affected-student count. The setting update requires the returned `version` to avoid overwriting concurrent edits.
