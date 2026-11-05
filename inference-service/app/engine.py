from __future__ import annotations

import hashlib
import importlib.metadata
import io
import json
import logging
import math
import os
import re
import subprocess
import threading
import time
import wave
from collections import OrderedDict
from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

import numpy as np

from .settings import Settings
from .text_normalizer import SpeechTextNormalizer


LOGGER = logging.getLogger("local.inference")
SAMPLE_RATE = 16_000
HALLUCINATION_PHRASES = {
    "thanks for watching",
    "please subscribe",
    "hay subscribe",
    "hãy subscribe",
    "đừng quên subscribe",
    "nhấn subscribe",
    "la la school",
    "để không bỏ lỡ những video hấp dẫn",
    "cảm ơn các bạn đã theo dõi",
    "đăng ký kênh",
}


@dataclass
class EngineStatus:
    ready: bool = False
    engine: str = ""
    model: str = ""
    device: str = "unknown"
    compute_type: str = ""
    loaded_at: str | None = None
    load_ms: int | None = None
    reason: str = "model_loading"

    def as_dict(self) -> dict[str, Any]:
        return {
            "ready": self.ready,
            "engine": self.engine,
            "model": self.model,
            "device": self.device,
            "compute_type": self.compute_type,
            "loaded_at": self.loaded_at,
            "load_ms": self.load_ms,
            "reason": self.reason,
        }


@dataclass
class Transcript:
    text: str
    text_raw: str
    text_normalized: str
    language: str
    duration_ms: int
    words: list[dict[str, Any]]
    segments: list[dict[str, Any]]
    avg_confidence: float
    model: str
    latency_ms: int
    queue_wait_ms: int
    warnings: list[str]

    def as_dict(self) -> dict[str, Any]:
        return {
            "text": self.text,
            "text_raw": self.text_raw,
            "text_normalized": self.text_normalized,
            "language": self.language,
            "duration_ms": self.duration_ms,
            "words": self.words,
            "segments": self.segments,
            "avg_confidence": self.avg_confidence,
            "model": self.model,
            "latency_ms": self.latency_ms,
            "queue_wait_ms": self.queue_wait_ms,
            "warnings": self.warnings,
        }


