from __future__ import annotations

from dataclasses import replace

import numpy as np
from fastapi.testclient import TestClient
from starlette.websockets import WebSocketDisconnect

from app import main
from app.engine import EngineStatus, SpeechEngine, Transcript, _float_audio_to_wav


class SilenceVad:
    def __init__(self):
        self.frame = 0

    def __call__(self, frame):
        self.frame += 1
        if self.frame == 1:
            return {"start": 0}
        if self.frame == 2:
            return {"end": 512}
        return None

    def reset_states(self):
        return None


class ContinuousSpeechVad:
    def __init__(self):
        self.frame = 0

    def __call__(self, frame):
        self.frame += 1
        return {"start": 0} if self.frame == 1 else None

    def reset_states(self):
        self.frame = 0


class NoSpeechStartVad:
    def __call__(self, frame):
        return None

    def reset_states(self):
        return None


class FakeEngine:
    def __init__(self):
        self.stt_status = EngineStatus(ready=False, engine="faster-whisper", model="missing", device="cpu", reason="model_files_missing")
        self.tts_status = EngineStatus(ready=False, engine="piper", model="", device="cpu", reason="voice_models_missing")

    def resource_snapshot(self):
        return {"gpu_mem_used_mb": None, "gpu_mem_total_mb": None, "cpu_percent": 0.0, "ram_used_mb": 128}

    def new_vad_iterator(self):
        return SilenceVad()

    def transcribe(self, audio, language, hotwords=None, initial_prompt="", final=True):
        return Transcript(
            text="xin chao", text_raw="xin chao", text_normalized="xin chao", language=language,
            duration_ms=round(len(audio) * 1000 / 16000),
            words=[{"text": "xin", "start_ms": 0, "end_ms": 120, "probability": 0.9}],
            segments=[{"start_ms": 0, "end_ms": 120, "text": "xin chao", "avg_logprob": -0.1,
                       "no_speech_prob": 0.01, "compression_ratio": 1.0}],
            avg_confidence=0.9, model="fixture", latency_ms=12, queue_wait_ms=1, warnings=[],
        )


def configure(monkeypatch):
    monkeypatch.setattr(main, "settings", replace(main.settings, internal_token="test-local-token"))
    monkeypatch.setattr(main, "engine", FakeEngine())
    return TestClient(main.app)


def test_health_requires_internal_token_and_reports_reason(monkeypatch):
    client = configure(monkeypatch)
    assert client.get("/health").status_code == 401
    response = client.get("/health", headers={"X-Internal-Token": "test-local-token"})
    assert response.status_code == 200
    assert response.json()["stt"]["ready"] is False
    assert response.json()["stt"]["reason"] == "model_files_missing"


def test_batch_transcription_contract_and_auth(monkeypatch):
    client = configure(monkeypatch)
    audio = b"RIFF" + b"\0" * 256
    assert client.post("/v1/stt/transcribe", files={"audio": ("a.wav", audio)}).status_code == 401
    monkeypatch.setattr("faster_whisper.audio.decode_audio", lambda _data, sampling_rate: np.zeros(1600, dtype=np.float32))
    response = client.post(
        "/v1/stt/transcribe",
        headers={"X-Internal-Token": "test-local-token"},
        files={"audio": ("a.wav", audio, "audio/wav")},
        data={"language": "vi", "hotwords": '["Java"]', "initial_prompt": "Java", "mode": "final"},
    )
    assert response.status_code == 200
    assert response.json()["text_raw"] == "xin chao"
    assert response.json()["words"][0]["probability"] == 0.9


def test_websocket_requires_token_and_emits_committed_then_closed(monkeypatch):
    client = configure(monkeypatch)
    try:
        with client.websocket_connect("/v1/stt/stream?language=vi"):
            raise AssertionError("unauthenticated local websocket must be rejected")
    except WebSocketDisconnect as exception:
        assert exception.code == 4401

    monkeypatch.setattr(main.engine, "stt_status", EngineStatus(ready=True, engine="test", model="test", device="cpu", reason=""))
    with client.websocket_connect(
        "/v1/stt/stream?language=vi&session_id=s&turn_id=t",
        headers={"X-Internal-Token": "test-local-token"},
    ) as websocket:
        assert websocket.receive_json()["type"] == "ready"
        websocket.send_json({"type": "config", "hotwords": [], "mode": "streaming_only"})
        websocket.send_bytes(np.zeros(512, dtype="<i2").tobytes())
        websocket.send_json({"type": "end"})
        committed = websocket.receive_json()
        closed = websocket.receive_json()
        assert committed["type"] == "committed"
        assert committed["text"] == "xin chao"
        assert closed["type"] == "closed"


