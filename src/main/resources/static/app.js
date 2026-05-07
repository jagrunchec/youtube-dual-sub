/* ─── State ─────────────────────────────────────────────────── */
let selectedLang1   = 'fr';
let selectedLang2   = 'de';
let subtitles1      = [];
let subtitles2      = [];
let player          = null;
let syncInterval    = null;
let immersionMode   = false;
let progressTimer   = null;   // setInterval handle for the per-step elapsed timer
let stepElapsed     = 0;      // seconds elapsed since the current step started
let historyExpanded = true;   // whether the history panel is expanded

const LANG_LABELS = {
    fr: 'Français', en: 'English', es: 'Español',
    it: 'Italiano', de: 'Deutsch', pl: 'Polski',
};

/* ─── Internationalisation ───────────────────────────────────── */
const I18N = {
    fr: {
        title:          'DualSub — Double Traduction YouTube',
        tagline:        '// TRADUCTION SIMULTANÉE  ·  DOUBLE PISTE EN TEMPS RÉEL',
        labelTarget:    '// CIBLE',
        labelLangs:     '// LANGUES',
        slotPrimary:    'LANGUE PRINCIPALE',
        slotSecondary:  'LANGUE SECONDAIRE',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALYSER & TRADUIRE',
        btnLoading:     'TRADUCTION EN COURS…',
        btnBack:        '◄  RETOUR',
        errNoUrl:       'Veuillez saisir une URL YouTube.',
        errServer:      'Impossible de joindre le serveur. Vérifiez que Spring Boot tourne sur le port 8080.',
        stepTranscript: 'Récupération du transcript',
        stepPunctuation:'Restauration de la ponctuation',
        stepSentences:  'Découpage en phrases',
        stepTranslation:'Traduction',
        stepSourceAuto: 'Piste 1 : langue source',
        stepCached:     'Transcript en cache ⚡',
        immLabel:       'MODE IMMERSION',
        immHint:        'langue de la vidéo · ma langue',
        labelHistory:   '// HISTORIQUE',
        histEmpty:      'Aucune vidéo regardée',
        hudStatus:      (n1, l1, n2, l2) => `${n1} lignes ${l1} · ${n2} lignes ${l2}`,
    },
    en: {
        title:          'DualSub — Dual YouTube Translation',
        tagline:        '// SIMULTANEOUS TRANSLATION  ·  DUAL TRACK IN REAL TIME',
        labelTarget:    '// TARGET',
        labelLangs:     '// LANGUAGES',
        slotPrimary:    'PRIMARY LANGUAGE',
        slotSecondary:  'SECONDARY LANGUAGE',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALYZE & TRANSLATE',
        btnLoading:     'TRANSLATING…',
        btnBack:        '◄  BACK',
        errNoUrl:       'Please enter a YouTube URL.',
        errServer:      'Cannot reach the server. Make sure Spring Boot is running on port 8080.',
        stepTranscript: 'Fetching transcript',
        stepPunctuation:'Restoring punctuation',
        stepSentences:  'Splitting into sentences',
        stepTranslation:'Translation',
        stepSourceAuto: 'Track 1: source language',
        stepCached:     'Transcript from cache ⚡',
        immLabel:       'IMMERSION MODE',
        immHint:        'video language · my language',
        labelHistory:   '// HISTORY',
        histEmpty:      'No videos watched yet',
        hudStatus:      (n1, l1, n2, l2) => `${n1} lines ${l1} · ${n2} lines ${l2}`,
    },
    es: {
        title:          'DualSub — Doble Traducción de YouTube',
        tagline:        '// TRADUCCIÓN SIMULTÁNEA  ·  DOBLE PISTA EN TIEMPO REAL',
        labelTarget:    '// OBJETIVO',
        labelLangs:     '// IDIOMAS',
        slotPrimary:    'IDIOMA PRINCIPAL',
        slotSecondary:  'IDIOMA SECUNDARIO',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALIZAR Y TRADUCIR',
        btnLoading:     'TRADUCIENDO…',
        btnBack:        '◄  VOLVER',
        errNoUrl:       'Por favor, introduce una URL de YouTube.',
        errServer:      'No se puede conectar al servidor. Verifica que Spring Boot esté en el puerto 8080.',
        stepTranscript: 'Obteniendo transcripción',
        stepPunctuation:'Restaurando puntuación',
        stepSentences:  'Dividiendo en frases',
        stepTranslation:'Traducción',
        stepSourceAuto: 'Pista 1: idioma fuente',
        stepCached:     'Transcripción en caché ⚡',
        immLabel:       'MODO INMERSIÓN',
        immHint:        'idioma del vídeo · mi idioma',
        labelHistory:   '// HISTORIAL',
        histEmpty:      'No hay vídeos vistos aún',
        hudStatus:      (n1, l1, n2, l2) => `${n1} líneas ${l1} · ${n2} líneas ${l2}`,
    },
    it: {
        title:          'DualSub — Doppia Traduzione YouTube',
        tagline:        '// TRADUZIONE SIMULTANEA  ·  DOPPIA TRACCIA IN TEMPO REALE',
        labelTarget:    '// DESTINAZIONE',
        labelLangs:     '// LINGUE',
        slotPrimary:    'LINGUA PRINCIPALE',
        slotSecondary:  'LINGUA SECONDARIA',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALIZZA E TRADUCI',
        btnLoading:     'TRADUZIONE IN CORSO…',
        btnBack:        '◄  INDIETRO',
        errNoUrl:       'Inserisci un URL di YouTube.',
        errServer:      'Impossibile raggiungere il server. Verifica che Spring Boot sia sulla porta 8080.',
        stepTranscript: 'Recupero trascrizione',
        stepPunctuation:'Ripristino punteggiatura',
        stepSentences:  'Suddivisione in frasi',
        stepTranslation:'Traduzione',
        stepSourceAuto: 'Traccia 1: lingua del video',
        stepCached:     'Trascrizione dalla cache ⚡',
        immLabel:       'MODALITÀ IMMERSIONE',
        immHint:        'lingua sorgente · la mia lingua',
        labelHistory:   '// CRONOLOGIA',
        histEmpty:      'Nessun video guardato',
        hudStatus:      (n1, l1, n2, l2) => `${n1} righe ${l1} · ${n2} righe ${l2}`,
    },
    de: {
        title:          'DualSub — Doppelte YouTube-Übersetzung',
        tagline:        '// SIMULTANÜBERSETZUNG  ·  DOPPELSPUR IN ECHTZEIT',
        labelTarget:    '// ZIEL',
        labelLangs:     '// SPRACHEN',
        slotPrimary:    'HAUPTSPRACHE',
        slotSecondary:  'NEBENSPRACHE',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALYSIEREN & ÜBERSETZEN',
        btnLoading:     'ÜBERSETZUNG LÄUFT…',
        btnBack:        '◄  ZURÜCK',
        errNoUrl:       'Bitte eine YouTube-URL eingeben.',
        errServer:      'Server nicht erreichbar. Prüfe, ob Spring Boot auf Port 8080 läuft.',
        stepTranscript: 'Transkript abrufen',
        stepPunctuation:'Zeichensetzung wiederherstellen',
        stepSentences:  'In Sätze aufteilen',
        stepTranslation:'Übersetzung',
        stepSourceAuto: 'Spur 1: Sprache des Videos',
        stepCached:     'Transkript aus Cache ⚡',
        immLabel:       'IMMERSIONSMODUS',
        immHint:        'Quellsprache · meine Sprache',
        labelHistory:   '// VERLAUF',
        histEmpty:      'Keine Videos angesehen',
        hudStatus:      (n1, l1, n2, l2) => `${n1} Zeilen ${l1} · ${n2} Zeilen ${l2}`,
    },
    pl: {
        title:          'DualSub — Podwójne tłumaczenie YouTube',
        tagline:        '// TŁUMACZENIE SYMULTANICZNE  ·  PODWÓJNA ŚCIEŻKA W CZASIE RZECZYWISTYM',
        labelTarget:    '// CEL',
        labelLangs:     '// JĘZYKI',
        slotPrimary:    'JĘZYK GŁÓWNY',
        slotSecondary:  'JĘZYK DODATKOWY',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALIZUJ I TŁUMACZ',
        btnLoading:     'TŁUMACZENIE W TOKU…',
        btnBack:        '◄  POWRÓT',
        errNoUrl:       'Proszę podać adres URL YouTube.',
        errServer:      'Nie można połączyć się z serwerem. Sprawdź, czy Spring Boot działa na porcie 8080.',
        stepTranscript: 'Pobieranie transkrypcji',
        stepPunctuation:'Przywracanie interpunkcji',
        stepSentences:  'Podział na zdania',
        stepTranslation:'Tłumaczenie',
        stepSourceAuto: 'Ścieżka 1: język wideo',
        stepCached:     'Transkrypcja z pamięci ⚡',
        immLabel:       'TRYB IMMERSJI',
        immHint:        'język źródłowy · mój język',
        labelHistory:   '// HISTORIA',
        histEmpty:      'Brak obejrzanych filmów',
        hudStatus:      (n1, l1, n2, l2) => `${n1} linii ${l1} · ${n2} linii ${l2}`,
    },
};

