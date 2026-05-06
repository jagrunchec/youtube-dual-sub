# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project does

DualSub is a local web application that plays a YouTube video and shows two simultaneous subtitle tracks underneath it, each translated into a different language chosen by the user. The user picks a video URL and two languages (FR, EN, ES, IT, DE); the backend fetches the transcript and runs both translations in parallel before the video starts.

## Build and run

Maven is not on the system PATH. Use the local installation:

```
# Build (skip tests — there are none)
C:\Users\User\maven\apache-maven-3.9.9\bin\mvn.cmd clean package -DskipTests

# Run the JAR (must be launched from the project root so the scripts/ directory is found)
java -jar target\youtube-dual-sub-1.0.0.jar
```

The app is served at `http://localhost:8080`.

After killing a running server, wait ~2 s before rebuilding because Maven's `clean` will fail if the JAR is still locked by the Java process.

## Python dependencies

Two Python scripts are used as subprocesses. Both libraries must be installed for the exact executable configured in `application.properties`:

```
C:\Python314\python.exe -m pip install youtube-transcript-api
C:\Python314\python.exe -m pip install deepmultilingualpunctuation
```

`deepmultilingualpunctuation` downloads the `oliverguhr/fullstop-punctuation-multilang-large` model (~680 MB) from Hugging Face on the first run. Subsequent runs load from the local cache (`~/.cache/huggingface`). If the library is not installed, the punctuation step is silently skipped.

`app.transcript.python` must be an **absolute path** — `ProcessBuilder` does not inherit the full shell PATH, so `python` alone will resolve to the wrong executable (the Windows Store stub).

## Architecture

### Request flow

```
Browser → POST /api/process
            │
            ├─ YouTubeTranscriptService.fetchTranscript()
            │     └─ fetchViaYtdlp()          ← subprocess: scripts/get_transcript.py
            │           └─ mergeSubtitles()   ← merge ASR fragments into readable phrases
            │
            ├─ TranslationService.translate(entries, lang1)  ← Google Translate (gtx, no key)
            └─ TranslationService.translate(entries, lang2)
```

### Why two separate steps instead of YouTube's tlang parameter

YouTube's `tlang` parameter (which would have YouTube do the translation server-side) returns HTTP 429 for all server-side requests regardless of cookies, delays, or HTTP version. Direct HTTP fetches of the timedtext endpoint also return `200 OK` with an empty body (YouTube's bot-detection silent-block). The Python `youtube-transcript-api` library bypasses this by using the Android VR InnerTube client, which is why it works where the Java HTTP client does not.

### Key design decisions

- **Subtitle merging** (`mergeSubtitles`): YouTube ASR produces 3–7 word fragments. These are merged into ~80-character phrases before translation — fewer, longer chunks improve both translation quality and readability. Merge stops at strong punctuation, gaps > 2500 ms, or when the combined text would exceed 80 chars.
- **UTF-8 everywhere**: `ProcessBuilder` sets `PYTHONIOENCODING=utf-8` and `PYTHONUTF8=1`; `TranslationService` reads HTTP responses as `byte[]` and decodes with `StandardCharsets.UTF_8` to avoid Java's ISO-8859-1 default.
- **Google Translate chunking**: subtitles are batched into ≤ 2000-char chunks joined by `\n`, sent to the unofficial `gtx` endpoint, and split back by `\n`. A 400 ms delay between chunks prevents 429s.
- **Binary search sync**: `app.js` runs a 100 ms `setInterval` and uses binary search over the sorted subtitle array to find the entry active at `player.getCurrentTime() * 1000`.

### Source layout

```
src/main/java/com/dualsub/
  controller/VideoController.java     REST endpoints (/api/process, /api/debug/*)
  service/YouTubeTranscriptService.java   transcript fetch + merge + punctuation pipeline
  service/TranslationService.java         Google Translate chunking + retry
  model/                                  SubtitleEntry, ProcessRequest, ProcessResponse

src/main/resources/
  static/index.html   single-page UI (language picker, YouTube IFrame, HUD)
  static/app.js       YouTube IFrame API integration + subtitle sync loop
  static/style.css    sci-fi / game theme (Orbitron font, neon glow, scanlines)
  application.properties

scripts/
  get_transcript.py   called by fetchViaYtdlp(); fetches raw ASR fragments via youtube-transcript-api
  punctuate.py        called by addPunctuation(); restores punctuation using deepmultilingualpunctuation
```

### Debug endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/debug/subtitles?url=VIDEO_URL&n=10` | Fetch source transcript (no translation), return first N entries |
| `GET /api/debug/transcript?url=VIDEO_URL` | Run full diagnostic: InnerTube status, HTML scraping result, cookie count |
| `GET /api/poc/tlang?videoId=ID` | Test YouTube's tlang parameter for each language (expected: all 429) |
