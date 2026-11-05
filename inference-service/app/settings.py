from __future__ import annotations

import os
from dataclasses import dataclass
from pathlib import Path


def _int(name: str, default: int, minimum: int = 1) -> int:
    try:
        value = int(os.getenv(name, str(default)))
    except ValueError as exc:
        raise ValueError(f"{name} must be an integer") from exc
    if value < minimum:
        raise ValueError(f"{name} must be >= {minimum}")
    return value


def _float(name: str, default: float, minimum: float) -> float:
    try:
        value = float(os.getenv(name, str(default)))
    except ValueError as exc:
        raise ValueError(f"{name} must be a number") from exc
    if value < minimum:
        raise ValueError(f"{name} must be >= {minimum}")
    return value


@dataclass(frozen=True)
class Settings:
    bind_host: str
    port: int
    internal_token: str
    models_dir: Path
    stt_model: Path
    stt_fallback_model: Path | None
    stt_device: str
    stt_compute_type: str
    stt_cpu_threads: int
    stt_partial_interval_ms: int
    stt_commit_silence_ms: int
    stt_max_segment_s: int
    stt_max_audio_s: int
    stt_max_concurrent: int
    stt_prompt_max_tokens: int
    stt_partial_beam_size: int
    stt_final_beam_size: int
    stt_partial_agreement_rounds: int
    stt_queue_wait_ms: int
    stt_confidence_threshold: float
    stt_no_speech_threshold: float
    stt_logprob_threshold: float
    stt_compression_ratio_threshold: float
    stt_repeat_limit: int
    hallucination_phrases: tuple[str, ...]
    tts_engine: str
    tts_voice_vi: Path
    tts_voice_en: Path
    tts_speed: float
    tts_max_text_length: int
    tts_cache_max_entries: int
    tts_cache_dir: Path
    tts_normalize: bool
    tts_lexicon_path: Path | None
    tts_use_cuda: bool
    request_timeout_s: int

    @classmethod
    def from_env(cls) -> "Settings":
        models = Path(os.getenv("MODELS_DIR", "/models")).resolve()
        fallback = os.getenv("LOCAL_STT_FALLBACK_MODEL", "").strip()
        lexicon = os.getenv("LOCAL_TTS_PRONUNCIATION_LEXICON", "").strip()
        bind_host = os.getenv("BIND_HOST", "127.0.0.1")
        if bind_host not in {"127.0.0.1", "::1"} and os.getenv("ALLOW_REMOTE_BIND", "false").lower() != "true":
            raise ValueError("BIND_HOST must be loopback unless ALLOW_REMOTE_BIND=true is explicitly set")
        return cls(
            bind_host=bind_host,
            port=_int("PORT", 8000),
            internal_token=os.getenv("LOCAL_INFERENCE_TOKEN", ""),
            models_dir=models,
            stt_model=Path(os.getenv("LOCAL_STT_MODEL", str(models / "stt" / "large-v3-turbo-ct2"))).resolve(),
            stt_fallback_model=Path(fallback).resolve() if fallback else None,
            stt_device=os.getenv("LOCAL_STT_DEVICE", "auto").lower(),
            stt_compute_type=os.getenv("LOCAL_STT_COMPUTE_TYPE", "int8_float16"),
            stt_cpu_threads=_int("LOCAL_STT_CPU_THREADS", 8),
            stt_partial_interval_ms=_int("LOCAL_STT_PARTIAL_INTERVAL_MS", 1000, 100),
            stt_commit_silence_ms=_int("LOCAL_STT_COMMIT_SILENCE_MS", 700, 100),
            stt_max_segment_s=_int("LOCAL_STT_MAX_SEGMENT_S", 28, 1),
            stt_max_audio_s=_int("LOCAL_STT_MAX_AUDIO_S", 180, 1),
            stt_max_concurrent=_int("LOCAL_STT_MAX_CONCURRENT", 1),
            stt_prompt_max_tokens=_int("LOCAL_STT_PROMPT_MAX_TOKENS", 160),
            stt_partial_beam_size=_int("LOCAL_STT_PARTIAL_BEAM_SIZE", 1),
            stt_final_beam_size=_int("LOCAL_STT_FINAL_BEAM_SIZE", 5),
            stt_partial_agreement_rounds=_int("LOCAL_STT_PARTIAL_AGREEMENT_ROUNDS", 2),
            stt_queue_wait_ms=_int("LOCAL_STT_QUEUE_WAIT_MS", 250),
            stt_confidence_threshold=_float("LOCAL_STT_CONFIDENCE_THRESHOLD", 0.35, 0.0),
            stt_no_speech_threshold=_float("LOCAL_STT_NO_SPEECH_THRESHOLD", 0.6, 0.0),
            stt_logprob_threshold=_float("LOCAL_STT_LOGPROB_THRESHOLD", -1.0, -10.0),
            stt_compression_ratio_threshold=_float("LOCAL_STT_COMPRESSION_RATIO_THRESHOLD", 2.4, 1.0),
            stt_repeat_limit=_int("LOCAL_STT_REPEAT_LIMIT", 4, 2),
            hallucination_phrases=tuple(
                phrase.strip().casefold()
                for phrase in os.getenv(
                    "LOCAL_STT_HALLUCINATION_PHRASES",
                    "thanks for watching,please subscribe,hãy subscribe,đừng quên subscribe,la la school",
                ).split(",")
                if phrase.strip()
            ),
            tts_engine=os.getenv("LOCAL_TTS_ENGINE", "piper").lower(),
            tts_voice_vi=_voice_path(models, os.getenv("LOCAL_TTS_VOICE_VI", "vi_VN-vais1000-medium")),
            tts_voice_en=_voice_path(models, os.getenv("LOCAL_TTS_VOICE_EN", "en_US-lessac-medium")),
            tts_speed=_float("LOCAL_TTS_SPEED", 1.0, 0.25),
            tts_max_text_length=_int("LOCAL_TTS_MAX_TEXT_LENGTH", 4000),
            tts_cache_max_entries=_int("LOCAL_TTS_CACHE_MAX_ENTRIES", 256),
            tts_cache_dir=Path(os.getenv("LOCAL_TTS_CACHE_DIR", str(models / "tts-cache"))).resolve(),
            tts_normalize=os.getenv("LOCAL_TTS_NORMALIZE", "true").lower() == "true",
            tts_lexicon_path=Path(lexicon).resolve() if lexicon else None,
            tts_use_cuda=os.getenv("LOCAL_TTS_USE_CUDA", "false").lower() == "true",
            request_timeout_s=_int("LOCAL_REQUEST_TIMEOUT_S", 180),
        )


def _voice_path(models: Path, value: str) -> Path:
    path = Path(value)
    if path.is_absolute():
        return path
    if not path.suffix:
        path = Path(f"{value}.onnx")
    return models / "tts" / path