function detectLang() {
    const supported = Object.keys(I18N);
    const prefs = (navigator.languages && navigator.languages.length)
        ? navigator.languages : [navigator.language || 'fr'];
    for (const pref of prefs) {
        const code = pref.split('-')[0].toLowerCase();
        if (supported.includes(code)) return code;
    }
    return 'fr';
}

const uiLang = detectLang();

function applyI18n() {
    const t = I18N[uiLang];
    document.title = t.title;
    document.documentElement.lang = uiLang;
    document.getElementById('tagline').textContent        = t.tagline;
    document.getElementById('labelTarget').innerHTML =
        t.labelTarget + '  <span class="blink">_</span>';
    document.getElementById('labelLangs').textContent     = t.labelLangs;
    document.getElementById('slotPrimary').textContent    = t.slotPrimary;
    document.getElementById('slotSecondary').textContent  = t.slotSecondary;
    document.getElementById('videoUrl').placeholder       = t.placeholder;
    document.getElementById('btnText').textContent        = t.btnAnalyze;
    document.getElementById('btnBack').textContent        = t.btnBack;
    document.getElementById('immersionLabel').textContent = t.immLabel;
    document.getElementById('immersionHint').textContent  = t.immHint;
    document.getElementById('labelHistory').textContent   = t.labelHistory;
}

