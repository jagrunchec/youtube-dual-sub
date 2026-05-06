/* ─── State ─────────────────────────────────────────────────── */
let selectedLang1 = 'fr';
let selectedLang2 = 'de';
let subtitles1    = [];
let subtitles2    = [];
let player        = null;
let syncInterval  = null;

const LANG_LABELS = {
    fr: 'Français',  en: 'English',    es: 'Español',
    it: 'Italiano',  de: 'Deutsch',    pt: 'Português',
    nl: 'Nederlands', pl: 'Polski',    ru: 'Русский',
};

/* ─── Internationalisation ───────────────────────────────────── */
const I18N = {
    fr: {
        title:          'DualSub — Double Traduction YouTube',
        tagline:        '// TRADUCTION SIMULTANÉE  ·  DOUBLE PISTE EN TEMPS RÉEL',
        labelTarget:    '// CIBLE',
        labelLangs:     '// LANGUES',
        slotPrimary:    'LANGUE PRINCIPALE',
        slotSecondary:  'LANGUE SECONDAIRE',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALYSER & TRADUIRE',
        btnLoading:     'TRADUCTION EN COURS…',
        btnBack:        '◄  RETOUR',
        errNoUrl:       'Veuillez saisir une URL YouTube.',
        errServer:      'Impossible de joindre le serveur. Vérifiez que Spring Boot tourne sur le port 8080.',
        stepTranscript: 'Récupération du transcript',
        stepPunctuation:'Restauration de la ponctuation',
        stepSentences:  'Découpage en phrases',
        stepTranslation:'Traduction',
        hudStatus:      (n1, l1, n2, l2) => `${n1} lignes ${l1} · ${n2} lignes ${l2}`,
    },
    en: {
        title:          'DualSub — Dual YouTube Translation',
        tagline:        '// SIMULTANEOUS TRANSLATION  ·  DUAL TRACK IN REAL TIME',
        labelTarget:    '// TARGET',
        labelLangs:     '// LANGUAGES',
        slotPrimary:    'PRIMARY LANGUAGE',
        slotSecondary:  'SECONDARY LANGUAGE',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALYZE & TRANSLATE',
        btnLoading:     'TRANSLATING…',
        btnBack:        '◄  BACK',
        errNoUrl:       'Please enter a YouTube URL.',
        errServer:      'Cannot reach the server. Make sure Spring Boot is running on port 8080.',
        stepTranscript: 'Fetching transcript',
        stepPunctuation:'Restoring punctuation',
        stepSentences:  'Splitting into sentences',
        stepTranslation:'Translation',
        hudStatus:      (n1, l1, n2, l2) => `${n1} lines ${l1} · ${n2} lines ${l2}`,
    },
    es: {
        title:          'DualSub — Doble Traducción de YouTube',
        tagline:        '// TRADUCCIÓN SIMULTÁNEA  ·  DOBLE PISTA EN TIEMPO REAL',
        labelTarget:    '// OBJETIVO',
        labelLangs:     '// IDIOMAS',
        slotPrimary:    'IDIOMA PRINCIPAL',
        slotSecondary:  'IDIOMA SECUNDARIO',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALIZAR Y TRADUCIR',
        btnLoading:     'TRADUCIENDO…',
        btnBack:        '◄  VOLVER',
        errNoUrl:       'Por favor, introduce una URL de YouTube.',
        errServer:      'No se puede conectar al servidor. Verifica que Spring Boot esté en el puerto 8080.',
        stepTranscript: 'Obteniendo transcripción',
        stepPunctuation:'Restaurando puntuación',
        stepSentences:  'Dividiendo en frases',
        stepTranslation:'Traducción',
        hudStatus:      (n1, l1, n2, l2) => `${n1} líneas ${l1} · ${n2} líneas ${l2}`,
    },
    it: {
        title:          'DualSub — Doppia Traduzione YouTube',
        tagline:        '// TRADUZIONE SIMULTANEA  ·  DOPPIA TRACCIA IN TEMPO REALE',
        labelTarget:    '// DESTINAZIONE',
        labelLangs:     '// LINGUE',
        slotPrimary:    'LINGUA PRINCIPALE',
        slotSecondary:  'LINGUA SECONDARIA',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALIZZA E TRADUCI',
        btnLoading:     'TRADUZIONE IN CORSO…',
        btnBack:        '◄  INDIETRO',
        errNoUrl:       'Inserisci un URL di YouTube.',
        errServer:      'Impossibile raggiungere il server. Verifica che Spring Boot sia sulla porta 8080.',
        stepTranscript: 'Recupero trascrizione',
        stepPunctuation:'Ripristino punteggiatura',
        stepSentences:  'Suddivisione in frasi',
        stepTranslation:'Traduzione',
        hudStatus:      (n1, l1, n2, l2) => `${n1} righe ${l1} · ${n2} righe ${l2}`,
    },
    de: {
        title:          'DualSub — Doppelte YouTube-Übersetzung',
        tagline:        '// SIMULTANÜBERSETZUNG  ·  DOPPELSPUR IN ECHTZEIT',
        labelTarget:    '// ZIEL',
        labelLangs:     '// SPRACHEN',
        slotPrimary:    'HAUPTSPRACHE',
        slotSecondary:  'NEBENSPRACHE',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALYSIEREN & ÜBERSETZEN',
        btnLoading:     'ÜBERSETZUNG LÄUFT…',
        btnBack:        '◄  ZURÜCK',
        errNoUrl:       'Bitte eine YouTube-URL eingeben.',
        errServer:      'Server nicht erreichbar. Prüfe, ob Spring Boot auf Port 8080 läuft.',
        stepTranscript: 'Transkript abrufen',
        stepPunctuation:'Zeichensetzung wiederherstellen',
        stepSentences:  'In Sätze aufteilen',
        stepTranslation:'Übersetzung',
        hudStatus:      (n1, l1, n2, l2) => `${n1} Zeilen ${l1} · ${n2} Zeilen ${l2}`,
    },
    pt: {
        title:          'DualSub — Tradução Dupla do YouTube',
        tagline:        '// TRADUÇÃO SIMULTÂNEA  ·  FAIXA DUPLA EM TEMPO REAL',
        labelTarget:    '// DESTINO',
        labelLangs:     '// IDIOMAS',
        slotPrimary:    'IDIOMA PRINCIPAL',
        slotSecondary:  'IDIOMA SECUNDÁRIO',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALISAR E TRADUZIR',
        btnLoading:     'A TRADUZIR…',
        btnBack:        '◄  VOLTAR',
        errNoUrl:       'Por favor, insira um URL do YouTube.',
        errServer:      'Não é possível contactar o servidor. Verifique se o Spring Boot está na porta 8080.',
        stepTranscript: 'A obter a transcrição',
        stepPunctuation:'A restaurar a pontuação',
        stepSentences:  'A dividir em frases',
        stepTranslation:'Tradução',
        hudStatus:      (n1, l1, n2, l2) => `${n1} linhas ${l1} · ${n2} linhas ${l2}`,
    },
    nl: {
        title:          'DualSub — Dubbele YouTube-vertaling',
        tagline:        '// SIMULTAANVERTALING  ·  DUBBELE TRACK IN REAL TIME',
        labelTarget:    '// DOEL',
        labelLangs:     '// TALEN',
        slotPrimary:    'HOOFDTAAL',
        slotSecondary:  'TWEEDE TAAL',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'ANALYSEREN & VERTALEN',
        btnLoading:     'VERTALING BEZIG…',
        btnBack:        '◄  TERUG',
        errNoUrl:       'Voer een YouTube-URL in.',
        errServer:      'Kan de server niet bereiken. Controleer of Spring Boot draait op poort 8080.',
        stepTranscript: 'Transcript ophalen',
        stepPunctuation:'Interpunctie herstellen',
        stepSentences:  'In zinnen opdelen',
        stepTranslation:'Vertaling',
        hudStatus:      (n1, l1, n2, l2) => `${n1} regels ${l1} · ${n2} regels ${l2}`,
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
        hudStatus:      (n1, l1, n2, l2) => `${n1} linii ${l1} · ${n2} linii ${l2}`,
    },
    ru: {
        title:          'DualSub — Двойной перевод YouTube',
        tagline:        '// СИНХРОННЫЙ ПЕРЕВОД  ·  ДВОЙНАЯ ДОРОЖКА В РЕАЛЬНОМ ВРЕМЕНИ',
        labelTarget:    '// ЦЕЛЬ',
        labelLangs:     '// ЯЗЫКИ',
        slotPrimary:    'ОСНОВНОЙ ЯЗЫК',
        slotSecondary:  'ДОПОЛНИТЕЛЬНЫЙ ЯЗЫК',
        placeholder:    'https://www.youtube.com/watch?v=…',
        btnAnalyze:     'АНАЛИЗИРОВАТЬ И ПЕРЕВЕСТИ',
        btnLoading:     'ПЕРЕВОД…',
        btnBack:        '◄  НАЗАД',
        errNoUrl:       'Введите URL YouTube.',
        errServer:      'Сервер недоступен. Убедитесь, что Spring Boot запущен на порту 8080.',
        stepTranscript: 'Получение транскрипции',
        stepPunctuation:'Восстановление пунктуации',
        stepSentences:  'Разбивка на предложения',
        stepTranslation:'Перевод',
        hudStatus:      (n1, l1, n2, l2) => `${n1} строк ${l1} · ${n2} строк ${l2}`,
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
    // labelTarget contains a blink span — rebuild the HTML safely
    document.getElementById('labelTarget').innerHTML =
        t.labelTarget + '  <span class="blink">_</span>';
    document.getElementById('labelLangs').textContent     = t.labelLangs;
    document.getElementById('slotPrimary').textContent    = t.slotPrimary;
    document.getElementById('slotSecondary').textContent  = t.slotSecondary;
    document.getElementById('videoUrl').placeholder       = t.placeholder;
    document.getElementById('btnText').textContent        = t.btnAnalyze;
    document.getElementById('btnBack').textContent        = t.btnBack;
}

/* ─── Boot ──────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', () => {
    applyI18n();
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

/* ─── Pipeline steps (for SSE progress display) ─────────────── */
const PIPELINE_STEPS = [
    { key: 'transcript',   label: '' },
    { key: 'punctuation',  label: '' },
    { key: 'sentences',    label: '' },
    { key: 'translation1', label: '' },
    { key: 'translation2', label: '' },
];

function initProgress(lang1Label, lang2Label) {
    const t = I18N[uiLang];
    PIPELINE_STEPS[0].label = t.stepTranscript;
    PIPELINE_STEPS[1].label = t.stepPunctuation;
    PIPELINE_STEPS[2].label = t.stepSentences;
    PIPELINE_STEPS[3].label = t.stepTranslation + ' ' + lang1Label;
    PIPELINE_STEPS[4].label = t.stepTranslation + ' ' + lang2Label;

    const panel = document.getElementById('progressPanel');
    panel.innerHTML = PIPELINE_STEPS.map((s, i) => `
        <div class="progress-step" id="pstep-${s.key}">
            <div class="step-icon">${i + 1}</div>
            <span class="step-label">${s.label}</span>
        </div>`).join('');
    panel.classList.remove('hidden');
}

function updateStep(stepKey) {
    const keys = PIPELINE_STEPS.map(s => s.key);
    const idx  = keys.indexOf(stepKey);
    keys.forEach((k, i) => {
        const el = document.getElementById('pstep-' + k);
        if (!el) return;
        el.classList.remove('active', 'done');
        if (i < idx)  el.classList.add('done');
        if (i === idx) el.classList.add('active');
    });
}

function hideProgress() {
    document.getElementById('progressPanel').classList.add('hidden');
}

/* ─── Process video ─────────────────────────────────────────── */
function processVideo() {
    const t   = I18N[uiLang];
    const url = document.getElementById('videoUrl').value.trim();
    if (!url) { showError(t.errNoUrl); return; }

    hideError();
    setLoading(true);

    const lang1Label = LANG_LABELS[selectedLang1] || selectedLang1.toUpperCase();
    const lang2Label = LANG_LABELS[selectedLang2] || selectedLang2.toUpperCase();
    initProgress(lang1Label, lang2Label);

    const params = new URLSearchParams({ videoUrl: url, lang1: selectedLang1, lang2: selectedLang2 });
    const sse = new EventSource('/api/process/stream?' + params);
    let sseHandled = false;

    sse.addEventListener('progress', e => {
        const { step } = JSON.parse(e.data);
        updateStep(step);
    });

    sse.addEventListener('complete', e => {
        sseHandled = true;
        sse.close();
        const data = JSON.parse(e.data);

        subtitles1 = data.subtitles1 || [];
        subtitles2 = data.subtitles2 || [];

        // ── Diagnostic console ────────────────────────────────
        console.log('[DualSub] Response received:');
        console.log('  videoId   :', data.videoId);
        console.log('  lang1     :', data.lang1Label, '→', subtitles1.length, 'subtitles');
        console.log('  lang2     :', data.lang2Label, '→', subtitles2.length, 'subtitles');
        if (subtitles1.length > 0) {
            console.log('  first [' + data.lang1Label + '] :', subtitles1[0]);
            console.log('  last  [' + data.lang1Label + '] :', subtitles1[subtitles1.length - 1]);
        } else { console.warn('  ⚠ subtitles1 is EMPTY'); }
        if (subtitles2.length > 0) {
            console.log('  first [' + data.lang2Label + '] :', subtitles2[0]);
        } else { console.warn('  ⚠ subtitles2 is EMPTY'); }
        // ────────────────────────────────────────────────────

        const label1 = data.lang1Label || lang1Label;
        const label2 = data.lang2Label || lang2Label;
        document.getElementById('lang1Badge').textContent = label1;
        document.getElementById('lang2Badge').textContent = label2;
        document.getElementById('hudStatus').textContent =
            t.hudStatus(subtitles1.length, label1, subtitles2.length, label2);

        setLoading(false);
        hideProgress();
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
        if (sseHandled) return;   // already handled via 'complete' or 'apierror'
        sse.close();
        console.error('[DualSub] SSE connection lost');
        showError(t.errServer);
        setLoading(false);
        hideProgress();
    };
}

/* ─── YouTube Player ────────────────────────────────────────── */
function showPlayer(videoId) {
    document.getElementById('configPanel').classList.add('hidden');
    document.getElementById('playerSection').classList.remove('hidden');

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

    document.getElementById('hudTime').textContent = formatMs(nowMs);
}

/* Binary search in a startMs-sorted array.
   Strategy: find the last entry whose startMs ≤ nowMs, then verify nowMs
   is still within its duration window.  This is more resilient than a strict
   interval search: if two entries accidentally overlap (timing edge-case),
   the most recently started one is preferred, and skipping is avoided. */
function find(list, nowMs) {
    if (!list || list.length === 0) return '';

    let lo = 0, hi = list.length - 1, best = -1;
    while (lo <= hi) {
        const mid = (lo + hi) >> 1;
        if (list[mid].startMs <= nowMs) { best = mid; lo = mid + 1; }
        else                            { hi = mid - 1; }
    }

    if (best === -1) return '';                          // before first subtitle
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
    document.getElementById('configPanel').classList.remove('hidden');
    document.getElementById('playerSection').classList.add('hidden');
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
