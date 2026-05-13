#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# start-dev.sh — Lance l'environnement DualSub complet dans tmux
#
# Usage:
#   ./start-dev.sh              # Spring Boot + cloudflared + Claude Code
#   ./start-dev.sh --build      # rebuild le JAR avant de démarrer
#   ./start-dev.sh --no-claude  # sans le panneau Claude Code
# ─────────────────────────────────────────────────────────────────────────────
set -e

SESSION="dualsub"
PROJECT="$HOME/youtube-dual-sub"
JAR="$PROJECT/target/youtube-dual-sub-1.0.0.jar"
BUILD=false
WITH_CLAUDE=true

for arg in "$@"; do
  case $arg in
    --build)     BUILD=true ;;
    --no-claude) WITH_CLAUDE=false ;;
  esac
done

# ── 1. Build si demandé ou si le JAR est absent ───────────────────────────
if $BUILD || [ ! -f "$JAR" ]; then
  echo ">>> Build en cours..."
  cd "$PROJECT"
  mvn clean package -DskipTests -q
  echo ">>> Build OK"
fi

# ── 2. Nettoyer les processus résiduels ──────────────────────────────────
tmux kill-session -t "$SESSION" 2>/dev/null || true
rm -f /tmp/cf.log

# ── 3. Script cloudflared : tunnel permanent ─────────────────────────────
CF_SCRIPT=$(cat <<'CFEOF'
echo ""
echo "╔══════════════════════════════════════════════════════╗"
echo "║  URL PUBLIQUE : https://app.neoventrix.uk            ║"
echo "╚══════════════════════════════════════════════════════╝"
echo ""
cloudflared tunnel run dualsub
CFEOF
)

# ── 4. Créer la session tmux ─────────────────────────────────────────────
tmux new-session -d -s "$SESSION" -x "$(tput cols 2>/dev/null || echo 200)" -y "$(tput lines 2>/dev/null || echo 50)"

# Pane 0 (haut) : Spring Boot
tmux send-keys -t "$SESSION:0" \
  "cd '$PROJECT' && java -jar '$JAR' --spring.profiles.active=linux" Enter

# Diviser en bas (65/35) → pane 1 : cloudflared
tmux split-window -v -t "$SESSION:0" -l "35%"
tmux send-keys -t "$SESSION:0.1" "sleep 7 && bash -c $(printf '%q' "$CF_SCRIPT")" Enter

# Diviser le haut en droite → pane 2 : Claude Code
if $WITH_CLAUDE; then
  tmux split-window -h -t "$SESSION:0.0"
  tmux send-keys -t "$SESSION:0.2" "sleep 10 && cd '$PROJECT' && claude" Enter
fi

# Focus sur Spring Boot
tmux select-pane -t "$SESSION:0.0"

echo ""
echo ">>> Session '${SESSION}' démarrée."
echo ">>> Ctrl+b + flèches : naviguer  |  Ctrl+b d : détacher  |  tmux attach -t ${SESSION} : revenir"
echo ""
tmux attach -t "$SESSION"
