#!/usr/bin/env python3
"""
whisper_openai.py — Cloud transcription via OpenAI Whisper API.

Usage: python3 whisper_openai.py <video_id> <api_key>

OpenAI limits: 25 MB per file. We download a low-bitrate mp3 to stay under.
Cost: ~$0.006 / minute of audio (~$0.36 / hour).

Output (stdout): {"language": "xx", "entries": [{"text":"..","start":0.0,"duration":2.5}, ...]}
Errors (stderr): plain text.
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
    candidates = [
        os.path.expanduser("~/.local/bin/yt-dlp"),
        "/usr/local/bin/yt-dlp",
        "/usr/bin/yt-dlp",
    ]
    for c in candidates:
        if os.path.isfile(c):
            return c
    return "yt-dlp"


def main():
    if len(sys.argv) < 3:
        die("Usage: whisper_openai.py <video_id> <api_key>")

    video_id = sys.argv[1]
    api_key  = sys.argv[2]
    url      = f"https://www.youtube.com/watch?v={video_id}"

    with tempfile.TemporaryDirectory(prefix="dualsub_openai_") as tmpdir:

        # ── 1. Download audio as mp3 (low quality to stay under 25 MB limit) ─
        audio_path = os.path.join(tmpdir, "audio.mp3")
        yt_dlp = _find_yt_dlp()
        dl = subprocess.run(
            [yt_dlp, "-x", "--audio-format", "mp3", "--audio-quality", "5",
             "-o", audio_path, "--no-playlist", "--quiet", "--no-warnings", url],
            capture_output=True, text=True, timeout=300
        )
        if dl.returncode != 0:
            die(f"yt-dlp failed: {dl.stderr.strip()}")
        if not os.path.exists(audio_path):
            die("yt-dlp produced no mp3 file")

        size_mb = os.path.getsize(audio_path) / 1_048_576
        if size_mb > 24:
            die(
                f"Audio file too large for OpenAI API ({size_mb:.1f} MB > 24 MB). "
                "Use faster-whisper (local) for long videos."
            )
        print(f"[Whisper/OpenAI] Audio: {size_mb:.1f} MB", file=sys.stderr)

        # ── 2. Call OpenAI Transcriptions API ─────────────────────────────────
        try:
            from openai import OpenAI
        except ImportError:
            die("openai package is not installed. Run: pip3 install openai")

        client = OpenAI(api_key=api_key)
        print("[Whisper/OpenAI] Sending to API...", file=sys.stderr)

        with open(audio_path, "rb") as f:
            result = client.audio.transcriptions.create(
                model="whisper-1",
                file=f,
                response_format="verbose_json",
                timestamp_granularities=["segment"],
            )

        entries = []
        for seg in (result.segments or []):
            text = seg.text.strip()
            if text:
                entries.append({
                    "text":     text,
                    "start":    round(float(seg.start), 3),
                    "duration": round(float(seg.end) - float(seg.start), 3),
                })

        lang = getattr(result, "language", None) or "unknown"
        print(json.dumps({"language": lang, "entries": entries}, ensure_ascii=False))


def die(msg: str):
    print(f"[Whisper/OpenAI ERROR] {msg}", file=sys.stderr)
    print(json.dumps({"error": msg}))
    sys.exit(1)


if __name__ == "__main__":
    main()
