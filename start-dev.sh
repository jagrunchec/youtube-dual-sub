#!/usr/bin/env bash
# ─────────────────────────────────────────────────────────────────────────────
# start-dev.sh — Lance l'environnement DualSub complet dans tmux
#
# Usage:
#   ./start-dev.sh            # démarre Spring Boot + cloudflared + Claude Code
#   ./start-dev.sh --build    # rebuild le JAR avant de démarrer
#   ./start-dev.sh --no-claude # sans Claude Code (2 panneaux seulement)
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

# ── 2. Tuer la session existante si elle tourne déjà ─────────────────────
tmux kill-session -t "$SESSION" 2>/dev/null || true

# ── 3. Créer la session tmux (détachée) ──────────────────────────────────
tmux new-session -d -s "$SESSION" -x "$(tput cols)" -y "$(tput lines)"

# Layout :
#  ┌─────────────────────────┬─────────────────────────┐
#  │  Spring Boot (0)        │  Claude Code (2)        │
#  ├─────────────────────────┴─────────────────────────┤
#  │  cloudflared (1)                                  │
#  └────────────────────────────────────────────────────┘

# Pane 0 (haut-gauche) : Spring Boot
tmux send-keys -t "$SESSION:0" \
  "cd '$PROJECT' && java -jar '$JAR' --spring.profiles.active=linux" Enter

# Diviser en bas → pane 1 : cloudflared
tmux split-window -v -t "$SESSION:0" -l "35%"
tmux send-keys -t "$SESSION:0.1" \
  "sleep 6 && cloudflared tunnel --url http://localhost:8080 2>&1 | \
   tee /tmp/cf.log | grep --line-buffered 'trycloudflare.com' | \
   awk '{print \"\\n>>> URL PUBLIQUE : \" \$NF \"\\n\"}'" Enter

# Diviser le haut en droite → pane 2 : Claude Code (optionnel)
if $WITH_CLAUDE; then
  tmux split-window -h -t "$SESSION:0.0"
  tmux send-keys -t "$SESSION:0.2" \
    "sleep 8 && cd '$PROJECT' && claude" Enter
fi

# Sélectionner le pane Spring Boot au démarrage
tmux select-pane -t "$SESSION:0.0"

# ── 4. S'attacher à la session ───────────────────────────────────────────
echo ""
echo ">>> Session tmux '${SESSION}' démarrée."
echo ">>> Raccourcis : Ctrl+b \" (split bas) | Ctrl+b % (split droite) | Ctrl+b flèches (navigation)"
echo ">>> Pour quitter sans arrêter : Ctrl+b d   |   Pour revenir : tmux attach -t ${SESSION}"
echo ""
tmux attach -t "$SESSION"