/* ─── Immersion mode ────────────────────────────────────────── */
function toggleImmersion(cb) {
    immersionMode = cb.checked;
    document.getElementById('slot1').classList.toggle('imm-locked', immersionMode);
    document.getElementById('slot2').classList.toggle('imm-locked', immersionMode);
    savePreferences();
}

/* ─── Boot ──────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
    applyI18n();
    document.getElementById('videoUrl').addEventListener('keydown', e => {
        if (e.key === 'Enter') processVideo();
    });
    loadPreferences();
    loadHistory();
});

/* ─── Language card selection ───────────────────────────────── */
function pick(row, code, btn) {
    document.getElementById('row' + row)
        .querySelectorAll('.flag-btn')
        .forEach(c => c.classList.remove('selected-green', 'selected-orange'));

    btn.classList.add(row === 1 ? 'selected-green' : 'selected-orange');
    if (row === 1) selectedLang1 = code;
    else           selectedLang2 = code;
    savePreferences();
}

/* ─── Preferences persistence ───────────────────────────────── */
async function loadPreferences() {
    try {
        const resp = await fetch('/api/preferences');
        if (!resp.ok) return;
        const prefs = await resp.json();

        // Apply saved language selections without re-triggering savePreferences
        if (prefs.lang1) {
            const btn1 = document.querySelector(`#row1 [data-code="${prefs.lang1}"]`);
            if (btn1) {
                document.getElementById('row1')
                    .querySelectorAll('.flag-btn')
                    .forEach(c => c.classList.remove('selected-green', 'selected-orange'));
                btn1.classList.add('selected-green');
                selectedLang1 = prefs.lang1;
            }
        }
        if (prefs.lang2) {
            const btn2 = document.querySelector(`#row2 [data-code="${prefs.lang2}"]`);
            if (btn2) {
                document.getElementById('row2')
                    .querySelectorAll('.flag-btn')
                    .forEach(c => c.classList.remove('selected-green', 'selected-orange'));
                btn2.classList.add('selected-orange');
                selectedLang2 = prefs.lang2;
            }
        }
        if (prefs.immersionMode) {
            const cb = document.getElementById('immersionCheck');
            cb.checked = true;
            immersionMode = true;
            document.getElementById('slot1').classList.add('imm-locked');
            document.getElementById('slot2').classList.add('imm-locked');
        }
    } catch (e) { /* silent — server may not be ready yet */ }
}

async function savePreferences() {
    try {
        await fetch('/api/preferences', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                lang1: selectedLang1,
                lang2: selectedLang2,
                immersionMode,
                uiLang,
            }),
        });
    } catch (e) { /* silent */ }
}

/* ─── History panel ─────────────────────────────────────────── */
async function loadHistory() {
    try {
        const resp = await fetch('/api/history');
        if (!resp.ok) return;
        const items = await resp.json();
        renderHistory(items);
    } catch (e) { /* silent */ }
    // Always returns a resolved promise so callers can chain .then()
}

