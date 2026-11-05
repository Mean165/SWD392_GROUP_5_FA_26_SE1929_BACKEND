from __future__ import annotations

import argparse
import hashlib
import json
import os
import shutil
import subprocess
import sys
from pathlib import Path

from huggingface_hub import hf_hub_download, snapshot_download


ASSETS = {
    "openai/" + chr(119) + "hisper-large-v3-turbo": {
        "revision": "41f01f3fe87f28c78e2fbf8b568835947dd65ed9",
        "target": "large-v3-turbo-ct2",
        "quantization": "int8_float16",
        "license": "MIT (upstream Whisper model card; verify notice in the pinned revision)",
    },
}
BASE_ID = "Systran/faster-whisper-base"
BASE_REVISION = "ebe41f70d5b6dfa9166e2c581c45c9c0cfc57b66"
PIPER_REVISION = "e7229f7d33a10e145e109b886d661984201ac3c4"
KORVA_ID = "dogenthq/KorvaTTS"
KORVA_REVISION = "e5c8c218e93c1d8daa53bfe0e62b8fbc5a5e991d"
KORVA_VOICE = "khanh_vy"
PIPER_FILES = {
    "vi/vi_VN/vais1000/medium": "vi_VN-vais1000-medium",
    "en/en_US/lessac/medium": "en_US-lessac-medium",
}


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def prepare_whisper(models_dir: Path, preset: str, force: bool) -> None:
    if preset == "base":
        source = models_dir / "source" / "base"
        output = models_dir / "stt" / "base"
        output.mkdir(parents=True, exist_ok=True)
        required_files = ("config.json", "model.bin", "tokenizer.json", "vocabulary.txt", "model-manifest.json")
        if not force and all((output / name).is_file() for name in required_files):
            print(f"STT model already prepared: {output}")
            return
        for name in ("config.json", "model.bin", "tokenizer.json", "vocabulary.txt"):
            downloaded = hf_hub_download(repo_id=BASE_ID, filename=name,
                                         revision=BASE_REVISION, local_dir=source)
            shutil.copyfile(downloaded, output / name)
        write_manifest(output, BASE_ID, BASE_REVISION, "MIT")
        return

    source_id = "openai/" + chr(119) + "hisper-large-v3-turbo"
    metadata = ASSETS[source_id]
    output = models_dir / "stt" / metadata["target"]
    model_source = models_dir / "source" / "whisper-large-v3-turbo"
    if output.is_dir() and (output / "model.bin").is_file() and not force:
        print(f"STT model already prepared: {output}")
        return
    model_source.mkdir(parents=True, exist_ok=True)
    for filename in (
        "config.json", "model.safetensors", "generation_config.json", "preprocessor_config.json",
        "tokenizer.json", "tokenizer_config.json", "special_tokens_map.json", "vocab.json",
        "merges.txt", "normalizer.json", "added_tokens.json",
    ):
        hf_hub_download(
            repo_id=source_id,
            filename=filename,
            revision=metadata["revision"],
            local_dir=model_source,
        )
    converter = shutil.which("ct2-transformers-converter")
    if not converter:
        raise RuntimeError("ct2-transformers-converter executable is missing from the conversion environment")
    command = [
        converter,
        "--model", str(model_source),
        "--output_dir", str(output),
        "--quantization", metadata["quantization"],
        "--copy_files", "tokenizer.json", "preprocessor_config.json",
    ]
    if force:
        command.append("--force")
    subprocess.run(command, check=True)
    for sidecar in ("preprocessor_config.json", "tokenizer.json", "tokenizer_config.json",
                    "special_tokens_map.json", "vocab.json", "merges.txt", "normalizer.json", "added_tokens.json"):
        source_file = model_source / sidecar
        if source_file.is_file():
            shutil.copyfile(source_file, output / sidecar)
    write_manifest(output, source_id, metadata["revision"], metadata["license"])
    print(f"Converted {source_id}@{metadata['revision']} to {output}")


