# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this project does

DualSub is a local web application that plays a YouTube video and shows two simultaneous subtitle tracks underneath it, each translated into a different language chosen by the user. The user picks a video URL and two target languages (FR, EN, ES, IT, DE, PL); the backend fetches the transcript, restores punctuation, splits into sentences, then streams progress and translations back to the browser via SSE before the video starts.

## Build and run

Maven is not on the system PATH. Use the local installation:

```
# Build (skip tests — there are none)
C:\Users\User\maven\apache-maven-3.9.9\bin\mvn.cmd clean package -DskipTests

# Run the JAR (must be launched from the project root so the scripts/ directory is found)
java -jar target\youtube-dual-sub-1.0.0.jar
```

The app is served at `http://localhost:8080`.

Kill the running server with `taskkill /F /IM java.exe` before rebuilding. Wait ~2 s after killing before running `clean` — Maven's clean will fail if the JAR is still locked by the Java process.

## Python dependencies

Two Python scripts are invoked as subprocesses. Both libraries must be installed for the exact executable configured in `application.properties`:

```
C:\Python314\python.exe -m pip install youtube-transcript-api
C:\Python314\python.exe -m pip install deepmultilingualpunctuation
C:\Python314\python.exe -m pip install transformers
```

`deepmultilingualpunctuation` downloads the `oliverguhr/fullstop-punctuation-multilang-large` model (~680 MB) from Hugging Face on the first run. Subsequent runs use the local cache (`~/.cache/huggingface`). If the library is not installed, the punctuation step is silently skipped.

`app.transcript.python` must be an **absolute path** — `ProcessBuilder` does not inherit the full shell PATH, so `python` alone resolves to the Windows Store stub.

`deepmultilingualpunctuation` v1.0.1 is incompatible with `transformers >= 5.x` (removed `grouped_entities` argument). `punctuate.py` works around this by calling the Hugging Face pipeline directly (`pipeline("ner", ..., aggregation_strategy=None)`) instead of using the library's wrapper class.

## Architecture

### Request flow (SSE)

```
Browser → GET /api/process/stream?videoUrl=…&lang1=…&lang2=…
            │   (EventSource — GET only, params in query string)
            │
            ├─ [SSE event: "progress" step=transcript]
            │
            └─ YouTubeTranscriptService.fetchTranscriptFull(videoId, progress)
                  └─ fetchViaYtdlp()              ← subprocess: scripts/get_transcript.py
                        ├─ mergeSubtitles()        ← merge ASR fragments → ~20-word phrases
                        ├─ [SSE event: "progress" step=punctuation]
                        ├─ addPunctuation()        ← subprocess: scripts/punctuate.py
                        ├─ [SSE event: "progress" step=sentences]
                        └─ splitAtSentences()      ← re-segment at sentence boundaries
                  └─ returns TranscriptResult { languageCode, entries }

            ├─ [SSE event: "progress" step=translation1]
            │     lang1="auto" (immersion mode) → serve source entries as-is, no network call
            │     lang1=code                   → TranslationService.translate(entries, lang1)
            │
            ├─ [SSE event: "progress" step=translation2]
            │     TranslationService.translate(entries, lang2)
            │
            └─ [SSE event: "complete" data=ProcessResponse JSON]
```

Both translation tracks receive the **same** sentence-aligned source entries and share their timing exactly. The browser sync loop (`app.js`) uses those shared timestamps for both tracks simultaneously.

### Immersion mode

