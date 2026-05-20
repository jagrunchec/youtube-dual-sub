#!/usr/bin/env python3
"""
whisper_transcribe.py — Local transcription via faster-whisper (GPU-accelerated).

Usage: python3 whisper_transcribe.py <video_id> <model_size>
  model_size : tiny | base | small | medium | large-v3

Output (stdout): {"language": "xx", "entries": [{"text":"..","start":0.0,"duration":2.5}, ...]}
Errors (stderr): plain text — the Java caller checks exit code.
"""

import sys
import json
import os
import shutil
import subprocess
import tempfile


def _find_yt_dlp():
    """Return the yt-dlp executable path, searching PATH and common pip install locations."""
    path = shutil.which("yt-dlp")
    if path:
        return path
    # pip --user install puts scripts here on Linux
    candidates = [
        os.path.expanduser("~/.local/bin/yt-dlp"),
        "/usr/local/bin/yt-dlp",
        "/usr/bin/yt-dlp",
    ]
    for c in candidates:
        if os.path.isfile(c):
            return c
    return "yt-dlp"  # let the OS find it (will fail with a clear error if not found)


def main():
    if len(sys.argv) < 3:
        die("Usage: whisper_transcribe.py <video_id> <model_size>")

    video_id   = sys.argv[1]
    model_size = sys.argv[2]          # "medium" or "large-v3"
    url        = f"https://www.youtube.com/watch?v={video_id}"

    with tempfile.TemporaryDirectory(prefix="dualsub_whisper_") as tmpdir:

        # ── 1. Download best audio with yt-dlp ───────────────────────────────
        # We keep whatever format yt-dlp chooses (webm/opus/m4a);
        # faster-whisper decodes via ffmpeg internally.
        audio_tmpl = os.path.join(tmpdir, "audio.%(ext)s")
        yt_dlp = _find_yt_dlp()
        dl = subprocess.run(
            [yt_dlp, "-x", "-o", audio_tmpl,
             "--no-playlist", "--quiet",
             "--no-warnings", url],
            capture_output=True, text=True, timeout=300
        )
        if dl.returncode != 0:
            die(f"yt-dlp failed: {dl.stderr.strip()}")

        files = [f for f in os.listdir(tmpdir) if f.startswith("audio.")]
        if not files:
            die("yt-dlp produced no output file")
        audio_path = os.path.join(tmpdir, files[0])

        # ── 2. Load faster-whisper model ──────────────────────────────────────
        try:
            from faster_whisper import WhisperModel
        except ImportError:
            die("faster-whisper is not installed. Run: pip3 install faster-whisper")

        device, compute = _best_device()
        print(f"[Whisper] Loading {model_size} on {device} ({compute})", file=sys.stderr)

        try:
            model = WhisperModel(model_size, device=device, compute_type=compute)
        except Exception as e:
            if device != "cpu":
                print(f"[Whisper] GPU failed ({e}), retrying on CPU", file=sys.stderr)
                model = WhisperModel(model_size, device="cpu", compute_type="int8")
            else:
                die(str(e))

        # ── 3. Transcribe ─────────────────────────────────────────────────────
        # VAD filter removes non-speech segments; beam_size=5 balances speed/quality.
        print(f"[Whisper] Transcribing {audio_path} ...", file=sys.stderr)
        segments, info = model.transcribe(
            audio_path,
            beam_size=5,
            vad_filter=True,
            vad_parameters={"min_silence_duration_ms": 500},
        )

        entries = []
        for seg in segments:
            text = seg.text.strip()
            if text:
                entries.append({
                    "text":     text,
                    "start":    round(float(seg.start), 3),
                    "duration": round(float(seg.end) - float(seg.start), 3),
                })

        print(
            json.dumps({"language": info.language, "entries": entries},
                       ensure_ascii=False),
        )


def _best_device():
    """Return (device, compute_type) — prefer CUDA float16, fall back to CPU int8."""
    try:
        import ctranslate2
        if ctranslate2.get_cuda_device_count() > 0:
            return "cuda", "float16"
    except Exception:
        pass
    return "cpu", "int8"


def die(msg: str):
    print(f"[Whisper ERROR] {msg}", file=sys.stderr)
    # Also emit a JSON error on stdout so the Java caller can parse it
    print(json.dumps({"error": msg}))
    sys.exit(1)


if __name__ == "__main__":
    main()
