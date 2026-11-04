# Local Speech Model and Runtime Licenses

Survey date: 2026-11-04. This records upstream metadata for the selected artifacts; it is not legal advice or a conclusion that a complete product distribution is cleared.

| Component | Candidate/version | License/provenance observed | Constraints and action |
|---|---|---|---|
| STT runtime | `faster-whisper 1.2.1`, `ctranslate2 4.8.5` | faster-whisper repository MIT; CTranslate2 dependency license to verify from the exact installed distribution before final packaging | Include required notices. GPU path requires CUDA 12/cuBLAS and cuDNN 9. |
| STT weights | Original Whisper large-v3-turbo, converted locally to CTranslate2 | Upstream model card declares MIT | Use the pinned upstream revision, record revision/hash, retain model/license metadata. Do not substitute an unreviewed third-party converted repository. |
| VAD | `silero-vad 6.2.3` | Upstream repository license must be included from the exact pinned release; repository currently identifies MIT | Include license/notice and pinned version. |
| TTS runtime | `piper-tts 1.8.0` | Current OHF-Voice Piper Python engine is GPLv3 | Bundling/distributing the inference container may require providing corresponding source and GPL notices. Confirm product distribution compatibility. |
| Vietnamese TTS voice | `vi_VN-vais1000-medium` | Piper voice repository metadata is MIT; voice card identifies VAIS-1000 source dataset under CC-BY-4.0 | Preserve attribution to source dataset and model card. Only one trained speaker/medium quality; human listening approval pending. |
| English TTS voice | `en_US-lessac-medium` | Piper voice repository metadata is MIT; model card references Blizzard 2013 voice data | The upstream dataset license/attribution conditions still need review before shipping. |
| TTS alternative not selected | `facebook/mms-tts-vie` | CC-BY-NC-4.0 | Non-commercial restriction makes it unsuitable as default until intended deployment rights are clarified. |
| TTS alternative not selected | Coqui XTTS-v2 | Coqui Public Model License | Restrictive model license; not selected pending rights review. |

Files should retain `LICENSES.md` and model cards with downloaded weights. Model weights, generated audio samples, and benchmark data are excluded from git.
