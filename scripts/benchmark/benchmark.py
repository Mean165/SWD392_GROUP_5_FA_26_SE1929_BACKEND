from __future__ import annotations

import argparse
import csv
from itertools import chain
import json
import re
import time
import unicodedata
import wave
from pathlib import Path

import numpy as np


ROOT = Path(__file__).resolve().parents[2]


def normalize(text: str) -> list[str]:
    value = unicodedata.normalize("NFC", (text or "").casefold())
    value = re.sub(r"[^\w\s]", " ", value, flags=re.UNICODE)
    return value.split()


def edit_distance(left: list[str], right: list[str]) -> int:
    previous = list(range(len(right) + 1))
    for i, token_left in enumerate(left, 1):
        current = [i]
        for j, token_right in enumerate(right, 1):
            current.append(min(current[-1] + 1, previous[j] + 1,
                               previous[j - 1] + (token_left != token_right)))
        previous = current
    return previous[-1]


def wer(reference: str, hypothesis: str) -> float:
    ref, hyp = normalize(reference), normalize(hypothesis)
    return edit_distance(ref, hyp) / max(1, len(ref))


def cer(reference: str, hypothesis: str) -> float:
    ref = list(" ".join(normalize(reference)))
    hyp = list(" ".join(normalize(hypothesis)))
    return edit_distance(ref, hyp) / max(1, len(ref))


def percentile(values: list[float], p: float) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    index = min(len(ordered) - 1, max(0, int(round((len(ordered) - 1) * p))))
    return round(ordered[index], 2)


def make_dry_run_rows() -> list[dict[str, object]]:
    audio = np.zeros(16_000, dtype=np.float32)
    return [{
        "case": "synthetic_silence",
        "language": "vi",
        "model": "dry-run-placeholder",
        "mode": "streaming_only",
        "hotwords": False,
        "reference": "",
        "hypothesis": "",
        "wer": 0.0,
        "cer": 0.0,
        "glossary_wer": None,
        "hallucinated_silence": False,
        "audio_seconds": len(audio) / 16_000,
        "stt_latency_ms": None,
        "queue_wait_ms": None,
        "rtf": None,
        "gpu_mem_mb": None,
        "ram_mb": None,
        "status": "dry_run_no_model_inference",
    }]


