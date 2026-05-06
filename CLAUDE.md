# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project does

DualSub is a local web application that plays a YouTube video and shows two simultaneous subtitle tracks underneath it, each translated into a different language chosen by the user. The user picks a video URL and two languages (FR, EN, ES, IT, DE); the backend fetches the transcript, restores punctuation, and runs both translations in parallel before the video starts.

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

`deepmultilingualpunctuation` downloads the `oliverguhr/fullstop-punctuation-multilang-large` model (~680 MB) from Hugging Face on the first run. Subsequent runs use the local cache (`~/.cache/huggingface`). If the library is not installed, the punctuation step is silently skipped and `splitAtSentences()` becomes a no-op (no sentence-boundary splitting without punctuation markers).

`app.transcript.python` must be an **absolute path** — `ProcessBuilder` does not inherit the full shell PATH, so `python` alone resolves to the Windows Store stub.

## Architecture

### Full pipeline (one request)

```
Browser → POST /api/process
            │
            └─ YouTubeTranscriptService.fetchTranscript()
                  └─ fetchViaYtdlp()              ← subprocess: scripts/get_transcript.py
                        ├─ mergeSubtitles()        ← merge ASR fragments → ~20-word phrases
                        ├─ addPunctuation()        ← subprocess: scripts/punctuate.py
                        └─ splitAtSentences()      ← re-segment at sentence boundaries

            ├─ TranslationService.translate(entries, lang1)  ← Google Translate (gtx, no key)
            └─ TranslationService.translate(entries, lang2)
```

Both translations receive the **same** sentence-aligned source entries and preserve their timing exactly. The browser sync loop (`app.js`) uses those shared timestamps for both tracks simultaneously.

### Why YouTube's tlang parameter is not used

YouTube's `tlang` parameter returns HTTP 429 for all server-side requests regardless of cookies, delays, or HTTP version. Direct HTTP fetches of the timedtext endpoint return `200 OK` with an empty body (bot-detection silent-block via TLS fingerprinting). The Python `youtube-transcript-api` library bypasses this by using the Android VR InnerTube client.

### Key design decisions

- **Subtitle merging** (`mergeSubtitles`): YouTube ASR produces 3–7 word fragments. `TARGET_CHARS=160` (~20 words), `GAP_MAX=1500 ms`, `GAP_BREAK=2500 ms`. Merge stops at strong punctuation, long silences, or when the combined text would exceed 160 chars. The 160-char target gives the punctuation model enough context per chunk.

- **Punctuation restoration** (`addPunctuation` → `punctuate.py`): All merged entry texts are concatenated into one stream, fed to `oliverguhr/fullstop-punctuation-multilang-large` (XLM-RoBERTa, sentencepiece tokenisation), and the punctuated words are redistributed back to the original entries by word count. The model never adds or removes words, so the count is stable. Graceful fallback if the script or library is missing.

- **Sentence re-segmentation** (`splitAtSentences`): After punctuation, flattens entries to a per-word list with proportionally interpolated timestamps, then reassembles at `.` `!` `?` boundaries (MAX_CHARS=200 safety valve). A post-pass pins every entry's `durationMs` to `nextEntry.startMs − thisEntry.startMs` — both trimming overshoots **and** extending undershoots — so the resulting intervals are non-overlapping and gapless, which is required for the binary search in `app.js` to work correctly. Minimum duration: 500 ms. First letter of each sentence is capitalised.

- **UTF-8 everywhere**: `ProcessBuilder` sets `PYTHONIOENCODING=utf-8` and `PYTHONUTF8=1`; `TranslationService` reads HTTP responses as `byte[]` and decodes with `StandardCharsets.UTF_8` to avoid Java's ISO-8859-1 default.

- **Google Translate chunking**: subtitles are batched into ≤ 2000-char chunks joined by `\n`, sent to the unofficial `gtx` endpoint, and split back by `\n`. A 400 ms delay between chunks and one automatic retry on 429 prevent rate-limiting.

- **Subtitle sync** (`app.js`): 100 ms `setInterval`. `find()` uses binary search to find the last entry whose `startMs ≤ nowMs`, then checks the duration window. The "last started" strategy is robust against any residual timing edge-cases.

### Source layout

```
src/main/java/com/dualsub/
  controller/VideoController.java          REST endpoints (/api/process, /api/debug/*)
  service/YouTubeTranscriptService.java    transcript fetch → merge → punctuation → split pipeline
  service/TranslationService.java          Google Translate chunking + retry
  model/                                   SubtitleEntry, ProcessRequest, ProcessResponse

src/main/resources/
  static/index.html        single-page UI (language picker, YouTube IFrame, HUD)
  static/app.js            YouTube IFrame API + subtitle sync loop
  static/style.css         sci-fi / game theme (Orbitron font, neon glow, scanlines)
  application.properties

scripts/
  get_transcript.py   fetches raw ASR fragments via youtube-transcript-api; outputs JSON to stdout
  punctuate.py        restores punctuation via deepmultilingualpunctuation; reads/writes JSON on stdin/stdout
```

### Debug endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/debug/subtitles?url=VIDEO_URL&n=10` | Fetch source transcript (no translation), return first N entries |
| `GET /api/debug/transcript?url=VIDEO_URL` | Run full diagnostic: InnerTube status, HTML scraping result, cookie count |
| `GET /api/poc/tlang?videoId=ID` | Test YouTube's tlang parameter for each language (expected: all 429) |