When the user checks the "Mode Immersion" checkbox, the browser sends `lang1=auto` and `lang2=<uiLang>` (browser's detected language). The server skips translation for track 1 entirely and returns the source entries unchanged. The detected language code (`TranscriptResult.languageCode`) is used to label the track badge. `languageCode` may be `null` when the HTTP fallback was used (in that case the label falls back to `"Source"`).

### SSE event types

| Event name | Meaning |
|---|---|
| `progress` | `{"step": "transcript\|punctuation\|sentences\|translation1\|translation2"}` |
| `complete`  | Full `ProcessResponse` JSON (videoId, subtitles1, subtitles2, lang1Label, lang2Label) |
| `apierror`  | `{"error": "…"}` — pipeline failure; named `apierror` (not `error`) to avoid collision with the browser's built-in SSE connection-error event |

The browser sets a `sseHandled` flag on `complete` or `apierror` before closing the `EventSource`, so `sse.onerror` can distinguish a normal server close from a true connection failure.

### Why YouTube's tlang parameter is not used

YouTube's `tlang` parameter returns HTTP 429 for all server-side requests regardless of cookies, delays, or HTTP version. Direct HTTP fetches of the timedtext endpoint return `200 OK` with an empty body (bot-detection silent-block via TLS fingerprinting). The Python `youtube-transcript-api` library bypasses this by using the Android VR InnerTube client.

### Key design decisions

- **`get_transcript.py` output format**: outputs `{"language": "xx", "entries": [...]}`. The `language` field is the BCP-47 code detected by `youtube-transcript-api`. A plain JSON array is accepted as a legacy fallback.

- **Subtitle merging** (`mergeSubtitles`): YouTube ASR produces 3–7 word fragments. `TARGET_CHARS=160` (~20 words), `GAP_MAX=1500 ms`, `GAP_BREAK=2500 ms`. Merge stops at strong punctuation, long silences, or when the combined text would exceed 160 chars.

- **Punctuation restoration** (`addPunctuation` → `punctuate.py`): All merged entry texts are concatenated into one stream, fed to `oliverguhr/fullstop-punctuation-multilang-large` (XLM-RoBERTa, sentencepiece tokenisation), and the punctuated words are redistributed back to the original entries by word count. The model never adds or removes words. Graceful fallback if the script or library is missing. Supported languages: cs, de, en, es, fr, it, pl, sk — this is why pt, nl, and ru are excluded from the app's target languages.

- **Sentence re-segmentation** (`splitAtSentences`): After punctuation, flattens entries to a per-word list with proportionally interpolated timestamps, then reassembles at `.` `!` `?` boundaries (MAX_CHARS=200 safety valve). A post-pass pins every entry's `durationMs` to `nextEntry.startMs − thisEntry.startMs` — both trimming overshoots and extending undershoots — so intervals are non-overlapping and gapless, which the binary search in `app.js` requires. Minimum duration: 500 ms. First letter of each sentence is capitalised.

- **UTF-8 everywhere**: `ProcessBuilder` sets `PYTHONIOENCODING=utf-8` and `PYTHONUTF8=1`; `TranslationService` reads HTTP responses as `byte[]` and decodes with `StandardCharsets.UTF_8` to avoid Java's ISO-8859-1 default.

- **Google Translate chunking**: subtitles are batched into ≤ 2000-char chunks joined by `\n`, sent to the unofficial `gtx` endpoint, and split back by `\n`. A 400 ms delay between chunks and one automatic retry on 429 prevent rate-limiting.

- **Subtitle sync** (`app.js`): 100 ms `setInterval`. `find()` uses binary search to find the last entry whose `startMs ≤ nowMs`, then checks the duration window. The "last started" strategy is robust against any residual timing edge-cases.

- **I18n**: the UI language is detected at startup from `navigator.languages` (first supported language in the preference list), with fallback to `fr`. All translatable strings live in an `I18N` dictionary in `app.js`, keyed by language code. Supported UI languages: fr, en, es, it, de, pl.

### Source layout

```
src/main/java/com/dualsub/
  controller/VideoController.java          REST endpoints (/api/process/stream, /api/debug/*, /api/poc/tlang)
  service/YouTubeTranscriptService.java    transcript fetch → merge → punctuation → split pipeline
                                           Inner classes: TranscriptResult, DiagInfo
  service/TranslationService.java          Google Translate chunking + retry
  model/                                   SubtitleEntry, ProcessRequest, ProcessResponse

src/main/resources/
  static/index.html        single-page UI (language picker, YouTube IFrame, HUD, immersion checkbox)
  static/app.js            I18n, SSE client, YouTube IFrame API, subtitle sync loop
  static/style.css         sci-fi / game theme (Orbitron font, neon glow, scanlines)
  application.properties

scripts/
  get_transcript.py   fetches raw ASR fragments via youtube-transcript-api; outputs {"language","entries"} JSON
  punctuate.py        restores punctuation via deepmultilingualpunctuation; reads/writes JSON on stdin/stdout
```

### Debug / diagnostic endpoints

| Endpoint | Purpose |
|---|---|
| `GET /api/languages` | Returns the supported language map `{code: label}` |
| `GET /api/debug/subtitles?url=VIDEO_URL&n=10` | Fetch source transcript (no translation), return first N entries |
| `GET /api/debug/transcript?url=VIDEO_URL` | Run full diagnostic: InnerTube status, HTML scraping result, cookie count |
| `GET /api/poc/tlang?videoId=ID` | Test YouTube's tlang parameter for each language (expected: all 429) |

`POST /api/process` still exists as a legacy synchronous endpoint (no SSE, no progress). It is not used by the current frontend.
