#!/usr/bin/env python3
"""Measure first partial/final latency through AiViva /ws/stt using PCM fixtures."""

import argparse
import asyncio
import json
import os
import time
from pathlib import Path

import websockets


SAMPLE_RATE = 16_000
BYTES_PER_SECOND = SAMPLE_RATE * 2


def parse_sample(value: str) -> tuple[str, Path]:
    label, separator, filename = value.partition("=")
    if not separator or not label or not filename:
        raise argparse.ArgumentTypeError("sample must be LABEL=path-to-mono-pcm16-16000.pcm")
    return label, Path(filename)


async def run_sample(label: str, audio_path: Path, args: argparse.Namespace) -> None:
    audio = audio_path.read_bytes()
    if not audio or len(audio) % 2:
        raise ValueError(f"{audio_path}: PCM16 file must be non-empty and have an even byte length")

    headers = {"Authorization": f"Bearer {args.access_token}"}
    options = {"max_size": None, "open_timeout": 10}
    try:
        connection = websockets.connect(args.url, additional_headers=headers, **options)
    except TypeError:
        connection = websockets.connect(args.url, extra_headers=headers, **options)

    first_result_at = None
    final_seen = False
    server_error = None
    ended = asyncio.Event()
    async with connection as websocket:
        await websocket.send(
            '{"type":"start","language":"' + args.language
            + '","purpose":"DEVICE_CHECK","sampleRate":16000,"encoding":"pcm_s16le",'
            + '"consent":true}'
        )

        async def read_events() -> None:
            nonlocal first_result_at, final_seen, server_error
            async for message in websocket:
                if not isinstance(message, str):
                    continue
                event = json.loads(message)
                if event.get("type") in ("partial", "final") and first_result_at is None:
                    first_result_at = time.perf_counter()
                if event.get("type") == "final":
                    final_seen = True
                if event.get("type") == "error":
                    server_error = event.get("code", "UNKNOWN")
                    ended.set()
                    return
                if event.get("type") == "ended":
                    ended.set()
                    return

        reader = asyncio.create_task(read_events())
        frame_bytes = args.frame_bytes - (args.frame_bytes % 2)
        started_at = None
        for offset in range(0, len(audio), frame_bytes):
            frame = audio[offset:offset + frame_bytes]
            if started_at is None:
                started_at = time.perf_counter()
            await websocket.send(frame)
            await asyncio.sleep(len(frame) / BYTES_PER_SECOND)
            if ended.is_set():
                break
        await websocket.send('{"type":"stop"}')
        try:
            await asyncio.wait_for(ended.wait(), timeout=args.stop_timeout)
        finally:
            reader.cancel()
            await asyncio.gather(reader, return_exceptions=True)

    if server_error:
        print(f"{label}: server_error={server_error}")
        return
    latency_ms = None if first_result_at is None or started_at is None else (first_result_at - started_at) * 1000
    shown = "no transcript event" if latency_ms is None else f"{latency_ms:.0f} ms"
    print(f"{label}: first_partial_or_final={shown}, final_received={str(final_seen).lower()}")


async def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sample", action="append", type=parse_sample, required=True,
                        help="label=path to raw mono PCM16 little-endian 16 kHz audio; repeat for each sample")
    parser.add_argument("--language", choices=("vi-VN", "en-US"), default="vi-VN")
    parser.add_argument("--frame-bytes", type=int, default=6400)
    parser.add_argument("--stop-timeout", type=float, default=15)
    parser.add_argument("--url", default=os.getenv("STT_SMOKE_WS_URL", "ws://localhost:8080/ws/stt"))
    parser.add_argument("--access-token", default=os.getenv("STT_SMOKE_ACCESS_TOKEN", ""))
    args = parser.parse_args()
    if not args.access_token:
        parser.error("Set STT_SMOKE_ACCESS_TOKEN to a valid student token.")
    if args.frame_bytes < 2 or args.stop_timeout <= 0:
        parser.error("frame-bytes must be >= 2 and stop-timeout must be positive.")
    for label, audio_path in args.sample:
        await run_sample(label, audio_path, args)


if __name__ == "__main__":
    asyncio.run(main())
