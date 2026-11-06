# Cloud Speech Provider Removal Inventory

Created before removals on 2026-11-04. Snapshot commit: `a0df337` (`snapshot before removing cloud providers`). This is the only file excluded from the final legacy-provider scan. `.env` is ignored and untracked; provider-only secret lines are to be removed locally without printing or committing their values. Status values below record completion of the planned delete, modify, or retain action.

## Backend / Compose / Dependencies

| Path | Type | Planned action | Status |
|---|---|---|---|
| `pom.xml` | Dependency | Remove Google Cloud Speech SDK; add only local-service dependencies if needed | completed per planned action |
| `docker-compose.yml` | Runtime config | Remove cloud speech/TTS keys, URLs, prices and providers; add local inference service wiring and profiles | completed per planned action |
| `Dockerfile` | Build config | Keep Java backend build; update only if required for local-provider integration | completed per planned action |
| `src/main/resources/application.properties` | Runtime config | Remove cloud provider URLs/credentials/models/credit settings; add local provider and inference settings | completed per planned action |
| `src/main/java/com/swd392/aiviva/common/config/OpenAiConfig.java` | Shared config | Remove speech-only OpenAI TTS/STT settings while retaining settings still consumed by unrelated OpenAI LLM/question generation | completed per planned action |
| `.env` (ignored; not tracked) | Secret/config | Remove `ELEVENLABS_API_KEY`, `ZEROWEIGHT_API_KEY`, cloud STT/TTS-only values; preserve unrelated LLM/mail/database values; never print or commit values | completed per planned action |

## Backend Speech Code

| Path | Type | Planned action | Status |
|---|---|---|---|
| `src/main/java/com/swd392/aiviva/speech/provider/ElevenLabsSpeechToTextProvider.java` | Provider code | Delete | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/provider/ElevenLabsBatchTranscriber.java` | Provider code | Delete | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/provider/GoogleCloudSpeechToTextProvider.java` | Provider code | Delete | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/provider/ZeroWeightTTSProvider.java` | Provider code | Delete | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/provider/ZeroWeightWebSocketClient.java` | Provider code | Delete | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/provider/ZeroWeightStreamingClient.java` | Provider code | Delete | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/provider/ZeroWeightSessionOptions.java` | Provider code | Delete | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/provider/ZeroWeightPcmResult.java` | Provider code | Delete | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/provider/ZeroWeightProviderException.java` | Provider code | Delete | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/provider/ZeroWeightUsageMeter.java` | Provider usage/cost code | Delete; replace with local resource tracking | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/config/SpeechToTextProperties.java` | Provider config | Remove cloud provider nested properties; add local service config | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/config/TextToSpeechProperties.java` | Provider config | Remove ZeroWeight nested properties; add local TTS settings | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/config/STTCostProperties.java` | Cloud price config | Delete; replace with resource tracker config | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/stt/application/SttCostTracker.java` | Cloud price tracker | Delete; replace with resource tracker | completed per planned action |
| `src/main/java/com/swd392/aiviva/interview/ai/stt/OpenAiWhisperSttProviderAdapter.java` | Cloud STT adapter | Delete | completed per planned action |
| `src/main/java/com/swd392/aiviva/interview/ai/tts/OpenAiTtsProviderAdapter.java` | Cloud TTS adapter | Delete | completed per planned action |
| `src/main/java/com/swd392/aiviva/interview/ai/stt/SpeechToTextProviderAdapter.java` | STT abstraction | Keep as a neutral local batch-transcription port or replace with a neutral interface; no provider branding | completed per planned action |
| `src/main/java/com/swd392/aiviva/interview/ai/stt/SpeechToTextProvider.java` | Live STT abstraction | Keep | completed per planned action |
| `src/main/java/com/swd392/aiviva/interview/ai/tts/TTSProvider.java` | TTS abstraction | Keep | completed per planned action |
| `src/main/java/com/swd392/aiviva/interview/service/SpeechToTextServiceImpl.java` | Service wiring | Rewire to local batch provider | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/stt/application/STTTurnSession.java` | STT orchestration | Integrate local streaming/final modes; rename cloud-era mode values | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/stt/domain/STTMode.java` | STT mode enum | Replace old mode values with `streaming_only`, `final_only`, `dual` | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/stt/domain/STTModeProperties.java` | STT mode config | Rename to local modes and resource settings | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/stt/provider/STTBatchTranscriber.java` | Batch STT abstraction | Keep/rename neutrally and implement through local service | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/websocket/SttWebSocketHandler.java` | Live flow | Rewire provider/orchestrator; retain authentication, consent, protocol and no-raw-log rules | completed per planned action |
| `src/main/java/com/swd392/aiviva/speech/websocket/SttHealthController.java` | Health API | Aggregate local inference health for STT and TTS | completed per planned action |
| `src/main/java/com/swd392/aiviva/devicecheck/service/DeviceCheckServiceImpl.java` | Device-check | Use local TTS/STT and local health | completed per planned action |
| `src/main/java/com/swd392/aiviva/interview/service/InterviewServiceImpl.java` | Exam flow/audio | Preserve no-grading role, local STT/TTS, configured audio storage/retention | completed per planned action |

