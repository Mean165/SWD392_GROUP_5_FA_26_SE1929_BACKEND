from __future__ import annotations

import asyncio
import base64
import hmac
import io
import json
import logging
import time
import uuid
from collections import deque
from contextlib import asynccontextmanager
from typing import Any

import numpy as np
import torch
from fastapi import Depends, FastAPI, File, Form, Header, HTTPException, Request, UploadFile, WebSocket, WebSocketDisconnect
from fastapi.responses import JSONResponse, Response, StreamingResponse
from pydantic import BaseModel, Field

from .engine import InferenceOverloaded, SpeechEngine
from .settings import Settings
from .text_normalizer import SpeechTextNormalizer


SAMPLE_RATE = 16_000
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(message)s")
LOGGER = logging.getLogger("local.inference")
settings = Settings.from_env()
engine = SpeechEngine(settings)


class RequestLoggerAdapter(logging.LoggerAdapter):
    def process(self, msg, kwargs):
        return msg, kwargs


@asynccontextmanager
async def lifespan(app: FastAPI):
    loader = asyncio.create_task(asyncio.to_thread(engine.load_all))
    yield
    try:
        await asyncio.wait_for(loader, timeout=5)
    except (asyncio.TimeoutError, Exception):
        pass


app = FastAPI(
    title="AiViva Local Inference",
    version="1.0.0",
    description="Loopback-only local STT/TTS. Audio is not forwarded to cloud speech providers.",
    docs_url=None,
    redoc_url=None,
    lifespan=lifespan,
)
normalizer = SpeechTextNormalizer(settings.tts_lexicon_path)


@app.middleware("http")
async def request_id_middleware(request: Request, call_next):
    request_id = request.headers.get("X-Request-ID") or str(uuid.uuid4())
    request.state.request_id = request_id
    response = await call_next(request)
    response.headers["X-Request-ID"] = request_id
    return response


def require_token(x_internal_token: str | None = Header(default=None, alias="X-Internal-Token")) -> str:
    expected = settings.internal_token
    if not expected or not x_internal_token or not hmac.compare_digest(x_internal_token, expected):
        raise HTTPException(status_code=401, detail={"code": "LOCAL_AUTH_FAILED", "message": "Internal authentication failed."})
    return x_internal_token


def require_json_token(request: Request) -> str:
    supplied = request.headers.get("X-Internal-Token")
    expected = settings.internal_token
    if not expected or not supplied or not hmac.compare_digest(supplied, expected):
        raise HTTPException(status_code=401, detail={"code": "LOCAL_AUTH_FAILED", "message": "Internal authentication failed."})
    return supplied


@app.get("/health")
def health(_: str = Depends(require_token)) -> dict[str, Any]:
    overall_ready = engine.stt_status.ready and engine.tts_status.ready
    return {
        "status": "ready" if overall_ready else "not_ready",
        "stt": engine.stt_status.as_dict(),
        "tts": engine.tts_status.as_dict(),
        "resources": engine.resource_snapshot(),
        "versions": installed_versions(),
    }


@app.get("/v1/models")
def models(_: str = Depends(require_token)) -> dict[str, Any]:
    installed = []
    for candidate in sorted((settings.models_dir / "stt").glob("*")) if (settings.models_dir / "stt").exists() else []:
        if candidate.is_dir() and (candidate / "config.json").is_file():
            installed.append({"name": candidate.name, "path": str(candidate), "loaded": candidate == settings.stt_model})
    voices = [str(path) for path in (settings.tts_voice_vi, settings.tts_voice_en) if path.is_file()]
    return {"stt": installed, "tts_voices": voices}