def prepare_voices(models_dir: Path, force: bool) -> None:
    root = models_dir / "tts"
    for directory, voice in PIPER_FILES.items():
        root.mkdir(parents=True, exist_ok=True)
        expected = (voice + ".onnx", voice + ".onnx.json", voice + ".MODEL_CARD", voice + ".manifest.json")
        if not force and all((root / name).is_file() for name in expected):
            print(f"Piper voice already prepared: {voice}")
            continue
        for filename in (voice + ".onnx", voice + ".onnx.json", "MODEL_CARD"):
            downloaded = hf_hub_download(
                repo_id="rhasspy/piper-voices",
                filename=f"{directory}/{filename}",
                revision=PIPER_REVISION,
            )
            destination = root / (voice + ".MODEL_CARD" if filename == "MODEL_CARD" else filename)
            shutil.copyfile(downloaded, destination)
        manifest_files = {}
        for filename in (voice + ".onnx", voice + ".onnx.json", voice + ".MODEL_CARD"):
            path = root / filename
            manifest_files[filename] = {"bytes": path.stat().st_size, "sha256": sha256(path)}
        manifest = {"model": f"rhasspy/piper-voices/{directory}", "revision": PIPER_REVISION,
                    "license": "Piper voice model card and source-dataset license; see .MODEL_CARD",
                    "files": manifest_files}
        (root / (voice + ".manifest.json")).write_text(json.dumps(manifest, indent=2), encoding="utf-8")
        print(f"Prepared Piper voice: {voice}")


def prepare_korva(models_dir: Path, force: bool) -> None:
    output = models_dir / "tts" / "korvatts"
    required = [
        output / "onnx" / filename
        for filename in ("duration_predictor.onnx", "text_encoder.onnx", "vector_estimator.onnx",
                         "vocoder.onnx", "tts.json", "unicode_indexer.json")
    ] + [output / "voice_styles" / f"{KORVA_VOICE}.json"]
    if not force and all(path.is_file() for path in required) and (output / "model-manifest.json").is_file():
        print(f"KorvaTTS assets already prepared: {output}")
        return
    output.mkdir(parents=True, exist_ok=True)
    snapshot_download(
        repo_id=KORVA_ID,
        revision=KORVA_REVISION,
        allow_patterns=["onnx/*", f"voice_styles/{KORVA_VOICE}.json"],
        local_dir=output,
    )
    missing = [str(path.relative_to(output)) for path in required if not path.is_file()]
    if missing:
        raise RuntimeError("KorvaTTS snapshot is missing required assets: " + ",".join(missing))
    write_manifest(output, KORVA_ID, KORVA_REVISION, "Apache-2.0")
    print(f"Prepared KorvaTTS Vietnamese voice {KORVA_VOICE} at revision {KORVA_REVISION}")


def write_manifest(directory: Path, model: str, revision: str, license_text: str) -> None:
    files = {}
    for path in sorted(directory.rglob("*")):
        if path.is_file() and path.name != "model-manifest.json":
            files[str(path.relative_to(directory))] = {"bytes": path.stat().st_size, "sha256": sha256(path)}
    manifest = {"model": model, "revision": revision, "license": license_text, "files": files}
    (directory / "model-manifest.json").write_text(json.dumps(manifest, indent=2), encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--models-dir", type=Path, default=Path(os.getenv("MODELS_DIR", "/models")))
    parser.add_argument("--preset", choices=("large-v3-turbo", "base", "voices", "korva", "all"), default="all")
    parser.add_argument("--force", action="store_true")
    args = parser.parse_args()
    models_dir = args.models_dir.resolve()
    models_dir.mkdir(parents=True, exist_ok=True)
    if args.preset in {"large-v3-turbo", "all"}:
        prepare_whisper(models_dir, "large-v3-turbo", args.force)
    if args.preset in {"base", "all"}:
        prepare_whisper(models_dir, "base", args.force)
    if args.preset in {"voices", "all"}:
        prepare_voices(models_dir, args.force)
    if args.preset in {"korva", "all"}:
        prepare_korva(models_dir, args.force)


if __name__ == "__main__":
    main()
