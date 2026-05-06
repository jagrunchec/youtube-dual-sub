/* ─── State ─────────────────────────────────────────────────── */
let selectedLang1 = 'fr';
let selectedLang2 = 'de';
let subtitles1    = [];
let subtitles2    = [];
let player        = null;
let syncInterval  = null;

const LANG_LABELS = {
    fr: 'Français', en: 'English', es: 'Español',
    it: 'Italiano', de: 'Deutsch'
};

/* ─── Boot ──────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
    document.getElementById('videoUrl').addEventListener('keydown', e => {
        if (e.key === 'Enter') processVideo();
    });
});

/* ─── Language card selection ───────────────────────────────── */
function pick(row, code, btn) {
    document.getElementById('row' + row)
        .querySelectorAll('.flag-btn')
        .forEach(c => c.classList.remove('selected-green', 'selected-orange'));

    btn.classList.add(row === 1 ? 'selected-green' : 'selected-orange');
    if (row === 1) selectedLang1 = code;
    else           selectedLang2 = code;
}

/* ─── Process video ─────────────────────────────────────────── */
async function processVideo() {
    const url = document.getElementById('videoUrl').value.trim();
    if (!url) { showError('Veuillez saisir une URL YouTube.'); return; }

    setLoading(true);
    hideError();

    try {
        const resp = await fetch('/api/process', {
            method:  'POST',
            headers: { 'Content-Type': 'application/json' },
            body:    JSON.stringify({ videoUrl: url, lang1: selectedLang1, lang2: selectedLang2 })
        });

        const data = await resp.json();

        if (!resp.ok) {
            showError(data.error || 'Erreur lors du traitement de la vidéo.');
            return;
        }

        subtitles1 = data.subtitles1 || [];
        subtitles2 = data.subtitles2 || [];

        // ── Diagnostic console ────────────────────────────────
        console.log(`[DualSub] Réponse reçue :`);
        console.log(`  videoId   : ${data.videoId}`);
        console.log(`  lang1     : ${data.lang1Label} → ${subtitles1.length} sous-titres`);
        console.log(`  lang2     : ${data.lang2Label} → ${subtitles2.length} sous-titres`);
        if (subtitles1.length > 0) {
            console.log(`  1er sous-titre [${data.lang1Label}] :`, subtitles1[0]);
            console.log(`  Der sous-titre [${data.lang1Label}] :`, subtitles1[subtitles1.length - 1]);
        } else {
            console.warn(`  ⚠ subtitles1 est VIDE`);
        }
        if (subtitles2.length > 0) {
            console.log(`  1er sous-titre [${data.lang2Label}] :`, subtitles2[0]);
        } else {
            console.warn(`  ⚠ subtitles2 est VIDE`);
        }
        // ────────────────────────────────────────────────────

        const label1 = data.lang1Label || LANG_LABELS[selectedLang1] || selectedLang1.toUpperCase();
        const label2 = data.lang2Label || LANG_LABELS[selectedLang2] || selectedLang2.toUpperCase();

        document.getElementById('lang1Badge').textContent = label1;
        document.getElementById('lang2Badge').textContent = label2;
        document.getElementById('hudStatus').textContent =
            `${subtitles1.length} lignes ${label1} · ${subtitles2.length} lignes ${label2}`;

        showPlayer(data.videoId);

    } catch (err) {
        console.error('[DualSub] Erreur fetch :', err);
        showError('Impossible de joindre le serveur. Vérifiez que Spring Boot tourne sur le port 8080.');
    } finally {
        setLoading(false);
    }
}