function renderHistory(items) {
    const panel = document.getElementById('historyPanel');
    const list  = document.getElementById('historyList');
    const count = document.getElementById('histCount');
    const t     = I18N[uiLang];

    if (!items || items.length === 0) {
        panel.classList.add('hidden');
        return;
    }

    count.textContent = items.length;
    panel.classList.remove('hidden');
    list.innerHTML = '';

    items.forEach(item => {
        const date      = new Date(item.watchedAt).toLocaleDateString(uiLang, {
            day: '2-digit', month: 'short', year: 'numeric',
        });
        const title     = item.videoTitle || item.videoId;
        const lang1Lbl  = item.lang1 === 'auto'
            ? '●' : (LANG_LABELS[item.lang1] || item.lang1.toUpperCase());
        const lang2Lbl  = LANG_LABELS[item.lang2] || item.lang2.toUpperCase();

        const el = document.createElement('div');
        el.className = 'hist-item';
        el.innerHTML = `
            <img class="hist-thumb" src="${item.thumbnailUrl || ''}"
                 alt="" loading="lazy"
                 onerror="this.style.display='none'">
            <div class="hist-info">
                <span class="hist-title" title="${escapeHtml(title)}">${escapeHtml(title)}</span>
                <div class="hist-meta">
                    <span class="hist-date">${date}</span>
                    <span class="hist-badge-lang green-badge-lang">${lang1Lbl}</span>
                    <span class="hist-badge-lang orange-badge-lang">${lang2Lbl}</span>
                </div>
            </div>
            <button class="hist-replay-btn" title="Relancer"
                    onclick="replayFromHistory('${item.videoId}','${item.lang1}','${item.lang2}')">
                ▶
            </button>
            <button class="hist-del-btn" title="Supprimer"
                    onclick="deleteHistoryEntry(${item.id}, this)">
                ✕
            </button>`;
        list.appendChild(el);
    });
}

function toggleHistoryPanel() {
    historyExpanded = !historyExpanded;
    document.getElementById('historyList').style.display =
        historyExpanded ? '' : 'none';
    document.getElementById('histChevron').textContent =
        historyExpanded ? '▼' : '▶';
}

async function deleteHistoryEntry(id, btn) {
    try {
        await fetch(`/api/history/${id}`, { method: 'DELETE' });
        // Remove the item from the DOM
        const item = btn.closest('.hist-item');
        if (item) item.remove();
        // If no items left, hide the panel
        if (!document.querySelector('.hist-item')) {
            document.getElementById('historyPanel').classList.add('hidden');
        }
        // Update count badge
        const remaining = document.querySelectorAll('.hist-item').length;
        document.getElementById('histCount').textContent = remaining || '';
    } catch (e) { /* silent */ }
}

function replayFromHistory(videoId, lang1, lang2) {
    document.getElementById('videoUrl').value =
        `https://www.youtube.com/watch?v=${videoId}`;

    // Select lang1
    const btn1 = document.querySelector(`#row1 [data-code="${lang1}"]`);
    if (btn1) pick(1, lang1, btn1);

    // Select lang2
    const btn2 = document.querySelector(`#row2 [data-code="${lang2}"]`);
    if (btn2) pick(2, lang2, btn2);

    // Uncheck immersion if needed
    if (immersionMode && lang1 !== 'auto') {
        const cb = document.getElementById('immersionCheck');
        cb.checked = false;
        toggleImmersion(cb);
    }

    window.scrollTo({ top: 0, behavior: 'smooth' });
    processVideo();
}

function escapeHtml(str) {
    return String(str)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;');
}

/* ─── Pipeline steps (for SSE progress display) ─────────────── */
const PIPELINE_STEPS = [
    { key: 'transcript',   label: '', color: '#00d4ff' },   // cyan
    { key: 'punctuation',  label: '', color: '#a855f7' },   // purple
    { key: 'sentences',    label: '', color: '#ff7b00' },   // orange
    { key: 'translation1', label: '', color: '#00ff88' },   // green
    { key: 'translation2', label: '', color: '#00ff88' },   // green
];

