---
name: logs
description: >
  Reads and analyses logs from the DualSub Spring Boot application running as a
  systemd service on WSL2. Use this skill proactively and immediately whenever
  the user reports a bug, an unexpected behaviour, a slow response, a blank
  screen, an error message, or asks "why did X happen / why doesn't Y work".
  Don't wait to be asked — fetching the logs is almost always the right first
  move when something seems wrong. Also use it to confirm that a deployment
  went well or that a feature is being called as expected.
---

## Context

- **Service name**: `dualsub`
- **Log backend**: systemd journald (no separate log file)
- **How to run commands**: via PowerShell using `wsl -u root -e bash -c "..."`
- **Working directory**: `/home/dev/youtube-dual-sub`

All log lines come from a Spring Boot 3.2 app. Application-level prints use
prefixes like `[Stream]`, `[Summary]`, `[Ollama]`, `[Gemini]`, `[Whisper]`,
`[History]`, `[TranslationCache]`, `[SSL]`. Spring's own lines start with a
timestamp and log level (`INFO`, `WARN`, `ERROR`).

---

## Command recipes

### 1 — Recent lines (default starting point)

```powershell
wsl -u root -e bash -c "journalctl -u dualsub -n 100 --no-pager"
```

Use 100 lines for a quick look. Raise to 300–500 when tracing a full request
cycle (transcript → translation → Ollama → response).

### 2 — Since a given time

```powershell
wsl -u root -e bash -c "journalctl -u dualsub --since '09:30' --no-pager"
```

Accepts `HH:MM`, `HH:MM:SS`, `YYYY-MM-DD HH:MM:SS`, or relative values like
`'10 minutes ago'`. Useful when the user says "I just tried X".

### 3 — Search for a pattern

```powershell
wsl -u root -e bash -c "journalctl -u dualsub -n 500 --no-pager | grep -i 'PATTERN'"
```

Common patterns to search for:

| What you want | Pattern |
|---|---|
| All errors | `ERROR\|Exception\|failed\|apierror` |
| Gemini calls & results | `Gemini\|gemini` |
| Ollama calls & results | `Ollama\|ollama` |
| Summary generation | `\[Summary\]` |
| A specific video | the videoId, e.g. `kAyJokPEDyA` |
| Translation cache hits/misses | `TranslationCache` |
| Whisper fallback | `Whisper\|whisper` |
| HTTP 4xx / 5xx | `HTTP [45][0-9][0-9]` |
| Slow requests | `[0-9][0-9][0-9][0-9]ms` |

### 4 — Errors only (systemd priority filter)

```powershell
wsl -u root -e bash -c "journalctl -u dualsub -p err..crit --no-pager -n 50"
```

### 5 — Follow live (for watching a request in real time)

```powershell
# Run in background; not interactive — use sparingly
wsl -u root -e bash -c "journalctl -u dualsub -f --no-pager -n 0" 
```

Because PowerShell doesn't stream well interactively, prefer fetching a
snapshot with `-n` after the action completes rather than following live.

### 6 — Combine time + pattern

```powershell
wsl -u root -e bash -c "journalctl -u dualsub --since '10 minutes ago' --no-pager | grep -iE 'Summary|Gemini|Ollama|ERROR'"
```

---

## How to read a request cycle

A successful video analysis looks like this in the logs:

```
[Stream] Processing: <videoId> | lang1=auto lang2=fr
[Stream] Transcript cache HIT: <videoId> (283 entries)   ← or MISS + pipeline steps
[TranslationCache] HIT: <videoId>/fr (283 entries)        ← or MISS → Google Translate
[Ollama] Refinement skipped — both tracks already cached  ← if cached
[History] Recorded watch: <videoId> ...
[Stream] Done: 283 [auto→de] / 283 [fr] (cached)
```

A summary generation looks like:

```
[Summary] lengthPct=25% → transcript=3241 words → target=810 words
[Summary] Ollama call: transcript=18234 chars, lang=fr, model=mistral-nemo
[Summary] Ollama returned 612 chars
```

or for Gemini:

```
[Summary] Gemini call: transcript=18234 chars, lang=fr, model=gemini-2.0-flash
[Summary] Generation failed: Gemini HTTP 429: ...   ← quota issue
```

---

## Diagnosis workflow

1. **Fetch recent logs** — start with recipe 1 (last 100 lines).
2. **Spot the request** — find the `[Stream] Processing` line that corresponds
   to what the user was doing. Use recipe 3 with the videoId if known.
3. **Trace the outcome** — follow the lines from that request forward:
   cache hits/misses, translation calls, Ollama/Gemini steps, final Done or error.
4. **If there's an error** — look for `ERROR`, `Exception`, `failed`,
   `apierror`, or an HTTP 4xx/5xx in the relevant window.
5. **Report findings** — summarise what happened, what failed, and why,
   in plain language for the user.

When a request is missing from the logs entirely, the issue is likely
client-side (SSE connection dropped before the server processed it, or the
browser cached the static assets and the JS didn't call the endpoint).