class SpeechEngine:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.stt_status = EngineStatus(engine="faster-whisper", model=settings.stt_model.name)
        self.tts_status = EngineStatus(engine=settings.tts_engine, model="")
        self.whisper = None
        self.vad_model = None
        self.tts_voices: dict[str, Any] = {}
        self.normalizer = SpeechTextNormalizer(settings.tts_lexicon_path)
        self._model_lock = threading.RLock()
        self._stt_slots = threading.BoundedSemaphore(settings.stt_max_concurrent)
        self._tts_cache: OrderedDict[str, bytes] = OrderedDict()
        self._tts_cache_lock = threading.RLock()
        self._load_started_at = time.perf_counter()

    def load_all(self) -> None:
        self._load_stt()
        self._load_tts()

    def new_vad_iterator(self):
        if not self.stt_status.ready:
            raise RuntimeError("stt_not_ready")
        from silero_vad import VADIterator

        return VADIterator(
            self.vad_model,
            threshold=0.5,
            sampling_rate=SAMPLE_RATE,
            min_silence_duration_ms=self.settings.stt_commit_silence_ms,
            speech_pad_ms=200,
        )

    def _load_stt(self) -> None:
        started = time.perf_counter()
        model_path = self.settings.stt_model
        if not model_path.is_dir():
            self._set_stt_failure("model_files_missing", model_path.name, started)
            return
        try:
            from faster_whisper import WhisperModel
            from silero_vad import load_silero_vad
            import torch

            torch.set_num_threads(1)
            requested_device = self.settings.stt_device
            if requested_device == "auto":
                try:
                    import ctranslate2

                    requested_device = "cuda" if ctranslate2.get_cuda_device_count() > 0 else "cpu"
                except Exception:
                    requested_device = "cpu"
            requested_compute = self.settings.stt_compute_type
            attempts: list[tuple[Path, str, str]] = [(model_path, requested_device, requested_compute)]
            if requested_device == "cuda" and requested_compute != "int8":
                attempts.append((model_path, "cuda", "int8"))
            if self.settings.stt_fallback_model and self.settings.stt_fallback_model.is_dir():
                attempts.append((self.settings.stt_fallback_model, "cpu", "int8"))

            failure_type = "model_load_failed"
            loaded_model = None
            for candidate_path, candidate_device, candidate_compute in attempts:
                try:
                    loaded_model = WhisperModel(
                        str(candidate_path),
                        device=candidate_device,
                        compute_type=candidate_compute,
                        cpu_threads=self.settings.stt_cpu_threads,
                        num_workers=1,
                        local_files_only=True,
                    )
                    model_name = candidate_path.name
                    device = candidate_device
                    compute_type = candidate_compute
                    break
                except Exception as exc:
                    message = str(exc).casefold()
                    if "vocabulary" in message or "model.bin" in message or "config.json" in message:
                        failure_type = "model_files_incomplete"
                    elif "out of memory" in message:
                        failure_type = "insufficient_memory"
                    elif "cuda" in message or "device" in message:
                        failure_type = "device_unavailable"
                    else:
                        failure_type = f"model_load_failed:{type(exc).__name__}"
                    LOGGER.warning("STT load attempt failed: device=%s compute=%s exception_class=%s",
                                   candidate_device, candidate_compute, type(exc).__name__)
            if loaded_model is None:
                self._set_stt_failure("model_load_failed", failure_type, started)
                return

            self.whisper = loaded_model
            self.vad_model = load_silero_vad()
            silence = np.zeros(SAMPLE_RATE, dtype=np.float32)
            segments, _ = self.whisper.transcribe(
                silence,
                language="vi",
                task="transcribe",
                beam_size=1,
                temperature=0.0,
                condition_on_previous_text=False,
                vad_filter=True,
                word_timestamps=False,
            )
            list(segments)
            self.stt_status = EngineStatus(
                ready=True,
                engine="faster-whisper/CTranslate2 + Silero VAD",
                model=model_name,
                device=device,
                compute_type=compute_type,
                loaded_at=datetime.now(timezone.utc).isoformat(),
                load_ms=round((time.perf_counter() - started) * 1000),
                reason="",
            )
            LOGGER.info("STT model ready: engine=%s model=%s device=%s compute_type=%s load_ms=%s",
                        self.stt_status.engine, model_name, device, compute_type, self.stt_status.load_ms)
        except Exception as exc:
            self._set_stt_failure("engine_load_failed", type(exc).__name__, started)

    def _set_stt_failure(self, reason: str, model: str, started: float) -> None:
        self.stt_status = EngineStatus(
            ready=False,
            engine="faster-whisper/CTranslate2 + Silero VAD",
            model=model,
            device=self.settings.stt_device,
            compute_type=self.settings.stt_compute_type,
            load_ms=round((time.perf_counter() - started) * 1000),
            reason=reason,
        )
        LOGGER.warning("STT model not ready: reason=%s model=%s", reason, model)

    def _load_tts(self) -> None:
        started = time.perf_counter()
        if self.settings.tts_engine != "piper":
            self.tts_status = EngineStatus(ready=False, engine=self.settings.tts_engine, reason="unsupported_tts_engine")
            return
        missing = [path.name for path in (self.settings.tts_voice_vi, self.settings.tts_voice_en) if not path.is_file()]
        if missing:
            self.tts_status = EngineStatus(ready=False, engine="piper", reason="voice_models_missing:" + ",".join(missing))
            LOGGER.warning("TTS voices not ready: missing_count=%s", len(missing))
            return
        try:
            from piper import PiperVoice
            from piper.config import SynthesisConfig

            self.tts_voices = {
                "vi": PiperVoice.load(str(self.settings.tts_voice_vi), use_cuda=self.settings.tts_use_cuda),
                "en": PiperVoice.load(str(self.settings.tts_voice_en), use_cuda=self.settings.tts_use_cuda),
            }
            self.settings.tts_cache_dir.mkdir(parents=True, exist_ok=True)
            warmup = SynthesisConfig(length_scale=1.0, normalize_audio=False)
            list(self.tts_voices["vi"].synthesize("Xin chào.", syn_config=warmup))
            list(self.tts_voices["en"].synthesize("Hello.", syn_config=warmup))
            self.tts_status = EngineStatus(
                ready=True,
                engine="piper",
                model=f"{self.settings.tts_voice_vi.name}+{self.settings.tts_voice_en.name}",
                device="cuda" if self.settings.tts_use_cuda else "cpu",
                compute_type="onnxruntime-gpu" if self.settings.tts_use_cuda else "onnxruntime-cpu",
                loaded_at=datetime.now(timezone.utc).isoformat(),
                load_ms=round((time.perf_counter() - started) * 1000),
                reason="",
            )
            LOGGER.info("TTS voices ready: engine=piper load_ms=%s", self.tts_status.load_ms)
        except Exception as exc:
            self.tts_status = EngineStatus(ready=False, engine="piper", reason="voice_load_failed:" + type(exc).__name__)
            LOGGER.warning("TTS not ready: exception_class=%s", type(exc).__name__)

    def resource_snapshot(self) -> dict[str, int | float | None]:
        import psutil

        memory = psutil.virtual_memory()
        gpu_used = gpu_total = None
        try:
            output = subprocess.check_output(
                ["nvidia-smi", "--query-gpu=memory.used,memory.total", "--format=csv,noheader,nounits"],
                timeout=1,
                text=True,
                stderr=subprocess.DEVNULL,
            ).splitlines()[0]
            used, total = (int(part.strip()) for part in output.split(",", maxsplit=1))
            gpu_used, gpu_total = used, total
        except Exception:
            pass
        return {
            "gpu_mem_used_mb": gpu_used,
            "gpu_mem_total_mb": gpu_total,
            "cpu_percent": psutil.cpu_percent(interval=None),
            "ram_used_mb": round(memory.used / (1024 * 1024)),
            "ram_total_mb": round(memory.total / (1024 * 1024)),
        }

    def transcribe(self, audio: np.ndarray, language: str, hotwords: list[str] | None = None,
                   initial_prompt: str = "", final: bool = True) -> Transcript:
        if not self.stt_status.ready or self.whisper is None:
            raise RuntimeError("stt_not_ready")
        queued_at = time.perf_counter()
        acquired = self._stt_slots.acquire(timeout=self.settings.stt_queue_wait_ms / 1000)
        queue_wait_ms = round((time.perf_counter() - queued_at) * 1000)
        if not acquired:
            raise InferenceOverloaded("stt_queue_full")
        started = time.perf_counter()
        try:
            prompt = self._limit_prompt(initial_prompt, language)
            with self._model_lock:
                segments, info = self.whisper.transcribe(
                    audio.astype(np.float32, copy=False),
                    language=language,
                    task="transcribe",
                    beam_size=self.settings.stt_final_beam_size if final else self.settings.stt_partial_beam_size,
                    best_of=1,
                    temperature=0.0,
                    condition_on_previous_text=False,
                    initial_prompt=prompt or None,
                    hotwords=self._hotwords(hotwords),
                    word_timestamps=final,
                    vad_filter=True,
                    vad_parameters={
                        "threshold": 0.5,
                        "min_speech_duration_ms": 250,
                        "min_silence_duration_ms": self.settings.stt_commit_silence_ms,
                        "speech_pad_ms": 200,
                    },
                    compression_ratio_threshold=self.settings.stt_compression_ratio_threshold,
                    log_prob_threshold=self.settings.stt_logprob_threshold,
                    no_speech_threshold=self.settings.stt_no_speech_threshold,
                )
                decoded = list(segments)
            latency_ms = round((time.perf_counter() - started) * 1000)
        finally:
            self._stt_slots.release()

        warnings: list[str] = []
        kept = []
        raw_segments = []
        for segment in decoded:
            text = segment.text.strip()
            if not text:
                continue
            raw_segments.append(segment)
            if segment.no_speech_prob >= self.settings.stt_no_speech_threshold:
                warnings.append("high_no_speech_probability")
                continue
            if segment.avg_logprob < self.settings.stt_logprob_threshold:
                warnings.append("low_average_log_probability")
                continue
            if segment.compression_ratio > self.settings.stt_compression_ratio_threshold:
                warnings.append("high_compression_ratio")
                continue
            if self._is_hallucination(text):
                warnings.append("hallucination_phrase_filtered")
                continue
            kept.append(segment)

        text_raw = " ".join(segment.text.strip() for segment in raw_segments).strip()
        text = " ".join(segment.text.strip() for segment in kept).strip()
        normalized = " ".join(text.split())
        words: list[dict[str, Any]] = []
        result_segments: list[dict[str, Any]] = []
        logprobs: list[float] = []
        for segment in kept:
            avg_logprob = float(segment.avg_logprob)
            logprobs.append(avg_logprob)
            start_ms = round(segment.start * 1000)
            end_ms = round(segment.end * 1000)
            result_segments.append({
                "start_ms": start_ms,
                "end_ms": end_ms,
                "text": segment.text.strip(),
                "avg_logprob": avg_logprob,
                "no_speech_prob": float(segment.no_speech_prob),
                "compression_ratio": float(segment.compression_ratio),
            })
            for word in (segment.words or []):
                words.append({
                    "text": word.word,
                    "start_ms": round(word.start * 1000),
                    "end_ms": round(word.end * 1000),
                    "probability": float(word.probability),
                })
        confidence = self._confidence(logprobs, kept)
        duration_ms = round(len(audio) * 1000 / SAMPLE_RATE)
        return Transcript(text=text, text_raw=text_raw, text_normalized=normalized,
                          language=language, duration_ms=duration_ms, words=words,
                          segments=result_segments, avg_confidence=confidence,
                          model=self.stt_status.model, latency_ms=latency_ms,
                          queue_wait_ms=queue_wait_ms, warnings=sorted(set(warnings)))

    def synthesize(self, text: str, language: str, speed: float, normalize: bool) -> tuple[bytes, int, str]:
        if not self.tts_status.ready:
            raise RuntimeError("tts_not_ready")
        if len(text) > self.settings.tts_max_text_length:
            raise ValueError("tts_text_too_long")
        if not math.isfinite(speed) or not 0.25 <= speed <= 4.0:
            raise ValueError("tts_speed_out_of_range")
        language = "vi" if language.lower().startswith("vi") else "en"
        normalized = self.normalizer.normalize(text, language, enabled=normalize)
        voice = self.tts_voices[language]
        key = hashlib.sha256("\0".join((normalized, str(voice), str(speed), str(normalize), self.tts_status.model)).encode("utf-8")).hexdigest()
        with self._tts_cache_lock:
            cached = self._tts_cache.get(key)
            if cached is not None:
                self._tts_cache.move_to_end(key)
                return cached, _wav_duration_ms(cached), normalized
            cache_file = self.settings.tts_cache_dir / f"{key}.wav"
            try:
                if cache_file.is_file():
                    cached = cache_file.read_bytes()
                    self._cache_tts(key, cached)
                    return cached, _wav_duration_ms(cached), normalized
            except OSError:
                pass
        from piper.config import SynthesisConfig

        output = io.BytesIO()
        with wave.open(output, "wb") as wav_file:
            voice.synthesize_wav(
                normalized,
                wav_file,
                syn_config=SynthesisConfig(length_scale=1.0 / speed, normalize_audio=False),
            )
        audio = output.getvalue()
        self._cache_tts(key, audio)
        try:
            temporary = self.settings.tts_cache_dir / f"{key}.wav.tmp"
            temporary.write_bytes(audio)
            temporary.replace(self.settings.tts_cache_dir / f"{key}.wav")
        except OSError:
            pass
        return audio, _wav_duration_ms(audio), normalized

    def tts_chunks(self, text: str, language: str, speed: float, normalize: bool):
        if not self.tts_status.ready:
            raise RuntimeError("tts_not_ready")
        if len(text) > self.settings.tts_max_text_length:
            raise ValueError("tts_text_too_long")
        if not math.isfinite(speed) or not 0.25 <= speed <= 4.0:
            raise ValueError("tts_speed_out_of_range")
        language = "vi" if language.lower().startswith("vi") else "en"
        normalized = self.normalizer.normalize(text, language, enabled=normalize)
        from piper.config import SynthesisConfig

        voice = self.tts_voices[language]
        config = SynthesisConfig(length_scale=1.0 / speed, normalize_audio=False)
        for chunk in voice.synthesize(normalized, syn_config=config):
            yield chunk

    def _cache_tts(self, key: str, audio: bytes) -> None:
        with self._tts_cache_lock:
            self._tts_cache[key] = audio
            self._tts_cache.move_to_end(key)
            while len(self._tts_cache) > self.settings.tts_cache_max_entries:
                self._tts_cache.popitem(last=False)

    def _limit_prompt(self, prompt: str, language: str) -> str:
        if not prompt:
            return ""
        try:
            from faster_whisper.tokenizer import Tokenizer

            tokenizer = Tokenizer(self.whisper.hf_tokenizer, self.whisper.model.is_multilingual,
                                  task="transcribe", language=language)
            ids = tokenizer.encode(prompt)
            if len(ids) <= self.settings.stt_prompt_max_tokens:
                return prompt
            return tokenizer.decode(ids[:self.settings.stt_prompt_max_tokens])
        except Exception:
            # Fall back to a bounded word list if tokenizer introspection fails.
            return " ".join(prompt.split()[:self.settings.stt_prompt_max_tokens])

    def _hotwords(self, hotwords: list[str] | None) -> str | None:
        if not hotwords:
            return None
        return " ".join(str(term).strip() for term in hotwords if str(term).strip())[:2000] or None

    def _is_hallucination(self, text: str) -> bool:
        normalized = re.sub(r"[^\w\s]", " ", text.casefold(), flags=re.UNICODE)
        normalized = " ".join(normalized.split())
        if any(phrase in normalized for phrase in (*HALLUCINATION_PHRASES, *self.settings.hallucination_phrases)):
            return True
        words = normalized.split()
        return len(words) >= self.settings.stt_repeat_limit and len(set(words)) <= 2

    def _confidence(self, logprobs: list[float], kept: list[Any]) -> float:
        if not logprobs:
            return 0.0
        average_logprob = sum(logprobs) / len(logprobs)
        no_speech = sum(float(segment.no_speech_prob) for segment in kept) / len(kept)
        value = math.exp(min(0.0, average_logprob)) * (1.0 - no_speech)
        return max(0.0, min(1.0, value))


class InferenceOverloaded(RuntimeError):
    pass


def _wav_duration_ms(audio: bytes) -> int:
    try:
        with wave.open(io.BytesIO(audio), "rb") as wav_file:
            return round(wav_file.getnframes() * 1000 / wav_file.getframerate())
    except (wave.Error, ZeroDivisionError):
        return 0