@app.post("/v1/stt/transcribe")
async def transcribe(
    audio: UploadFile = File(...),
    language: str = Form(...),
    hotwords: str = Form(default="[]"),
    initial_prompt: str = Form(default=""),
    mode: str = Form(default="final"),
    _: str = Depends(require_token),
) -> dict[str, Any]:
    validate_language(language)
    if mode != "final":
        raise HTTPException(status_code=400, detail={"code": "INVALID_MODE", "message": "mode must be final."})
    data = await audio.read()
    max_bytes = settings.stt_max_audio_s * SAMPLE_RATE * 2
    if not data or len(data) > max_bytes:
        raise HTTPException(status_code=413, detail={"code": "AUDIO_TOO_LARGE", "message": "Audio is empty or exceeds the configured limit."})
    try:
        terms = json.loads(hotwords)
        if not isinstance(terms, list) or not all(isinstance(term, str) for term in terms):
            raise ValueError("hotwords must be a list of strings")
        from faster_whisper.audio import decode_audio

        decoded = decode_audio(io.BytesIO(data), sampling_rate=SAMPLE_RATE)
    except Exception as exc:
        raise HTTPException(status_code=400, detail={"code": "INVALID_AUDIO_FORMAT", "message": "Audio format could not be decoded."}) from exc
    try:
        result = await asyncio.to_thread(engine.transcribe, decoded, language, terms,
                                         initial_prompt, True)
    except InferenceOverloaded:
        raise HTTPException(status_code=429, headers={"Retry-After": "1"},
                             detail={"code": "LOCAL_INFERENCE_OVERLOADED", "message": "Local STT queue is full."})
    except RuntimeError as exc:
        code = str(exc) if str(exc) in {"stt_not_ready"} else "LOCAL_STT_FAILED"
        raise HTTPException(status_code=503, headers={"Retry-After": "2"},
                             detail={"code": code, "message": "Local STT is not ready."}) from exc
    return {
        **result.as_dict(),
        "text_raw": result.text,
        "text_normalized": " ".join(result.text.split()),
        "request_id": str(uuid.uuid4()),
    }


class SynthesizeRequest(BaseModel):
    text: str = Field(min_length=1, max_length=settings.tts_max_text_length)
    language: str
    voice: str
    speed: float = Field(default=1.0, ge=0.25, le=4.0)
    format: str = "wav"
    normalize: bool = False


@app.post("/v1/tts/synthesize")
def synthesize(body: SynthesizeRequest, _: str = Depends(require_token)) -> Response:
    validate_language(body.language)
    if body.format not in {"wav", "mp3", "opus", "pcm"}:
        raise HTTPException(status_code=400, detail={"code": "INVALID_FORMAT", "message": "Unsupported local TTS format."})
    try:
        wav_bytes, duration_ms, normalized_text = engine.synthesize(
            body.text, body.language, body.speed, body.normalize)
        audio = convert_format(wav_bytes, body.format)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail={"code": "INVALID_TTS_REQUEST", "message": str(exc)}) from exc
    except RuntimeError as exc:
        raise HTTPException(status_code=503, headers={"Retry-After": "2"},
                             detail={"code": "TTS_NOT_READY", "message": "Local TTS model is not ready."}) from exc
    request_id = str(uuid.uuid4())
    LOGGER.info("tts completed request_id=%s language=%s duration_ms=%s bytes=%s",
                request_id, body.language, duration_ms, len(audio))
    media_type = {"wav": "audio/wav", "mp3": "audio/mpeg", "opus": "audio/ogg", "pcm": "audio/L16"}[body.format]
    return Response(content=audio, media_type=media_type,
                    headers={"X-Duration-Ms": str(duration_ms), "X-Request-ID": request_id,
                             "Cache-Control": "no-store", "X-Normalized-Text-Length": str(len(normalized_text))})


@app.post("/v1/tts/stream")
async def stream_tts(body: SynthesizeRequest, _: str = Depends(require_token)) -> StreamingResponse:
    validate_language(body.language)
    if body.format not in {"pcm_s16le", "wav"}:
        raise HTTPException(status_code=400, detail={"code": "INVALID_FORMAT", "message": "Stream format must be pcm_s16le or wav."})

    async def chunks():
        seq = 0
        total_bytes = 0
        started = time.perf_counter()
        try:
            iterator = engine.tts_chunks(body.text, body.language, body.speed, body.normalize)
            sentinel = object()
            while True:
                chunk = await asyncio.to_thread(lambda: next(iterator, sentinel))
                if chunk is sentinel:
                    break
                raw = chunk.audio_int16_bytes
                total_bytes += len(raw)
                event = {
                    "type": "audio",
                    "seq": seq,
                    "audio_base64": base64.b64encode(raw).decode("ascii"),
                    "sample_rate": chunk.sample_rate,
                    "channels": chunk.sample_channels,
                }
                seq += 1
                yield (json.dumps(event, ensure_ascii=False) + "\n").encode("utf-8")
            duration_ms = round(total_bytes * 1000 / (2 * max(1, chunk.sample_rate if seq else 22_050)))
            yield (json.dumps({"type": "done", "duration_ms": duration_ms, "latency_ms": round((time.perf_counter() - started) * 1000)}) + "\n").encode("utf-8")
        except Exception as exc:
            yield (json.dumps({"type": "error", "code": "LOCAL_TTS_FAILED", "message": "Local TTS stream failed."}) + "\n").encode("utf-8")

    return StreamingResponse(chunks(), media_type="application/x-ndjson",
                             headers={"X-Audio-Encoding": "pcm_s16le", "X-Sample-Rate": "22050",
                                      "X-Channels": "1", "Cache-Control": "no-store"})


