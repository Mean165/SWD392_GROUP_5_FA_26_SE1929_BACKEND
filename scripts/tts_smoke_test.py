#!/usr/bin/env python3
"""Measure ZeroWeight first-audio-frame latency over one reused socket."""

import asyncio
import json
import math
import os
import time
import urllib.error
import urllib.request

import websockets


BASE_URL = os.getenv("ZEROWEIGHT_API_URL", "https://api.zeroweight.ai")
API_KEY = os.getenv("ZEROWEIGHT_API_KEY")
VOICE_ID = os.getenv("ZEROWEIGHT_VOICE_ID", "ngocanh")
SPEED = float(os.getenv("ZEROWEIGHT_SPEED", "1.0"))
NORMALIZE_TEXT = os.getenv("ZEROWEIGHT_NORMALIZE_TEXT", "true").lower() == "true"
SAMPLE_RATE = 24000
HEADER_BYTES = 12
SAMPLES = [
    "Xin chào. Đây là câu kiểm tra tốc độ tổng hợp giọng nói.",
    "Em hãy giải thích khái niệm đóng gói trong lập trình hướng đối tượng.",
    "Cảm ơn em. Thầy cô hỏi thêm một ý nhé.",
]


def create_session() -> dict:
    if not API_KEY:
        raise RuntimeError("Set ZEROWEIGHT_API_KEY in the environment before running this script.")
    body = json.dumps({
        "voice_id": VOICE_ID,
        "format": "pcm_s16le",
        "sample_rate": SAMPLE_RATE,
        "normalize_text": NORMALIZE_TEXT,
        "sampling": {
            "temperature": 0.8,
            "repetition_penalty": 1.2,
            "topk": 25,
            "topp": 0.95,
        },
    }).encode("utf-8")
    request = urllib.request.Request(
        f"{BASE_URL}/api/v1/realtime/stream/session",
        data=body,
        headers={"Content-Type": "application/json", "X-ZW-Api-Key": API_KEY},
        method="POST",
    )
    try:
        with urllib.request.urlopen(request, timeout=15) as response:
            return json.loads(response.read())
    except urllib.error.HTTPError as error:
        raise RuntimeError(f"ZeroWeight session request failed with HTTP {error.code}.") from None


async def main() -> None:
    session = create_session()
    timings = []
    total_frames = 0
    total_pcm_bytes = 0
    opened_at = time.perf_counter()

    async with websockets.connect(session["url"], max_size=None, open_timeout=10) as socket:
        for index, text in enumerate(SAMPLES):
            context_id = f"smoke-{index + 1}"
            await socket.send(json.dumps({
                "type": "config",
                "context_id": context_id,
                "speed": SPEED,
            }))
            started_at = time.perf_counter()
            await socket.send(json.dumps({
                "type": "synthesize",
                "context_id": context_id,
                "text": text,
                "segment_mode": "auto",
            }))
            await socket.send(json.dumps({"type": "end", "context_id": context_id}))
            first_frame_at = None

            while True:
                message = await asyncio.wait_for(socket.recv(), timeout=30)
                if isinstance(message, bytes):
                    if len(message) <= HEADER_BYTES:
                        continue
                    frame_payload = message[HEADER_BYTES:]
                    total_frames += 1
                    total_pcm_bytes += len(frame_payload)
                    if first_frame_at is None:
                        first_frame_at = time.perf_counter()
                    continue

                event = json.loads(message)
                if event.get("type") == "error":
                    raise RuntimeError("ZeroWeight returned a synthesis error.")
                if event.get("type") == "context_done" and event.get("context_id") == context_id:
                    break

            latency_ms = None if first_frame_at is None else (first_frame_at - started_at) * 1000
            timings.append(latency_ms)
            shown = "no audio" if latency_ms is None else f"{latency_ms:.0f} ms"
            print(f"{context_id}: first audio frame {shown}")

        await socket.close(code=1000, reason="smoke test complete")

    socket_seconds = time.perf_counter() - opened_at
    estimated_credits = max(1, math.ceil(socket_seconds))
    print(f"frames={total_frames}, pcm_bytes={total_pcm_bytes}, socket_seconds={socket_seconds:.2f}")
    print(f"estimated_credits={estimated_credits} (estimate from open socket seconds)")


if __name__ == "__main__":
    asyncio.run(main())