function initProgress(lang1Label, lang2Label) {
    const t = I18N[uiLang];
    PIPELINE_STEPS[0].label = t.stepTranscript;
    PIPELINE_STEPS[1].label = t.stepPunctuation;
    PIPELINE_STEPS[2].label = t.stepSentences;
    PIPELINE_STEPS[3].label = immersionMode ? t.stepSourceAuto : t.stepTranslation + ' ' + lang1Label;
    PIPELINE_STEPS[4].label = t.stepTranslation + ' ' + lang2Label;

    const panel = document.getElementById('progressPanel');
    panel.innerHTML = `
        <div class="prog-row">
            <span class="prog-spin"></span>
            <span class="prog-label" id="progLabel">…</span>
            <span class="prog-timer" id="progTimer">0 s</span>
        </div>
        <div class="prog-bar-track">
            <div class="prog-bar-fill" id="progBar"></div>
        </div>`;
    panel.classList.remove('hidden');
}

function updateStep(stepKey, cached) {
    const keys  = PIPELINE_STEPS.map(s => s.key);
    const idx   = keys.indexOf(stepKey);
    const step  = PIPELINE_STEPS[idx];
    if (!step) return;

    // Cache hit: gold colour.
    // — Transcript: generic "from cache" label.
    // — Translations: keep the language label + ⚡ suffix (e.g. "Traduction Français ⚡").
    const color = cached ? '#ffd700' : step.color;
    const label = cached
        ? (stepKey === 'transcript' ? I18N[uiLang].stepCached : step.label + ' ⚡')
        : step.label;

    // Use the translation1 position (idx=3) for the bar when cached
    const barIdx = cached ? 3 : idx;
    const pct    = Math.round(((barIdx + 1) / (PIPELINE_STEPS.length + 1)) * 100);

    document.getElementById('progressPanel').style.setProperty('--prog-color', color);

    const labelEl = document.getElementById('progLabel');
    if (labelEl) labelEl.textContent = label;

    const barEl = document.getElementById('progBar');
    if (barEl) barEl.style.width = pct + '%';

    // Reset and restart the per-step elapsed timer
    clearInterval(progressTimer);
    stepElapsed = 0;
    const timerEl = document.getElementById('progTimer');
    if (timerEl) timerEl.textContent = '0 s';
    progressTimer = setInterval(() => {
        stepElapsed++;
        const el = document.getElementById('progTimer');
        if (el) el.textContent = stepElapsed + ' s';
    }, 1000);
}

function hideProgress() {
    clearInterval(progressTimer);
    progressTimer = null;
    stepElapsed   = 0;
    document.getElementById('progressPanel').classList.add('hidden');
}

/* ─── Process video ─────────────────────────────────────────── */
function processVideo() {
    const t   = I18N[uiLang];
    const url = document.getElementById('videoUrl').value.trim();
    if (!url) { showError(t.errNoUrl); return; }

    hideError();
    setLoading(true);

    const effectiveLang1 = immersionMode ? 'auto'   : selectedLang1;
    const effectiveLang2 = immersionMode ? uiLang   : selectedLang2;
    const lang1Label = immersionMode ? '?' : (LANG_LABELS[selectedLang1] || selectedLang1.toUpperCase());
    const lang2Label = LANG_LABELS[effectiveLang2] || effectiveLang2.toUpperCase();
    initProgress(lang1Label, lang2Label);

    const params = new URLSearchParams({ videoUrl: url, lang1: effectiveLang1, lang2: effectiveLang2 });
    const sse = new EventSource('/api/process/stream?' + params);
    let sseHandled = false;

    sse.addEventListener('progress', e => {
        const data = JSON.parse(e.data);
        updateStep(data.step, data.cached === true);
    });

    sse.addEventListener('complete', e => {
        sseHandled = true;
        sse.close();
        const barEl = document.getElementById('progBar');
        if (barEl) barEl.style.width = '100%';
        const data = JSON.parse(e.data);

        subtitles1 = data.subtitles1 || [];
        subtitles2 = data.subtitles2 || [];

        console.log('[DualSub] Response received:');
        console.log('  videoId   :', data.videoId);
        console.log('  lang1     :', data.lang1Label, '→', subtitles1.length, 'subtitles');
        console.log('  lang2     :', data.lang2Label, '→', subtitles2.length, 'subtitles');

        const label1 = data.lang1Label || lang1Label;
        const label2 = data.lang2Label || lang2Label;
        document.getElementById('lang1Badge').textContent = label1;
        document.getElementById('lang2Badge').textContent = label2;
        document.getElementById('hudStatus').textContent =
            t.hudStatus(subtitles1.length, label1, subtitles2.length, label2);

        setLoading(false);
        hideProgress();

        // Refresh history panel after a short delay (recordWatch is async on server).
        // In player mode: also ensure the sidebar becomes visible.
        setTimeout(() => {
            loadHistory().then(() => {
                const histPanel = document.getElementById('historyPanel');
                const inSidebar = histPanel.classList.contains('hist-sidebar-mode');
                if (inSidebar) {
                    const hasItems = document.querySelectorAll('#historyList .hist-item').length > 0;
                    histPanel.classList.toggle('hidden', !hasItems);
                }
            });
        }, 1500);

        showPlayer(data.videoId);
    });

    sse.addEventListener('apierror', e => {
        sseHandled = true;
        sse.close();
        const { error } = JSON.parse(e.data);
        showError(error || t.errServer);
        setLoading(false);
        hideProgress();
    });

    sse.onerror = () => {
        if (sseHandled) return;
        sse.close();
        console.error('[DualSub] SSE connection lost');
        showError(t.errServer);
        setLoading(false);
        hideProgress();
    };
}

