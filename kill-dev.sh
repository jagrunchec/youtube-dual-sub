#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# kill-dev.sh — Arrête proprement l'environnement DualSub
# ─────────────────────────────────────────────────────────────────────────────

echo ">>> Arrêt de la session tmux 'dualsub'..."
tmux kill-session -t dualsub 2>/dev/null && echo "    tmux OK" || echo "    (session déjà arrêtée)"

echo ">>> Arrêt de Spring Boot (java)..."
pkill -f "youtube-dual-sub.*\.jar" 2>/dev/null && echo "    Spring Boot OK" || echo "    (déjà arrêté)"

echo ">>> Arrêt de cloudflared..."
pkill -f "cloudflared" 2>/dev/null && echo "    cloudflared OK" || echo "    (déjà arrêté)"

echo ">>> Nettoyage des logs temporaires..."
rm -f /tmp/cf.log

echo ""
echo ">>> Environnement arrêté. Relancer avec : ./start-dev.sh"
