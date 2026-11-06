# Known Limitations

- No grading model is configured by default. A local OpenAI-compatible server/model must be installed separately; the local STT/TTS inference service is not a grading LLM.
- Prompt-based scoring has not been benchmarked against a gold set. `confidence = 1 - population variance` is an uncalibrated stability proxy, not a probability of correctness.
- PII filtering is regex-based and cannot detect all spoken names or identifiers. Keep the provider local for sensitive exams; remote use requires explicit override and carries transcript egress risk.
- No signed audio retrieval endpoint exists; raw audio URLs are omitted from the grading API. Add it only with authorized short-lived signing and per-view audit.
- No auto/student final-grade API, bulk actions, overall comments, OpenAPI generator config or group-6 export route exists yet.
- Rubric snapshots use current active subject criteria. Existing rubric schema has no per-level descriptors. Legacy official final rows are not converted into Group 4 revisions and are skipped by reconciliation.
- Score scale is fixed at 10; supported per-exam rounding steps are honored. `MANUAL_ONLY` unanswered policy is not exposed; the active policy requires explicit lecturer score cells for every criterion.
- Queue priority is currently neutral (0) and not computed from incidents/review signals. STT flags are displayed and add review flags without changing scores.
- Agreement metrics are criterion-level MAE/exact/within-one only; no QWK/cohort fairness report.
- The Spring initializer runs schema SQL on every startup; test the additive migration against a disposable database before shared deployment.