## Backend Tests / Fixtures

| Path | Type | Planned action | Status |
|---|---|---|---|
| `src/test/java/com/swd392/aiviva/speech/provider/ElevenLabsSpeechToTextProviderTest.java` | Vendor fake-WebSocket test | Delete; replace with local provider/server contract tests | completed per planned action |
| `src/test/java/com/swd392/aiviva/speech/provider/ElevenLabsBatchTranscriberTest.java` | Vendor fake-HTTP test | Delete; replace with local batch contract tests | completed per planned action |
| `src/test/java/com/swd392/aiviva/speech/provider/ZeroWeightTTSProviderTest.java` | Vendor fake-server test | Delete; replace with local TTS tests | completed per planned action |
| `src/test/java/com/swd392/aiviva/interview/ai/stt/OpenAiWhisperSttProviderAdapterTest.java` | Cloud STT test | Delete; replace with local batch adapter tests | completed per planned action |
| `src/test/java/com/swd392/aiviva/interview/ai/tts/OpenAiTtsProviderAdapterTest.java` | Cloud TTS test | Delete; replace with local TTS tests | completed per planned action |
| `src/test/java/com/swd392/aiviva/speech/stt/provider/MockSTTProviderTest.java` | Mock provider test | Keep; verify no key dependency and test/dev-only activation | completed per planned action |
| `src/test/java/com/swd392/aiviva/speech/stt/application/KeytermSelectorTest.java` | Keyterm tests | Keep and update budget assertions for local model prompt/hotwords | completed per planned action |
| `src/test/java/com/swd392/aiviva/speech/stt/application/TranscriptReconcilerTest.java` | Transcript tests | Keep and update local result metadata if needed | completed per planned action |
| `src/test/java/com/swd392/aiviva/speech/stt/application/STTTurnSessionTest.java` | Orchestration tests | Keep and test local mode names/finalization | completed per planned action |
| `src/test/java/com/swd392/aiviva/devicecheck/service/DeviceCheckServiceImplTest.java` | Device-check tests | Update provider fixtures/messages for local health and Piper WAV | completed per planned action |
| `src/test/java/com/swd392/aiviva/devicecheck/controller/DeviceCheckControllerSecurityTest.java` | Device-check API tests | Keep auth coverage; update health/provider payload only | completed per planned action |
| `src/test/java/com/swd392/aiviva/interview/service/InterviewServiceImplTest.java` | Interview tests | Keep; update local STT/audio-retention wiring | completed per planned action |
| `src/test/java/com/swd392/aiviva/interview/service/SpeechAndTtsServiceCompatibilityTest.java` | TTS integration tests | Keep and retarget to local TTS | completed per planned action |
| `src/test/java/com/swd392/aiviva/interview/service/AnswerAnalysisServiceImplTest.java` | Unrelated LLM tests | Retain; remove only any speech-provider wording if present | completed per planned action |
| `src/test/java/com/swd392/aiviva/question/service/*` | Unrelated question LLM tests | Retain; do not remove Gemini/OpenAI question services | completed per planned action |
| `src/test/resources/stt/mock_scenarios.json` | Mock fixture | Keep for development/tests; not a production provider | completed per planned action |

## Backend Docs / Scripts / Seed

| Path | Type | Planned action | Status |
|---|---|---|---|
| `docs/vendor/elevenlabs/realtime_stt.md` | Vendor docs | Delete | completed per planned action |
| `docs/vendor/elevenlabs/keyterm_prompting.md` | Vendor docs | Delete | completed per planned action |
| `docs/vendor/elevenlabs/batch_convert.md` | Vendor docs | Delete | completed per planned action |
| `docs/vendor/elevenlabs/` | Vendor docs directory | Remove after its three files are deleted | completed per planned action |
| `docs/zeroweight-tts.md` | Vendor docs | Delete | completed per planned action |
| `docs/stt-streaming.md` | Cloud STT docs | Replace with local API/runbook links | completed per planned action |
| `docs/group3/stt/00_VENDOR_NOTES.md` | Vendor notes | Delete; local facts go in this directory | completed per planned action |
| `docs/group3/stt/01_SURVEY.md` | Cloud-era survey | Replace with local survey link or delete | completed per planned action |
| `docs/group3/stt/02_PLAN.md` | Cloud-era plan | Replace with local plan link or delete | completed per planned action |
| `scripts/tts_smoke_test.py` | ZeroWeight benchmark | Delete; replace with local TTS benchmark | completed per planned action |
| `scripts/requirements-tts-smoke.txt` | Vendor script dependency | Delete or fold only still-needed websocket test dependency into local scripts | completed per planned action |
| `scripts/stt_smoke_test.py` | STT smoke test | Keep/retarget to local `/ws/stt` and benchmark harness | completed per planned action |
| `src/main/resources/data.sql` | Seed data | Keep unless an exact provider-specific row is confirmed; do not delete unrelated seed rows | completed per planned action |
| `src/main/resources/schema.sql` | DB schema | Keep audit schema; add only local metadata/resource fields if needed | completed per planned action |
| `src/main/resources/db/migration/V3__stt_results_and_session_events.sql` | DB migration | Keep as historical migration; add forward migration for local mode values without deleting audit rows | completed per planned action |
| `README.md` | Project docs | Remove cloud speech setup; document local profiles and model setup | completed per planned action |

