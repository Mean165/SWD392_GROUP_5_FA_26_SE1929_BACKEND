# Frontend Implementation Notes

For the React `/student/device-check` flow, use `GET /api/student/device-check/stt/health`; the ready flag is nested at `health.stt.ready`. The check is optional and the existing skip-to-exam behavior must remain. See `API_CONTRACT.md` for the actual health shape and `WS_PROTOCOL.md` for the actual authenticated WebSocket protocol.

The STT server is private/local behind Spring. The browser connects only to same-origin `/ws/stt`, sends a student JWT using `access_token` query authentication, then a `start` JSON message and binary 16 kHz mono PCM16LE frames. Do not put the local internal token or inference URL in React configuration.

For privacy copy, say the browser sends microphone audio to the AiViva server for local speech recognition. DEVICE_CHECK audio and transcript are transient and are not persisted by backend; do not promise this for EXAM, whose configured audio/transcript retention follows the interview policy. The STT component only converts speech into text. It does not assess correctness or give a grade.

The speaker step uses the authenticated local Piper WAV endpoint. Do not use browser speech recognition, retain captured microphone audio in browser storage, or ignore typed server error codes. Keep vi/en translations in the existing i18n layer.