/* ─── YouTube Player ────────────────────────────────────────── */
function showPlayer(videoId) {
    const app         = document.querySelector('.app');
    const histPanel   = document.getElementById('historyPanel');
    const playerLayout = document.getElementById('playerLayout');

    document.getElementById('configPanel').classList.add('hidden');

    // Move history panel into the player layout as a left sidebar.
    // Only show as sidebar if there are history items — otherwise skip it
    // to avoid a blank column consuming space.
    playerLayout.insertBefore(histPanel, playerLayout.firstChild);
    histPanel.classList.add('hist-sidebar-mode');
    const hasItems = document.querySelectorAll('#historyList .hist-item').length > 0;
    histPanel.classList.toggle('hidden', !hasItems);

    playerLayout.classList.remove('hidden');
    app.classList.add('player-active');

    subtitles1.sort((a, b) => a.startMs - b.startMs);
    subtitles2.sort((a, b) => a.startMs - b.startMs);

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
            onReady:       () => { console.log('[DualSub] Player ready'); },
            onStateChange: onPlayerStateChange,
        }
    });
}

function onPlayerStateChange(ev) {
    const states = { '-1':'UNSTARTED', 0:'ENDED', 1:'PLAYING', 2:'PAUSED', 3:'BUFFERING', 5:'CUED' };
    console.log('[DualSub] Player state:', states[ev.data] || ev.data);
    if (ev.data === YT.PlayerState.ENDED) {
        stopSync();
    } else if (!syncInterval) {
        startSync();
    }
}

function startSync() {
    stopSync();
    syncInterval = setInterval(syncSubtitles, 100);
}
function stopSync() {
    if (syncInterval) { clearInterval(syncInterval); syncInterval = null; }
}

/* ─── Subtitle sync ─────────────────────────────────────────── */
function syncSubtitles() {
    if (!player || typeof player.getCurrentTime !== 'function') return;
    const nowMs = player.getCurrentTime() * 1000;
    document.getElementById('subtitleText1').textContent = find(subtitles1, nowMs);
    document.getElementById('subtitleText2').textContent = find(subtitles2, nowMs);
    document.getElementById('hudTime').textContent = formatMs(nowMs);
}

function find(list, nowMs) {
    if (!list || list.length === 0) return '';
    let lo = 0, hi = list.length - 1, best = -1;
    while (lo <= hi) {
        const mid = (lo + hi) >> 1;
        if (list[mid].startMs <= nowMs) { best = mid; lo = mid + 1; }
        else                            { hi = mid - 1; }
    }
    if (best === -1) return '';
    const s = list[best];
    return nowMs < s.startMs + s.durationMs ? s.text : '';
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

    const app          = document.querySelector('.app');
    const histPanel    = document.getElementById('historyPanel');
    const playerLayout = document.getElementById('playerLayout');

    // Move history panel back between configPanel and playerLayout
    app.insertBefore(histPanel, playerLayout);
    histPanel.classList.remove('hist-sidebar-mode');

    playerLayout.classList.add('hidden');
    document.getElementById('configPanel').classList.remove('hidden');
    app.classList.remove('player-active');

    // Reload history — renderHistory() will show/hide the panel as appropriate
    loadHistory();
    if (player) player.stopVideo();
}

/* ─── UI helpers ────────────────────────────────────────────── */
function setLoading(on) {
    const t = I18N[uiLang];
    document.getElementById('btnProcess').disabled = on;
    document.getElementById('btnText').textContent = on ? t.btnLoading : t.btnAnalyze;
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