## Docker / Environment Variables

| Path/variable | Type | Planned action | Status |
|---|---|---|---|
| `.env` `ELEVENLABS_API_KEY` | Ignored secret | Remove line and value locally; never print or commit | completed per planned action |
| `.env` `ZEROWEIGHT_API_KEY` | Ignored secret | Remove line and value locally; never print or commit | completed per planned action |
| `ELEVENLABS_REGION`, `ELEVENLABS_ENABLE_LOGGING`, `ELEVENLABS_CONNECT_TIMEOUT_SECONDS` | Provider config | Remove from Compose/properties/example | completed per planned action |
| `ZEROWEIGHT_API_URL`, `ZEROWEIGHT_VOICE_ID`, `ZEROWEIGHT_SPEED`, `ZEROWEIGHT_NORMALIZE_TEXT`, `ZEROWEIGHT_TIMEOUT_MILLIS`, `ZEROWEIGHT_CREDIT_BUDGET`, `ZEROWEIGHT_CREDIT_WARNING_REMAINING`, `ZEROWEIGHT_MAX_AUDIO_BYTES` | Provider config | Remove from Compose/properties/example | completed per planned action |
| `GOOGLE_CLOUD_PROJECT`, `GOOGLE_CLOUD_SPEECH_LOCATION`, `GOOGLE_CLOUD_SPEECH_MODEL` | Cloud STT config | Remove from Compose/properties/example | completed per planned action |
| `OPENAI_TTS_VOICE_VI`, `OPENAI_TTS_VOICE_EN`, `OPENAI_TTS_MAX_TEXT_LENGTH` and speech-only OpenAI URL/model settings | Cloud speech config | Remove; preserve shared `OPENAI_API_KEY`, LLM URL/model/timeout only where used by non-speech LLM code | completed per planned action |
| `STT_PRICE_PER_HOUR_REALTIME`, `STT_PRICE_PER_HOUR_BATCH`, `STT_PRICE_PER_HOUR_KEYTERMS` | Cloud cost config | Remove; replace with local compute/resource settings | completed per planned action |

## Frontend Repo (separate dirty worktree)

The frontend branch/worktree already had unrelated modifications at survey time. Only listed speech files will be edited; all other existing changes must remain intact.

| Path | Type | Planned action | Status |
|---|---|---|---|
| `package.json` | Dependency | Remove `@elevenlabs/elevenlabs-js`; retain React/Vite and other unrelated deps | completed per planned action |
| `package-lock.json` | Lockfile | Regenerate/remove only ElevenLabs SDK dependency | completed per planned action |
| `src/pages/student/DeviceCheckPage.tsx` | Privacy text | Replace configured-cloud wording with local AiViva inference wording | completed per planned action |
| `src/context/LanguageContext.tsx` | i18n | Update Vietnamese/English privacy strings | completed per planned action |
| `src/pages/student/DeviceCheckPage.test.tsx` | FE test | Update health provider fixture and local privacy assertion | completed per planned action |
| `src/services/deviceCheck/deviceCheckApi.test.ts` | FE test | Rename ZeroWeight fixture/test description to local TTS | completed per planned action |
| `src/types/stt.ts` | FE API type | Update to aggregated local health payload | completed per planned action |
| `src/services/deviceCheck/deviceCheckApi.ts` | FE API | Parse/report local health reason without provider secrets | completed per planned action |

## Unrelated Providers and Retained Abstractions

`OpenAiConfig`, `OpenAiLlmProviderAdapter`, `OpenAiQuestionGenerationServiceImpl`, Gemini question/embedding adapters, `SpeechToTextProvider`, `TTSProvider`, `MockSTTProvider`, and `MockTTSProvider` are explicitly reviewed. Keep the neutral interfaces, mock providers (test/dev only), and unrelated LLM/question-generation providers. Remove only speech-specific cloud adapters/config.