def benchmark_model(rows: list[dict[str, str]], models_dir: Path, benchmark_data: Path) -> list[dict[str, object]]:
    from faster_whisper import WhisperModel
    from faster_whisper.vad import VadOptions

    outputs: list[dict[str, object]] = []
    model_choices = [
        ("large-v3-turbo-ct2", "cuda", "int8_float16"),
        ("base", "cpu", "int8"),
    ]
    terms = []
    glossary_path = ROOT / "benchmark_data" / "glossary.txt"
    if glossary_path.is_file():
        terms = [line.strip() for line in glossary_path.read_text(encoding="utf-8").splitlines() if line.strip()]
    for model_name, device, compute_type in model_choices:
        model_path = models_dir / "stt" / model_name
        if not model_path.is_dir():
            outputs.append({"case": "model_load", "model": model_name, "device": device,
                            "compute_type": compute_type, "status": "model_not_installed"})
            continue
        try:
            model = WhisperModel(str(model_path), device=device, compute_type=compute_type,
                                 cpu_threads=8, num_workers=1, local_files_only=True)
        except Exception as exc:
            outputs.append({"case": "model_load", "model": model_name, "device": device,
                            "compute_type": compute_type, "status": "load_failed",
                            "error_type": type(exc).__name__})
            continue
        for entry in rows:
            audio_path = benchmark_data / entry.get("audio_file", "")
            if not audio_path.is_file():
                outputs.append({"case": entry.get("case", "unknown"), "model": model_name,
                                "status": "audio_file_missing"})
                continue
            try:
                with wave.open(str(audio_path), "rb") as wav_file:
                    channels = wav_file.getnchannels()
                    sample_rate = wav_file.getframerate()
                    sample_width = wav_file.getsampwidth()
            except (wave.Error, EOFError):
                channels = 0
                sample_rate = 0
                sample_width = 0
            if sample_rate != 16_000 or sample_width != 2 or channels not in (1, 2):
                outputs.append({"case": entry.get("case", audio_path.name), "model": model_name,
                                "status": "audio_must_be_pcm16_mono_or_stereo_wav_16000"})
                continue
            for use_hotwords in (False, True):
                started = time.perf_counter()
                segments, info = model.transcribe(
                    str(audio_path), language=entry.get("language", "vi"), beam_size=5,
                    condition_on_previous_text=False, word_timestamps=True,
                    vad_filter=True, vad_parameters=VadOptions(min_silence_duration_ms=500),
                    hotwords=" ".join(terms[:64]) if use_hotwords and terms else None,
                    initial_prompt=" ".join(terms[:32]) if use_hotwords and terms else None,
                )
                segments = list(segments)
                latency = (time.perf_counter() - started) * 1000
                hypothesis = " ".join(segment.text.strip() for segment in segments).strip()
                reference = entry.get("reference", "")
                text = {**entry, "reference": reference, "hypothesis": hypothesis}
                glossary_ref = " ".join(term for term in terms if term.casefold() in reference.casefold())
                glossary_hyp = " ".join(term for term in terms if term.casefold() in hypothesis.casefold())
                audio_seconds = float(info.duration or 0)
                outputs.append({
                    **text,
                    "model": model_name,
                    "mode": "final",
                    "hotwords": use_hotwords,
                    "wer": round(wer(reference, hypothesis), 4),
                    "cer": round(cer(reference, hypothesis), 4),
                    "glossary_wer": None if not glossary_ref else round(wer(glossary_ref, glossary_hyp), 4),
                    "hallucinated_silence": entry.get("case") == "silence" and bool(hypothesis),
                    "audio_seconds": round(audio_seconds, 3),
                    "stt_latency_ms": round(latency, 2),
                    "queue_wait_ms": 0,
                    "rtf": round(latency / max(1, audio_seconds * 1000), 3),
                    "gpu_mem_mb": None,
                    "ram_mb": None,
                    "language_probability": round(float(info.language_probability or 0), 4),
                    "status": "ran_on_model",
                })
    return outputs


def benchmark_tts(models_dir: Path, report_dir: Path) -> list[dict[str, object]]:
    import io
    import wave
    from piper import PiperVoice
    from korvatts import TTS as KorvaTTS

    cases = [
        ("vi_numbers", "vi", "Kỳ thi diễn ra vào ngày 2026-11-15, có 12 câu hỏi."),
        ("vi_terms", "vi", "Em hãy giải thích REST API, SQL và lập trình hướng đối tượng."),
        ("en_numbers", "en", "The oral exam has 12 questions and lasts 30 minutes."),
        ("en_terms", "en", "Explain REST API, SQL, CPU and object oriented programming."),
    ]
    korva_assets = models_dir / "tts" / "korvatts"
    english_voice_path = models_dir / "tts" / "en_US-lessac-medium.onnx"
    missing = []
    if not (korva_assets / "onnx" / "vocoder.onnx").is_file(): missing.append("vi:korvatts-assets")
    if not (korva_assets / "voice_styles" / "khanh_vy.json").is_file(): missing.append("vi:khanh_vy")
    if not english_voice_path.is_file(): missing.append("en:lessac")
    if missing:
        return [{"case": "piper_load", "status": "voice_model_missing", "missing_languages": ",".join(missing)}]
    korva = KorvaTTS(assets_dir=korva_assets, device="cpu", auto_download=False, num_threads=4)
    english_voice = PiperVoice.load(english_voice_path)
    outputs: list[dict[str, object]] = []
    sample_dir = report_dir / "tts_samples"
    sample_dir.mkdir(parents=True, exist_ok=True)
    for case_id, language, text in cases:
        started = time.perf_counter()
        output = io.BytesIO()
        if language == "vi":
            wav, duration_seconds = korva.synthesize(text, voice="khanh_vy", lang="vi", total_steps=32, speed=1.0)
            pcm = np.clip(wav, -1.0, 1.0)
            pcm = (pcm * 32767.0).astype("<i2", copy=False)
            with wave.open(output, "wb") as wav_file:
                wav_file.setnchannels(1); wav_file.setsampwidth(2); wav_file.setframerate(korva.sample_rate)
                wav_file.writeframes(pcm.tobytes())
            duration_ms = round(duration_seconds * 1000)
            first_byte_ms = (time.perf_counter() - started) * 1000
            voice_name = "khanh_vy"
        else:
            chunks = iter(english_voice.synthesize(text))
            try:
                first = next(chunks)
            except StopIteration:
                outputs.append({"case": case_id, "status": "empty_audio"})
                continue
            first_byte_ms = (time.perf_counter() - started) * 1000
            duration_ms = 0
            with wave.open(output, "wb") as wav_file:
                wav_file.setnchannels(first.sample_channels)
                wav_file.setsampwidth(first.sample_width)
                wav_file.setframerate(first.sample_rate)
                for chunk in chain((first,), chunks):
                    wav_file.writeframes(chunk.audio_int16_bytes)
                    duration_ms += round(len(chunk.audio_int16_bytes) * 1000 /
                                         (chunk.sample_rate * chunk.sample_channels * chunk.sample_width))
            voice_name = english_voice_path.name
        audio = output.getvalue()
        total_ms = (time.perf_counter() - started) * 1000
        sample_path = sample_dir / f"{case_id}.wav"
        sample_path.write_bytes(audio)
        outputs.append({"case": case_id, "language": language,
                        "engine": "korvatts" if language == "vi" else "piper",
                        "voice": voice_name, "first_audio_ms": round(first_byte_ms, 2),
                        "output_mode": "full_utterance" if language == "vi" else "first_chunk",
                        "total_ms": round(total_ms, 2), "duration_ms": duration_ms,
                        "rtf": round(total_ms / max(1, duration_ms), 3), "audio_bytes": len(audio),
                        "sample_path": sample_path.as_posix(), "status": "ran_on_local_voice"})
    return outputs