@app.websocket("/v1/stt/stream")
async def stt_stream(websocket: WebSocket, language: str = "vi", session_id: str = "", turn_id: str = "") -> None:
    supplied = websocket.headers.get("X-Internal-Token", "")
    if not settings.internal_token or not supplied or not hmac.compare_digest(supplied, settings.internal_token):
        await websocket.close(code=4401, reason="internal_auth_failed")
        return
    await websocket.accept()
    if language not in {"vi", "en"}:
        await send_error(websocket, "UNSUPPORTED_LANGUAGE", "language must be vi or en", False)
        await websocket.close(code=4400)
        return
    if not engine.stt_status.ready:
        await send_error(websocket, "STT_NOT_READY", engine.stt_status.reason, True, 2)
        await websocket.close(code=1013)
        return

    request_id = str(uuid.uuid4())
    vad_iterator = engine.new_vad_iterator()
    await websocket.send_json({"type": "ready", "request_id": request_id,
                               "sample_rate": SAMPLE_RATE, "encoding": "pcm_s16le"})
    hotwords: list[str] = []
    initial_prompt = ""
    stt_mode = "streaming_only"
    configured = False
    audio_samples = 0
    processed_samples = 0
    seq = 0
    partial_revisions = 0
    partial_task: asyncio.Task | None = None
    pending_pcm = np.empty(0, dtype=np.int16)
    pre_roll: deque[np.ndarray] = deque(maxlen=10)
    active_chunks: list[np.ndarray] = []
    active = False
    active_start_sample = 0
    last_partial_at = 0
    previous_candidate: list[str] = []
    stable_candidate: list[str] = []
    stable_rounds = 0
    committed: list[str] = []
    send_lock = asyncio.Lock()
    started_at = time.perf_counter()

    async def safe_send(event: dict[str, Any]) -> None:
        async with send_lock:
            await websocket.send_json(event)

    async def transcribe_partial(snapshot: np.ndarray, generation: int) -> tuple[int, Any]:
        try:
            result = await asyncio.to_thread(engine.transcribe, snapshot, language, hotwords, initial_prompt, False)
            return generation, result
        except InferenceOverloaded:
            raise

    async def finish_segment(end_sample: int, reason: str) -> None:
        nonlocal active, active_chunks, previous_candidate, stable_candidate, stable_rounds, partial_task, seq, last_partial_at
        if not active_chunks:
            active = False
            return
        if partial_task is not None:
            try:
                generation, partial_result = await asyncio.wait_for(partial_task, timeout=settings.request_timeout_s)
                if stt_mode != "final_only":
                    await apply_partial(partial_result, generation)
            except Exception:
                pass
            partial_task = None
        audio = np.concatenate(active_chunks).astype(np.float32, copy=False)
        try:
            result = await asyncio.wait_for(
                asyncio.to_thread(engine.transcribe, audio, language, hotwords, initial_prompt, True),
                timeout=settings.request_timeout_s,
            )
        except InferenceOverloaded:
            await send_error(websocket, "LOCAL_INFERENCE_OVERLOADED", "Local STT queue is full.", True, 1)
            active = False
            active_chunks = []
            return
        except Exception as exc:
            await send_error(websocket, "LOCAL_STT_FAILED", "Local STT final decode failed.", True, 2)
            LOGGER.warning("stt final decode failed request_id=%s exception_class=%s", request_id, type(exc).__name__)
            active = False
            active_chunks = []
            return
        if result.text:
            seq += 1
            start_ms = round(active_start_sample * 1000 / SAMPLE_RATE)
            end_ms = max(start_ms, round(end_sample * 1000 / SAMPLE_RATE))
            committed.append(result.text)
            await safe_send({
                "type": "committed", "text": result.text, "words": result.words,
                "start_ms": start_ms, "end_ms": end_ms,
                "avg_confidence": result.avg_confidence, "seq": seq,
                "queue_wait_ms": result.queue_wait_ms, "latency_ms": result.latency_ms,
                "model": result.model, "warnings": result.warnings,
                "needs_human_review": result.avg_confidence < settings.stt_confidence_threshold,
                "request_id": request_id,
            })
        else:
            await send_error(websocket, "TRANSCRIPT_UNRELIABLE",
                             "No reliable transcript was produced. Ask the student to repeat the answer.", True, 0)
            LOGGER.info("stt segment empty request_id=%s warning_count=%s raw_chars=%s",
                        request_id, len(result.warnings), len(result.text_raw))
        if "hallucination_phrase_filtered" in result.warnings:
            await send_error(websocket, "TRANSCRIPT_UNRELIABLE", "Speech segment was filtered as a likely recognition hallucination.", True, 0)
        elif result.warnings:
            LOGGER.info("stt segment filtered request_id=%s warning_count=%s", request_id, len(result.warnings))
        active = False
        active_chunks = []
        previous_candidate = []
        stable_candidate = []
        stable_rounds = 0
        last_partial_at = audio_samples
        pre_roll.clear()
        vad_iterator.reset_states()

    async def process_vad_frame(frame_i16: np.ndarray, actual_samples: int) -> None:
        nonlocal active, active_chunks, active_start_sample, last_partial_at, processed_samples
        float_frame = frame_i16.astype(np.float32) / 32768.0
        event = vad_iterator(torch.from_numpy(float_frame))
        frame_end_sample = processed_samples + actual_samples
        if event and "start" in event:
            active = True
            preroll_samples = sum(len(frame) for frame in pre_roll)
            active_start_sample = max(0, processed_samples - preroll_samples)
            active_chunks = [frame.astype(np.float32) / 32768.0 for frame in pre_roll]
            last_partial_at = processed_samples
        pre_roll.append(frame_i16[:actual_samples].copy())
        if active:
            active_chunks.append(float_frame[:actual_samples])
            segment_duration = sum(len(part) for part in active_chunks) / SAMPLE_RATE
            if event and "end" in event:
                await finish_segment(frame_end_sample, "vad_silence")
            elif segment_duration >= settings.stt_max_segment_s:
                await safe_send({"type": "warning", "code": "SEGMENT_LIMIT_PENDING", "recoverable": True,
                                 "request_id": request_id})
        processed_samples += actual_samples

    async def apply_partial(result, generation: int) -> None:
        nonlocal previous_candidate, stable_candidate, stable_rounds, seq, partial_revisions
        candidate = result.text.split()
        if not candidate:
            return
        common: list[str] = []
        for left, right in zip(previous_candidate, candidate):
            if left.casefold() != right.casefold():
                break
            common.append(left)
        if common == stable_candidate and common:
            stable_rounds += 1
        else:
            stable_candidate = common
            stable_rounds = 1 if common else 0
        if previous_candidate and candidate != previous_candidate:
            partial_revisions += 1
        previous_candidate = candidate
        is_stable = stable_rounds >= settings.stt_partial_agreement_rounds
        seq += 1
        await safe_send({"type": "partial", "text": result.text,
                         "stable_prefix": " ".join(stable_candidate) if is_stable else "",
                         "stable": is_stable, "words": result.words,
                         "seq": seq, "request_id": request_id, "revision": generation})

    try:
        while True:
            message = await websocket.receive()
            if message.get("type") == "websocket.disconnect":
                break
            data = message.get("bytes")
            text = message.get("text")
            if text is not None:
                try:
                    control = json.loads(text)
                except json.JSONDecodeError:
                    await send_error(websocket, "INVALID_MESSAGE", "Control messages must be JSON.", False)
                    break
                if not configured:
                    if control.get("type") != "config":
                        await send_error(websocket, "INVALID_MESSAGE", "Send config before audio.", False)
                        break
                    hotwords = [str(item) for item in control.get("hotwords", []) if str(item).strip()]
                    initial_prompt = str(control.get("initial_prompt", ""))
                    stt_mode = control.get("mode", "streaming_only")
                    if stt_mode not in {"streaming_only", "final_only", "dual"}:
                        await send_error(websocket, "INVALID_MODE", "Unsupported local STT mode.", False)
                        break
                    configured = True
                    continue
                control_type = control.get("type")
                if control_type == "commit":
                    await finish_segment(audio_samples, "manual_commit")
                    continue
                if control_type == "end":
                    if pending_pcm.size:
                        padded = np.pad(pending_pcm, (0, 512 - pending_pcm.size))
                        await process_vad_frame(padded, len(pending_pcm))
                        pending_pcm = np.empty(0, dtype=np.int16)
                    if active_chunks:
                        await finish_segment(processed_samples, "end")
                    await safe_send({"type": "closed", "request_id": request_id})
                    await websocket.close(code=1000)
                    LOGGER.info("stt session closed request_id=%s duration_ms=%s committed=%s partial_revisions=%s",
                                request_id, round((time.perf_counter() - started_at) * 1000), len(committed), partial_revisions)
                    return
                await send_error(websocket, "INVALID_MESSAGE", "Unknown STT control message.", False)
                break

            if data is None:
                continue
            if not configured:
                await send_error(websocket, "INVALID_MESSAGE", "Send config before audio.", False)
                break
            if not data or len(data) % 2:
                await send_error(websocket, "INVALID_AUDIO_FORMAT", "PCM16 frame must contain complete samples.", False)
                break
            samples = np.frombuffer(data, dtype="<i2").copy()
            audio_samples += len(samples)
            if audio_samples > settings.stt_max_audio_s * SAMPLE_RATE:
                await send_error(websocket, "SESSION_TIMEOUT", "Audio duration exceeded the configured limit.", False)
                break
            pending_pcm = np.concatenate((pending_pcm, samples))
            while pending_pcm.size >= 512:
                frame = pending_pcm[:512]
                pending_pcm = pending_pcm[512:]
                await process_vad_frame(frame, 512)
            if partial_task is not None and partial_task.done():
                try:
                    generation, partial_result = partial_task.result()
                    if stt_mode != "final_only":
                        await apply_partial(partial_result, generation)
                except Exception:
                    pass
                partial_task = None
            if active and audio_samples - last_partial_at >= settings.stt_partial_interval_ms * SAMPLE_RATE / 1000:
                if partial_task is None or partial_task.done():
                    if partial_task is not None:
                        try:
                            generation, partial_result = partial_task.result()
                            await apply_partial(partial_result, generation)
                        except Exception:
                            pass
                    snapshot = np.concatenate(active_chunks).astype(np.float32, copy=False)
                    generation = audio_samples
                    partial_task = asyncio.create_task(transcribe_partial(snapshot, generation))
                    last_partial_at = audio_samples
    except WebSocketDisconnect:
        pass
    except Exception as exc:
        await send_error(websocket, "LOCAL_INFERENCE_ERROR", "Local STT session failed.", True, 1)
        LOGGER.warning("stt websocket failed request_id=%s exception_class=%s", request_id, type(exc).__name__)
    finally:
        if partial_task is not None and not partial_task.done():
            partial_task.cancel()