/* ─── YouTube Player ────────────────────────────────────────── */
function showPlayer(videoId) {
    document.getElementById('configPanel').classList.add('hidden');
    document.getElementById('playerSection').classList.remove('hidden');

    // Trier pour garantir la recherche binaire
    subtitles1.sort((a, b) => a.startMs - b.startMs);
    subtitles2.sort((a, b) => a.startMs - b.startMs);

    // Démarrage immédiat de la synchro
    startSync();

    if (player) {
        player.loadVideoById(videoId);
        return;
    }

    player = new YT.Player('youtubePlayer', {
        height: '506',
        width:  '900',
        videoId,
        playerVars: { playsinline: 1, rel: 0, modestbranding: 1 },
        events: {
            onReady:       () => { console.log('[DualSub] Lecteur YouTube prêt'); },
            onStateChange: onPlayerStateChange,
        }
    });
}

function onPlayerStateChange(ev) {
    const states = { '-1':'UNSTARTED', 0:'ENDED', 1:'PLAYING', 2:'PAUSED', 3:'BUFFERING', 5:'CUED' };
    console.log('[DualSub] État lecteur :', states[ev.data] || ev.data);

    if (ev.data === YT.PlayerState.ENDED) {
        stopSync();
    } else if (!syncInterval) {
        // Relancer si la synchro a été stoppée (ne devrait pas arriver)
        startSync();
    }
}

function startSync() {
    stopSync();
    syncInterval = setInterval(syncSubtitles, 100);
    console.log('[DualSub] Synchro démarrée');
}
function stopSync() {
    if (syncInterval) {
        clearInterval(syncInterval);
        syncInterval = null;
    }
}

/* ─── Subtitle sync ─────────────────────────────────────────── */
function syncSubtitles() {
    if (!player || typeof player.getCurrentTime !== 'function') return;

    const nowMs = player.getCurrentTime() * 1000;

    const t1 = find(subtitles1, nowMs);
    const t2 = find(subtitles2, nowMs);

    document.getElementById('subtitleText1').textContent = t1;
    document.getElementById('subtitleText2').textContent = t2;

    // Afficher l'heure courante dans le debug
    document.getElementById('hudTime').textContent = formatMs(nowMs);
}

/* Recherche binaire dans un tableau trié par startMs */
function find(list, nowMs) {
    if (!list || list.length === 0) return '';

    let lo = 0, hi = list.length - 1;
    while (lo <= hi) {
        const mid = (lo + hi) >> 1;
        const s   = list[mid];
        if      (nowMs < s.startMs)                  hi = mid - 1;
        else if (nowMs >= s.startMs + s.durationMs)  lo = mid + 1;
        else                                          return s.text;
    }
    return '';
}

function formatMs(ms) {
    const s = Math.floor(ms / 1000);
    const m = Math.floor(s / 60);
    const h = Math.floor(m / 60);
    return h > 0
        ? `${h}:${String(m % 60).padStart(2,'0')}:${String(s % 60).padStart(2,'0')}`
        : `${m}:${String(s % 60).padStart(2,'0')}`;
}

/* ─── Reset ─────────────────────────────────────────────────── */
function resetApp() {
    stopSync();
    subtitles1 = [];
    subtitles2 = [];
    document.getElementById('subtitleText1').textContent = '';
    document.getElementById('subtitleText2').textContent = '';
    document.getElementById('hudStatus').textContent = '';
    document.getElementById('hudTime').textContent = '';
    document.getElementById('configPanel').classList.remove('hidden');
    document.getElementById('playerSection').classList.add('hidden');
    if (player) player.stopVideo();
}

/* ─── UI helpers ────────────────────────────────────────────── */
function setLoading(on) {
    const btn = document.getElementById('btnProcess');
    btn.disabled = on;
    document.getElementById('btnText').textContent =
        on ? 'TRADUCTION EN COURS…' : 'ANALYSER & TRADUIRE';
    document.getElementById('btnSpinner').classList.toggle('hidden', !on);
}

function showError(msg) {
    const box = document.getElementById('errorBox');
    box.textContent = msg;
    box.classList.remove('hidden');
}
function hideError() {
    document.getElementById('errorBox').classList.add('hidden');
}

function onYouTubeIframeAPIReady() {}