def test_websocket_rejects_invalid_pcm_alignment(monkeypatch):
    client = configure(monkeypatch)
    monkeypatch.setattr(main.engine, "stt_status", EngineStatus(ready=True, engine="test", model="test", device="cpu", reason=""))
    with client.websocket_connect(
        "/v1/stt/stream?language=en",
        headers={"X-Internal-Token": "test-local-token"},
    ) as websocket:
        assert websocket.receive_json()["type"] == "ready"
        websocket.send_json({"type": "config"})
        websocket.send_bytes(b"\0")
        error = websocket.receive_json()
        assert error["type"] == "error"
        assert error["code"] == "INVALID_AUDIO_FORMAT"


def test_short_segment_emits_partial_before_committed(monkeypatch):
    client = configure(monkeypatch)
    monkeypatch.setattr(main, "settings", replace(main.settings, stt_partial_interval_ms=100))
    monkeypatch.setattr(main.engine, "stt_status", EngineStatus(ready=True, engine="test", model="test", device="cpu", reason=""))
    monkeypatch.setattr(main.engine, "new_vad_iterator", lambda: ContinuousSpeechVad())
    with client.websocket_connect(
        "/v1/stt/stream?language=vi",
        headers={"X-Internal-Token": "test-local-token"},
    ) as websocket:
        assert websocket.receive_json()["type"] == "ready"
        websocket.send_json({"type": "config", "mode": "streaming_only"})
        for _ in range(4):
            websocket.send_bytes(np.zeros(512, dtype="<i2").tobytes())
        websocket.send_json({"type": "end"})
        events = [websocket.receive_json(), websocket.receive_json(), websocket.receive_json()]
        assert [event["type"] for event in events] == ["partial", "committed", "closed"]
        assert events[0]["text"] == "xin chao"


def test_empty_filtered_final_reports_recoverable_error(monkeypatch):
    client = configure(monkeypatch)
    monkeypatch.setattr(main.engine, "stt_status", EngineStatus(ready=True, engine="test", model="test", device="cpu", reason=""))
    empty_result = replace(
        FakeEngine().transcribe(np.zeros(512, dtype=np.float32), "vi"),
        text="", text_raw="", text_normalized="", warnings=["high_no_speech_probability"],
    )
    monkeypatch.setattr(main.engine, "transcribe", lambda *args, **kwargs: empty_result)
    with client.websocket_connect(
        "/v1/stt/stream?language=vi",
        headers={"X-Internal-Token": "test-local-token"},
    ) as websocket:
        assert websocket.receive_json()["type"] == "ready"
        websocket.send_json({"type": "config", "mode": "streaming_only"})
        websocket.send_bytes(np.zeros(512, dtype="<i2").tobytes())
        websocket.send_bytes(np.zeros(512, dtype="<i2").tobytes())
        error = websocket.receive_json()
        assert error["type"] == "error"
        assert error["code"] == "TRANSCRIPT_UNRELIABLE"


def test_end_runs_final_decode_when_streaming_vad_missed_the_start(monkeypatch):
    client = configure(monkeypatch)
    monkeypatch.setattr(main.engine, "stt_status", EngineStatus(ready=True, engine="test", model="test", device="cpu", reason=""))
    monkeypatch.setattr(main.engine, "new_vad_iterator", lambda: NoSpeechStartVad())
    with client.websocket_connect(
        "/v1/stt/stream?language=vi",
        headers={"X-Internal-Token": "test-local-token"},
    ) as websocket:
        assert websocket.receive_json()["type"] == "ready"
        websocket.send_json({"type": "config", "mode": "streaming_only"})
        for _ in range(4):
            websocket.send_bytes(np.zeros(512, dtype="<i2").tobytes())
        websocket.send_json({"type": "end"})
        committed = websocket.receive_json()
        closed = websocket.receive_json()
        assert committed["type"] == "committed"
        assert committed["text"] == "xin chao"
        assert closed["type"] == "closed"


def test_filters_vietnamese_subscription_hallucination():
    engine = SpeechEngine(main.settings)

    assert engine._is_hallucination(
        "Hãy subscribe cho kênh La La School để không bỏ lỡ những video hấp dẫn"
    )


def test_korva_float_wave_is_mono_pcm16_at_44100_hz():
    import io
    import wave

    wav_bytes = _float_audio_to_wav(np.array([-1.0, 0.0, 1.0], dtype=np.float32), 44_100)
    with wave.open(io.BytesIO(wav_bytes), "rb") as audio:
        assert audio.getframerate() == 44_100
        assert audio.getnchannels() == 1
        assert audio.getsampwidth() == 2
        assert audio.getnframes() == 3
