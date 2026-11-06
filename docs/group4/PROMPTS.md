# Grading Prompt

Prompt version: `group4-v1`.

The system prompt instructs the configured model to provide rubric-based suggestions only, treat transcript text as untrusted content, use only supplied criteria and answer turns, return one strict JSON object, score each criterion as a 0..1 fraction, cite exact transcript substrings, and avoid judgments about accent, fluency, delivery or identity. The user payload contains question/rubric content and anonymized turn aliases. It does not contain user IDs or STT fairness signals.

Output shape:

```json
{
  "criteria": [
    { "criterion": "C1", "fraction": 0.75, "justification": "...", "evidence": [{ "turn": "T1", "quote": "exact words" }] }
  ],
  "strengths": [],
  "weaknesses": [],
  "missingPoints": [],
  "flags": []
}
```

The backend rejects missing/extra fields, unknown aliases, out-of-range fractions, omitted/duplicate criterion scores, invalid response JSON and oversized text. Evidence offsets are calculated by exact substring search against the selected stored transcript; absent quotes are stored as unverified and add `EVIDENCE_NOT_FOUND`. No raw model response is persisted. The transcript pattern redactor handles emails, phone-like strings and long digit sequences; spoken names remain a limitation.