def validate_language(language: str) -> None:
    if language not in {"vi", "en", "vi-VN", "en-US"}:
        raise HTTPException(status_code=400, detail={"code": "UNSUPPORTED_LANGUAGE", "message": "language must be vi or en"})


async def send_error(websocket: WebSocket, code: str, message: str,
                     recoverable: bool, retry_after: int | None = None) -> None:
    payload: dict[str, Any] = {"type": "error", "code": code, "message": message, "recoverable": recoverable}
    if retry_after:
        payload["retry_after"] = retry_after
    await websocket.send_json(payload)


def installed_versions() -> dict[str, str]:
    from importlib.metadata import PackageNotFoundError, version

    names = ("faster-whisper", "ctranslate2", "silero-vad", "piper-tts", "torch", "fastapi", "uvicorn")
    versions: dict[str, str] = {}
    for name in names:
        try:
            versions[name] = version(name)
        except PackageNotFoundError:
            versions[name] = "missing"
    return versions


def convert_format(wav_bytes: bytes, output_format: str) -> bytes:
    if output_format == "wav":
        return wav_bytes
    if output_format == "pcm":
        try:
            with wave.open(io.BytesIO(wav_bytes), "rb") as wav_file:
                if wav_file.getsampwidth() != 2 or wav_file.getnchannels() != 1:
                    raise ValueError("Piper WAV output is not mono PCM16")
                return wav_file.readframes(wav_file.getnframes())
        except wave.Error as exc:
            raise ValueError("Piper returned invalid WAV data") from exc
    codec = "libmp3lame" if output_format == "mp3" else "libopus"
    container = "mp3" if output_format == "mp3" else "ogg"
    command = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-f", "wav", "-i", "pipe:0",
               "-vn", "-c:a", codec, "-f", container, "pipe:1"]
    result = subprocess.run(command, input=wav_bytes, capture_output=True, timeout=30, check=False)
    if result.returncode != 0 or not result.stdout:
        raise RuntimeError("Local audio format conversion failed.")
    return result.stdout
