# wake-restart.ps1
# Called by Windows Task Scheduler on system resume from sleep.
# Restarts the DualSub systemd services in WSL2 so the Cloudflare
# tunnel reconnects promptly after the network stack is restored.

Start-Sleep -Seconds 10   # give WSL2 and the network time to fully wake

wsl -u root -e bash -c "systemctl restart cloudflared-dualsub"
wsl -u root -e bash -c "systemctl restart dualsub"
