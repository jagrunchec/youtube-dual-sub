---
name: deploy
description: >
  Deploy DualSub to WSL2 Ubuntu. Use this skill whenever the user says
  "déploie", "deploy", "mets à jour le serveur", "pousse sur Ubuntu",
  "redémarre le serveur", or after any code change that needs to go live.
  Handles the full pipeline: static asset version bump (if needed),
  git commit/push on Windows, git pull + Maven build + systemd restart in WSL2,
  then confirms startup. Always use this skill rather than running the
  individual commands manually.
---

# Deploy DualSub to WSL2

## Context

- **Windows** is the primary development machine (edits, git commits, pushes).
- **WSL2** (`/home/dev/youtube-dual-sub/`) runs the live server via two systemd services:
  - `dualsub` — Spring Boot JAR on port 8080
  - `cloudflared-dualsub` — Cloudflare named tunnel → `app.neoventrix.uk`
- Static files (`style.css`, `app.js`) are embedded in the JAR, so a rebuild is always needed after any change.

## Step 1 — Bump static asset version (if static files changed)

If `src/main/resources/static/style.css` or `app.js` were modified in this session, increment the `?v=N` cache-busting parameter in `index.html` **before committing**.

Find the current versions:
```powershell
Select-String "style\.css\?v=|app\.js\?v=" C:\Users\User\youtube-dual-sub\src\main\resources\static\index.html
```

Then use Edit tool to increment the version number(s) by 1.

## Step 2 — Commit & push from Windows

Stage only the relevant files (never use `git add -A` blindly):
```powershell
cd C:\Users\User\youtube-dual-sub
git add <changed files>
git commit -m "descriptive message`n`nCo-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
git push
```

## Step 3 — Pull & build in WSL2

```powershell
wsl -u root -e bash -c "cd /home/dev/youtube-dual-sub && git pull && mvn clean package -DskipTests -q && echo BUILD_OK"
```

Timeout: 120 seconds. If BUILD_OK is printed, proceed.

## Step 4 — Restart Spring Boot

```powershell
wsl -u root -e bash -c "systemctl restart dualsub && echo RESTART_OK"
```

## Step 5 — Verify startup

Wait 12 seconds then check the journal:
```powershell
Start-Sleep -Seconds 12
wsl -u root -e bash -c "systemctl status dualsub --no-pager | tail -3"
```

Look for: `Started DualSubApplication in X seconds`

## Step 6 — Report to user

Summarise what was deployed and confirm the site is live at `https://app.neoventrix.uk`.

## Notes

- **Do not restart `cloudflared-dualsub`** unless the tunnel is broken — it stays connected across Spring Boot restarts.
- If the build fails, show the Maven error output and stop — do not restart the service with a broken JAR.
- If only static files (CSS/JS/HTML) changed, the rebuild is still required because they are embedded in the fat JAR.
