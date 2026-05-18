---
name: status
description: >
  Check the live health of the DualSub server running in WSL2. Use this skill
  whenever the user asks "est-ce que le serveur tourne ?", "quel est l'état du
  serveur ?", "le site est accessible ?", "status", "is the server up?",
  "vérifie que tout fonctionne", or after a deploy to confirm everything is OK.
  Also use proactively after any restart or when diagnosing an outage.
---

# DualSub Server Status Check

## What to check

Run all checks in parallel where possible, then present a clear summary.

### 1 — Systemd services

```powershell
wsl -u root -e bash -c "systemctl is-active dualsub cloudflared-dualsub"
```

Expected: two lines both saying `active`.

For more detail (last log lines):
```powershell
wsl -u root -e bash -c "systemctl status dualsub --no-pager -l | tail -5"
wsl -u root -e bash -c "systemctl status cloudflared-dualsub --no-pager -l | tail -5"
```

### 2 — Port 8080 responding

```powershell
wsl -e bash -c "curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/actuator/health 2>/dev/null || curl -s -o /dev/null -w '%{http_code}' http://localhost:8080/ 2>/dev/null"
```

Expected: `200` or `302` (redirect to login).

### 3 — Cloudflare tunnel connections

```powershell
wsl -u root -e bash -c "journalctl -u cloudflared-dualsub --no-pager -n 20 | grep -E 'Registered|ERR|failed|reconnect'"
```

Expected: 4 `Registered tunnel connection` lines (connIndex 0–3), no recent ERR.

### 4 — Recent Spring Boot errors

```powershell
wsl -u root -e bash -c "journalctl -u dualsub --no-pager -n 30 | grep -E 'ERROR|WARN|Exception' | tail -5"
```

## Output format

Present a simple dashboard:

```
✅ dualsub          — active (started Xm ago)
✅ cloudflared      — active, 4 tunnel connections
✅ Port 8080        — HTTP 200
⚠️  Erreurs récentes — [details if any]
```

Use ✅ / ⚠️ / ❌ based on findings.

## If something is wrong

| Problem | Fix |
|---|---|
| `dualsub` inactive | `wsl -u root -e bash -c "systemctl restart dualsub"` |
| `cloudflared` inactive | `wsl -u root -e bash -c "systemctl restart cloudflared-dualsub"` |
| Port 8080 not responding | Check `journalctl -u dualsub -n 50` for startup errors |
| 0 tunnel connections | Restart cloudflared, check network |