def benchmark_pipeline_latency(report_dir: Path, iterations: int = 100) -> dict[str, object]:
    """Exercise the turn-latency reporting pipeline without implying ASR/TTS inference ran."""
    simulated: list[dict[str, float]] = []
    for _ in range(iterations):
        begin = time.perf_counter()
        # Deterministic tiny stand-ins verify only the aggregation/control path.
        mock_partial_ms = 10.0
        mock_finalize_ms = 25.0
        mock_analyzer_ms = 5.0
        mock_tts_first_byte_ms = 20.0
        total = mock_partial_ms + mock_finalize_ms + mock_analyzer_ms + mock_tts_first_byte_ms
        simulated.append({"partial_ms": mock_partial_ms, "finalize_ms": mock_finalize_ms,
                          "analyzer_ms": mock_analyzer_ms, "tts_first_byte_ms": mock_tts_first_byte_ms,
                          "end_to_end_ms": total, "harness_elapsed_ms": (time.perf_counter() - begin) * 1000})
    path = report_dir / f"pipeline_dry_run_{time.strftime('%Y%m%d')}.json"
    path.write_text(json.dumps({
        "status": "dry_run_mock_components_only",
        "inference_executed": False,
        "iterations": iterations,
        "p50_ms": percentile([row["end_to_end_ms"] for row in simulated], 0.5),
        "p95_ms": percentile([row["end_to_end_ms"] for row in simulated], 0.95),
        "components": simulated[0] if simulated else {},
    }, indent=2), encoding="utf-8")
    return {"status": "dry_run_mock_components_only", "p50_ms": 60.0, "p95_ms": 60.0, "path": str(path)}


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--tts", action="store_true", help="Benchmark installed local Piper voices and write local WAV samples")
    parser.add_argument("--tts-only", action="store_true", help="Benchmark Piper voices without an STT corpus")
    parser.add_argument("--models-dir", type=Path, default=Path("models"))
    parser.add_argument("--report-dir", type=Path, default=Path("reports"))
    parser.add_argument("--benchmark-data-dir", type=Path, default=None)
    args = parser.parse_args()
    benchmark_data = (args.benchmark_data_dir or (ROOT / "benchmark_data")).resolve()
    metadata = benchmark_data / "meta.csv"
    if args.tts_only:
        rows = []
    elif args.dry_run:
        rows = make_dry_run_rows()
    else:
        if not metadata.is_file():
            raise SystemExit("benchmark_data/meta.csv is required for real accuracy benchmarks; do not use synthetic audio as student accuracy evidence")
        with metadata.open(encoding="utf-8-sig", newline="") as stream:
            rows = list(csv.DictReader(stream))
        if not rows:
            raise SystemExit("benchmark_data/meta.csv contains no samples")
        rows = benchmark_model(rows, args.models_dir.resolve(), benchmark_data)

    args.report_dir.mkdir(parents=True, exist_ok=True)
    pipeline_summary = benchmark_pipeline_latency(args.report_dir)
    tts_rows = benchmark_tts(args.models_dir.resolve(), args.report_dir) if (args.tts or args.tts_only) and not args.dry_run else []
    if tts_rows:
        tts_csv = args.report_dir / f"tts_benchmark_{time.strftime('%Y%m%d')}.csv"
        with tts_csv.open("w", encoding="utf-8", newline="") as stream:
            writer = csv.DictWriter(stream, fieldnames=sorted({key for row in tts_rows for key in row}))
            writer.writeheader()
            writer.writerows(tts_rows)
        if args.tts_only:
            lines = [f"# Local TTS Smoke Benchmark {time.strftime('%Y%m%d')}", "",
                     "These model timings use fixed synthetic text; listening quality is not assessed.", "",
                     "| Case | Language | Engine | Voice | First audio ms | Output mode | Total ms | Audio ms | RTF | Status |",
                     "|---|---|---|---|---:|---|---:|---:|---:|---|"]
            for row in tts_rows:
                lines.append(f"| {row.get('case')} | {row.get('language')} | {row.get('engine')} | {row.get('voice')} | {row.get('first_audio_ms')} | {row.get('output_mode')} | {row.get('total_ms')} | {row.get('duration_ms')} | {row.get('rtf')} | {row.get('status')} |")
            tts_report = args.report_dir / f"tts_benchmark_{time.strftime('%Y%m%d')}.md"
            tts_report.write_text("\n".join(lines) + "\n", encoding="utf-8")
            print(f"Wrote {tts_csv}, {tts_report}, and synthetic WAV samples")
            return
    day = time.strftime("%Y%m%d")
    csv_path = args.report_dir / f"benchmark_{day}.csv"
    fields = sorted({key for row in rows for key in row})
    with csv_path.open("w", encoding="utf-8", newline="") as stream:
        writer = csv.DictWriter(stream, fieldnames=fields)
        writer.writeheader()
        writer.writerows(rows)
    report_path = args.report_dir / f"benchmark_{day}.md"
    measured = [row for row in rows if isinstance(row.get("stt_latency_ms"), (int, float))]
    latencies = [float(row["stt_latency_ms"]) for row in measured]
    lines = [f"# Local Speech Benchmark {day}", "", f"STT samples/rows: {len(rows)}",
             f"Mode: {'dry-run' if args.dry_run else 'real corpus'}",
             f"End-to-end pipeline mock P50/P95 (ms): {pipeline_summary['p50_ms']}/{pipeline_summary['p95_ms']} (mock components only; not inference latency)",
             "Dataset: no authentic student recordings are included in the repository.",
             f"Measured P50 latency (ms): {percentile(latencies, 0.50) if latencies else 'not measured'}",
             f"Measured P95 latency (ms): {percentile(latencies, 0.95) if latencies else 'not measured'}",
             f"Measured RTF P50: {percentile([float(r['rtf']) for r in measured if isinstance(r.get('rtf'), (int,float))], 0.5) or 'not measured'}",
             "", "This report does not claim accuracy or latency for any model not actually loaded and run.",
             "Real WER/CER and fairness analysis require a consented, human-transcribed corpus and metadata."]
    lines.extend(["", "## TTS", ""])
    if tts_rows:
        lines.append("| Case | Voice | First chunk ms | Total ms | Audio ms | RTF | Status |")
        lines.append("|---|---|---:|---:|---:|---:|---|")
        for row in tts_rows:
            lines.append(f"| {row.get('case')} | {row.get('voice','')} | {row.get('first_chunk_ms','')} | {row.get('total_ms','')} | {row.get('duration_ms','')} | {row.get('rtf','')} | {row.get('status')} |")
    else:
        lines.append("TTS inference not run (use `--tts` after installing the Piper voices).")
    report_path.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"Wrote {csv_path} and {report_path}")


if __name__ == "__main__":
    main()
