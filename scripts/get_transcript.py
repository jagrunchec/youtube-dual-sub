#!/usr/bin/env python3
"""
Fetches the source-language transcript for a YouTube video using youtube-transcript-api.

Output (stdout): JSON array  [{"start": float, "duration": float, "text": str}, ...]
On error:        JSON object {"error": str}

Usage: python get_transcript.py VIDEO_ID
"""

import sys
import io
import json

# Force UTF-8 on stdout — Windows defaults to CP850 / CP1252
sys.stdout = io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8", errors="replace")


def main():
    if len(sys.argv) < 2:
        print(json.dumps({"error": "Usage: get_transcript.py VIDEO_ID"}))
        sys.exit(1)

    video_id = sys.argv[1]

    try:
        from youtube_transcript_api import YouTubeTranscriptApi
    except ImportError:
        print(json.dumps({
            "error": "youtube-transcript-api not installed. Run: pip install youtube-transcript-api"
        }))
        sys.exit(1)

    api = YouTubeTranscriptApi()

    # Preferred source languages (we pass the raw transcript to Java,
    # which then translates via Google Translate)
    LANG_PREFERENCE = ["de", "en", "fr", "es", "it", "pt", "ja", "ko", "zh-Hans", "zh"]

    try:
        transcript_list = api.list(video_id)

        # Prefer auto-generated (ASR) transcripts — more widely available
        found = None
        for lang in LANG_PREFERENCE:
            try:
                found = transcript_list.find_transcript([lang])
                break
            except Exception:
                continue

        # Fallback: use the first available transcript
        if found is None:
            found = next(iter(transcript_list))

        entries = list(found.fetch())
        result = [
            {
                "start":    entry.start,
                "duration": entry.duration,
                "text":     entry.text.replace("\n", " ").strip()
            }
            for entry in entries
            if entry.text.strip()
        ]
        # Include the detected language code so the Java layer can use it
        # (e.g. for "immersion mode" where track 1 = source language as-is)
        print(json.dumps({
            "language": found.language_code,
            "entries":  result,
        }, ensure_ascii=False))

    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)


if __name__ == "__main__":
    main()
