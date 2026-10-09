#!/usr/bin/env python3
"""Prepare the pinned, English-only offline TTS asset for the Android APK.

The model archive is downloaded at build time only. The app itself ships the
prepared ZIP and performs no TTS-related network requests at runtime.
"""
from __future__ import annotations

import hashlib
import os
import sys
import urllib.request
import zipfile
from pathlib import Path
import tarfile

ROOT = Path(__file__).resolve().parents[1]
CACHE = ROOT / ".cache" / "tts"
ARCHIVE_NAME = "vits-piper-en_US-amy-medium-int8.tar.bz2"
ARCHIVE_URL = (
    "https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/"
    + ARCHIVE_NAME
)
ARCHIVE_SIZE = 21_028_122
ARCHIVE_SHA256 = "bd23c0aa629eb3719448582f45ede49e8fa6a679061fed5eab16a6a6fd8e7e82"
ARCHIVE_PREFIX = "vits-piper-en_US-amy-medium-int8/"
MODEL_NAME = "en_US-amy-medium.onnx"
MODEL_SHA256 = "e93cf3361c5561b9fedcec20b26ae5ecd068172e514740562f1263be65b3848f"
VOICE_ATTRIBUTION = """Voice/model attribution: Amy (en_US), Piper VITS, medium quality; model distributed by the Sherpa-ONNX TTS models release. The upstream voice data is attributed by its model card to Mycroft Mimic3 Voices (https://github.com/MycroftAI/mimic3-voices). License: Creative Commons Attribution-ShareAlike 4.0 International (CC BY-SA 4.0), see MIMIC3-VOICES-LICENSE.txt. The Sherpa-ONNX archive supplies this ONNX model in INT8-quantized form; the model file is included unchanged in the app package.
"""
VOICE_LICENSE_PATH = ROOT / "licenses" / "MIMIC3-VOICES-LICENSE.txt"
PACK_PATH = ROOT / "android" / "app" / "src" / "main" / "assets" / "tts" / "tts-en.zip"
CORE_ESPEAK_FILES = {
    "en_dict",
    "intonations",
    "phondata",
    "phondata-manifest",
    "phonindex",
    "phontab",
}


def sha256_file(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for block in iter(lambda: f.read(1024 * 1024), b""):
            h.update(block)
    return h.hexdigest()


def download_archive(path: Path) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if path.exists() and path.stat().st_size == ARCHIVE_SIZE and sha256_file(path) == ARCHIVE_SHA256:
        print(f"Using cached, verified voice archive: {path}")
        return

    part = path.with_suffix(path.suffix + ".part")
    part.unlink(missing_ok=True)
    request = urllib.request.Request(
        ARCHIVE_URL,
        headers={"User-Agent": "Simorgh-Irani-Offline-build/1.3"},
    )
    print(f"Downloading pinned English voice archive ({ARCHIVE_SIZE:,} bytes)...")
    total = 0
    try:
        with urllib.request.urlopen(request, timeout=45) as response, part.open("wb") as out:
            while True:
                chunk = response.read(1024 * 1024)
                if not chunk:
                    break
                out.write(chunk)
                total += len(chunk)
                print(f"  {total:,}/{ARCHIVE_SIZE:,} bytes", flush=True)
        if total != ARCHIVE_SIZE:
            raise RuntimeError(f"unexpected archive size: {total}")
        digest = sha256_file(part)
        if digest != ARCHIVE_SHA256:
            raise RuntimeError(f"archive SHA-256 mismatch: {digest}")
        os.replace(part, path)
    except Exception:
        part.unlink(missing_ok=True)
        raise


def validate_pack(path: Path) -> bool:
    if not path.is_file():
        return False
    try:
        with zipfile.ZipFile(path) as zf:
            if zf.testzip() is not None:
                return False
            names = set(zf.namelist())
            required = {
                MODEL_NAME,
                MODEL_NAME + ".json",
                "tokens.txt",
                "MODEL_CARD",
                "VOICE_ATTRIBUTION.txt",
                "MIMIC3-VOICES-LICENSE.txt",
                *(f"espeak-ng-data/{name}" for name in CORE_ESPEAK_FILES),
            }
            if not required.issubset(names):
                return False
            if any(name == "fa_dict" or name.endswith("/fa_dict") for name in names):
                return False
            model_hash = hashlib.sha256(zf.read(MODEL_NAME)).hexdigest()
            return (
                model_hash == MODEL_SHA256
                and zf.read("VOICE_ATTRIBUTION.txt") == VOICE_ATTRIBUTION.encode("utf-8")
                and zf.read("MIMIC3-VOICES-LICENSE.txt") == VOICE_LICENSE_PATH.read_bytes()
            )
    except (OSError, zipfile.BadZipFile, KeyError):
        return False


def prepare() -> None:
    if validate_pack(PACK_PATH):
        print(f"Verified English-only TTS pack already prepared: {PACK_PATH} ({PACK_PATH.stat().st_size:,} bytes)")
        return

    cache_path = CACHE / ARCHIVE_NAME
    download_archive(cache_path)
    staged: dict[str, bytes] = {}
    with tarfile.open(cache_path, mode="r:bz2") as archive:
        for member in archive.getmembers():
            if not member.isfile() or not member.name.startswith(ARCHIVE_PREFIX):
                continue
            relative = member.name[len(ARCHIVE_PREFIX):]
            keep = (
                relative in {"MODEL_CARD", MODEL_NAME, MODEL_NAME + ".json", "tokens.txt"}
                or relative.startswith("espeak-ng-data/lang/")
                or relative.startswith("espeak-ng-data/voices/")
                or relative.startswith("espeak-ng-data/")
                and relative.removeprefix("espeak-ng-data/") in CORE_ESPEAK_FILES
            )
            if not keep:
                continue
            source = archive.extractfile(member)
            if source is None:
                raise RuntimeError(f"could not read archive member {member.name}")
            staged[relative] = source.read()

    staged["VOICE_ATTRIBUTION.txt"] = VOICE_ATTRIBUTION.encode("utf-8")
    staged["MIMIC3-VOICES-LICENSE.txt"] = VOICE_LICENSE_PATH.read_bytes()

    if MODEL_NAME not in staged or hashlib.sha256(staged[MODEL_NAME]).hexdigest() != MODEL_SHA256:
        raise RuntimeError("the extracted ONNX model failed its pinned SHA-256 check")
    required_files = {
        MODEL_NAME + ".json",
        "tokens.txt",
        "MODEL_CARD",
        "VOICE_ATTRIBUTION.txt",
        "MIMIC3-VOICES-LICENSE.txt",
    }
    required_files.update(f"espeak-ng-data/{name}" for name in CORE_ESPEAK_FILES)
    if not required_files.issubset(staged):
        raise RuntimeError("the archive is missing a required English TTS/phonemizer file")
    if any(name == "fa_dict" or name.endswith("/fa_dict") for name in staged):
        raise RuntimeError("Persian dictionary unexpectedly included in English voice pack")

    PACK_PATH.parent.mkdir(parents=True, exist_ok=True)
    temporary = PACK_PATH.with_suffix(".zip.part")
    temporary.unlink(missing_ok=True)
    try:
        with zipfile.ZipFile(temporary, "w", allowZip64=True) as zf:
            for name in sorted(staged):
                # ONNX weights are already quantized; ZIP_STORED avoids wasting build CPU.
                compression = zipfile.ZIP_STORED if name == MODEL_NAME else zipfile.ZIP_DEFLATED
                zf.writestr(name, staged[name], compress_type=compression, compresslevel=6)
        if not validate_pack(temporary):
            raise RuntimeError("generated offline voice pack failed validation")
        os.replace(temporary, PACK_PATH)
    except Exception:
        temporary.unlink(missing_ok=True)
        raise

    print(
        "Prepared verified English-only offline TTS asset: "
        f"{PACK_PATH} ({PACK_PATH.stat().st_size:,} bytes); "
        f"INT8 model {len(staged[MODEL_NAME]):,} bytes; "
        f"phonemizer files {sum(name.startswith('espeak-ng-data/') for name in staged)}"
    )


if __name__ == "__main__":
    try:
        prepare()
    except Exception as exc:
        print(f"TTS asset preparation failed: {exc}", file=sys.stderr)
        raise
