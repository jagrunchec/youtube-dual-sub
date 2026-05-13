/* ══════════════════════════════════════════════════════════════
   AUTH & USER SYSTEM
   ══════════════════════════════════════════════════════════════ */

let currentUser = null;   // populated after successful login / checkAuth

/* ── Check auth on load ─────────────────────────────────────── */
async function checkAuth() {
    try {
        const resp = await fetch('/api/auth/me');
        if (resp.ok) {
            currentUser = await resp.json();
            showApp();
        } else {
            showAuthOverlay();
        }
    } catch (e) {
        showAuthOverlay();
    }
}

function showApp() {
    document.getElementById('authOverlay').classList.add('hidden');
    document.getElementById('userBar').classList.remove('hidden');
    document.getElementById('mainApp').classList.remove('hidden');
    renderUserBar();
    // Show help automatically on first visit
    if (!localStorage.getItem('dualsubHelpSeen')) {
        setTimeout(showHelp, 700);
    }
}

function showAuthOverlay() {
    document.getElementById('authOverlay').classList.remove('hidden');
    document.getElementById('userBar').classList.add('hidden');
    document.getElementById('mainApp').classList.add('hidden');
}

/* ── User bar ───────────────────────────────────────────────── */
function renderUserBar() {
    if (!currentUser) return;
    const initials = ((currentUser.firstName[0] || '') + (currentUser.lastName[0] || '')).toUpperCase() || '?';
    document.getElementById('userAvatar').textContent     = initials;
    document.getElementById('userName').textContent       = currentUser.firstName + ' ' + currentUser.lastName;
    document.getElementById('userRoleBadge').textContent  = currentUser.role;
    document.getElementById('userRoleBadge').className    = 'user-role-badge role-' + currentUser.role.toLowerCase();

    // Admin button
    const btnAdmin = document.getElementById('btnAdminPanel');
    btnAdmin.style.display = currentUser.role === 'ADMIN' ? '' : 'none';

    // Quota display for LIMITED users
    const quotaEl = document.getElementById('userQuota');
    if (currentUser.weeklyVideoLimit > 0) {
        const left  = currentUser.weeklyViewsLeft;
        const total = currentUser.weeklyVideoLimit;
        quotaEl.textContent = left + '/' + total + ' vidéos restantes';
        quotaEl.classList.remove('hidden');
        quotaEl.className = 'user-quota ' + (left === 0 ? 'quota-empty' : left <= 2 ? 'quota-low' : '');
    } else {
        quotaEl.classList.add('hidden');
    }
}

/* ── Auth tabs ──────────────────────────────────────────────── */
function showAuthTab(tab) {
    ['login', 'register', 'recover'].forEach(t => {
        document.getElementById(t + 'Form').classList.add('hidden');
        document.getElementById('tab' + t.charAt(0).toUpperCase() + t.slice(1)).classList.remove('active');
    });
    document.getElementById(tab + 'Form').classList.remove('hidden');
    document.getElementById('tab' + tab.charAt(0).toUpperCase() + tab.slice(1)).classList.add('active');
}

/* ── Login ──────────────────────────────────────────────────── */
async function doLogin(e) {
    e.preventDefault();
    const errEl = document.getElementById('loginError');
    errEl.classList.add('hidden');
    const email    = document.getElementById('loginEmail').value.trim();
    const password = document.getElementById('loginPassword').value;
    try {
        const resp = await fetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email, password }),
        });
        const data = await resp.json();
        if (!resp.ok) { errEl.textContent = data.error; errEl.classList.remove('hidden'); return; }
        currentUser = data;
        showApp();
        await loadPreferences();
        loadHistory();
    } catch (err) {
        errEl.textContent = 'Impossible de joindre le serveur.';
        errEl.classList.remove('hidden');
    }
}

/* ── Register ───────────────────────────────────────────────── */
async function doRegister(e) {
    e.preventDefault();
    const errEl = document.getElementById('registerError');
    errEl.classList.add('hidden');
    const password  = document.getElementById('regPassword').value;
    const password2 = document.getElementById('regPassword2').value;
    if (password !== password2) {
        errEl.textContent = 'Les mots de passe ne correspondent pas.';
        errEl.classList.remove('hidden');
        return;
    }
    const q1 = document.getElementById('regQ1').value;
    const q2 = document.getElementById('regQ2').value;
    if (q1 === q2) {
        errEl.textContent = 'Choisissez deux questions différentes.';
        errEl.classList.remove('hidden');
        return;
    }
    // Collect optional profile fields
    const learnChecked = [...document.querySelectorAll('#regLearnLangs input[type=checkbox]:checked')]
        .map(c => c.value);
    const birthYear = document.getElementById('regBirthYear').value;
    const country   = document.getElementById('regCountry').value.trim();

    try {
        const resp = await fetch('/api/auth/register', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                email:            document.getElementById('regEmail').value.trim(),
                password,
                firstName:        document.getElementById('regFirstName').value.trim(),
                lastName:         document.getElementById('regLastName').value.trim(),
                nativeLanguage:   document.getElementById('regNative').value,
                languagesToLearn: JSON.stringify(learnChecked),
                birthYear:        birthYear ? parseInt(birthYear) : null,
                country:          country || null,
                questionKeys:     [q1, q2],
                answers:          [document.getElementById('regA1').value, document.getElementById('regA2').value],
            }),
        });
        const data = await resp.json();
        if (!resp.ok) { errEl.textContent = data.error; errEl.classList.remove('hidden'); return; }
        if (data.pending) {
            // Email verification required — show confirmation message
            showRegisterPending(data.message);
            return;
        }
        // Auto-login after registration (mail disabled mode)
        currentUser = data;
        showApp();
        await loadPreferences();
        loadHistory();
    } catch (err) {
        errEl.textContent = 'Impossible de joindre le serveur.';
        errEl.classList.remove('hidden');
    }
}

function populateRegisterProfile() {
    // Native language select
    const nativeSel = document.getElementById('regNative');
    if (nativeSel) {
        nativeSel.innerHTML = '';
        Object.entries(LANG_LABELS).forEach(([code, label]) => {
            const opt = document.createElement('option');
            opt.value = code;
            opt.textContent = label;
            nativeSel.appendChild(opt);
        });
    }
    // Languages to learn checkboxes
    const learnDiv = document.getElementById('regLearnLangs');
    if (learnDiv) {
        learnDiv.innerHTML = '';
        Object.entries(LANG_LABELS).forEach(([code, label]) => {
            const lbl = document.createElement('label');
            lbl.className = 'pf-check';
            lbl.innerHTML = `<input type="checkbox" value="${code}"> ${label}`;
            learnDiv.appendChild(lbl);
        });
    }
}

async function loadSecurityQuestions() {
    try {
        const resp = await fetch('/api/auth/questions');
        if (!resp.ok) return;
        const questions = await resp.json();  // { key: label }
        ['regQ1', 'regQ2'].forEach(id => {
            const sel = document.getElementById(id);
            sel.innerHTML = '';
            Object.entries(questions).forEach(([key, label]) => {
                const opt = document.createElement('option');
                opt.value = key; opt.textContent = label;
                sel.appendChild(opt);
            });
        });
        // Default second question to a different one
        const opts = document.getElementById('regQ2').options;
        if (opts.length > 1) opts[1].selected = true;
    } catch (e) { /* silent */ }
}

/* ── Logout ─────────────────────────────────────────────────── */
async function doLogout() {
    await fetch('/api/auth/logout', { method: 'POST' });
    currentUser = null;
    showAuthOverlay();
    resetApp();
}

/* ── Registration pending (email confirmation required) ─────── */
function showRegisterPending(message) {
    // Switch to the recover tab and repurpose step2 as a "check your email" panel
    showAuthTab('recover');
    document.getElementById('recoverStep1').classList.add('hidden');
    document.getElementById('recoverStep3').classList.add('hidden');
    document.getElementById('recoverStep2').classList.remove('hidden');
    document.getElementById('recoverSentMsg').textContent =
        message || 'Un email de confirmation a été envoyé. Cliquez sur le lien pour activer votre compte.';
    // Change the tab label to reflect registration context
    document.getElementById('tabRecover').textContent = 'CONFIRMATION';
}

/* ── Password recovery (email link) ─────────────────────────── */
async function doSendRecoverEmail() {
    const email  = document.getElementById('recEmail').value.trim();
    const errEl  = document.getElementById('recoverError1');
    errEl.classList.add('hidden');
    if (!email) { errEl.textContent = 'Saisissez votre email.'; errEl.classList.remove('hidden'); return; }
    try {
        const resp = await fetch('/api/auth/recover/send', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email }),
        });
        const data = await resp.json();
        if (!resp.ok) { errEl.textContent = data.error; errEl.classList.remove('hidden'); return; }
        document.getElementById('recoverStep1').classList.add('hidden');
        document.getElementById('recoverSentMsg').textContent =
            'Un lien de réinitialisation a été envoyé à ' + email + '. Il est valable 1 heure.';
        document.getElementById('recoverStep2').classList.remove('hidden');
    } catch (e) {
        errEl.textContent = 'Impossible de joindre le serveur.'; errEl.classList.remove('hidden');
    }
}

/* ── Reset password via token (from email link) ─────────────── */
let _resetToken = null;  // populated when ?resetToken=xxx is in the URL

async function doResetPasswordByToken() {
    const errEl = document.getElementById('recoverError3');
    errEl.classList.add('hidden');
    const newPassword  = document.getElementById('recNewPwd').value;
    const newPassword2 = document.getElementById('recNewPwd2').value;
    if (newPassword.length < 8) {
        errEl.textContent = 'Minimum 8 caractères.'; errEl.classList.remove('hidden'); return;
    }
    if (newPassword !== newPassword2) {
        errEl.textContent = 'Les mots de passe ne correspondent pas.'; errEl.classList.remove('hidden'); return;
    }
    try {
        const resp = await fetch('/api/auth/reset-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ token: _resetToken, newPassword }),
        });
        const data = await resp.json();
        if (!resp.ok) { errEl.textContent = data.error; errEl.classList.remove('hidden'); return; }
        errEl.textContent = '✓ Mot de passe modifié ! Vous pouvez vous connecter.';
        errEl.style.color = '#00ff88';
        errEl.classList.remove('hidden');
        setTimeout(() => showAuthTab('login'), 2000);
    } catch (e) {
        errEl.textContent = 'Impossible de joindre le serveur.'; errEl.classList.remove('hidden');
    }
}

/* ── Profile modal ──────────────────────────────────────────── */
function showProfileModal() {
    if (!currentUser) return;
    const u = currentUser;
    const LEVELS = ['A1','A2','B1','B2','C1','C2'];
    const langs = JSON.parse(u.languagesToLearn || '[]');
    const level = JSON.parse(u.learningLevel    || '{}');

    document.getElementById('profileModalBody').innerHTML = `
        <div class="profile-section">
            <h3>// MES STATISTIQUES</h3>
            <div id="pfStatsContent" class="stats-loading">Chargement…</div>
        </div>` + `
        <div class="profile-section">
            <h3>// INFORMATIONS PERSONNELLES</h3>
            <div class="profile-row2">
                <div class="pf"><label>PRÉNOM</label><input id="pfFirstName" value="${esc(u.firstName)}"></div>
                <div class="pf"><label>NOM</label><input id="pfLastName" value="${esc(u.lastName)}"></div>
            </div>
            <div class="pf"><label>EMAIL</label><input value="${esc(u.email)}" disabled></div>
            <div class="profile-row2">
                <div class="pf"><label>ANNÉE DE NAISSANCE</label><input id="pfBirthYear" type="number" value="${u.birthYear || ''}" placeholder="1990"></div>
                <div class="pf"><label>PAYS</label><input id="pfCountry" value="${esc(u.country)}" placeholder="FR"></div>
            </div>
        </div>
        <div class="profile-section">
            <h3>// APPRENTISSAGE</h3>
            <div class="pf"><label>LANGUE MATERNELLE</label>
                <select id="pfNative">
                    ${Object.entries(LANG_LABELS).map(([k,v]) => `<option value="${k}" ${u.nativeLanguage===k?'selected':''}>${v}</option>`).join('')}
                </select>
            </div>
            <div class="pf"><label>LANGUES À APPRENDRE</label>
                <div class="pf-checkboxes">
                    ${Object.entries(LANG_LABELS).map(([k,v]) => `
                        <label class="pf-check"><input type="checkbox" value="${k}" ${langs.includes(k)?'checked':''}> ${v}</label>
                    `).join('')}
                </div>
            </div>
        </div>
        <div class="profile-section">
            <h3>// CHANGER LE MOT DE PASSE</h3>
            <div class="pf"><label>MOT DE PASSE ACTUEL</label>
                <div class="pwd-wrap">
                    <input type="password" id="pfOldPwd" placeholder="••••••••">
                    <button type="button" class="pwd-toggle" onclick="togglePwd('pfOldPwd',this)" title="Afficher/masquer">👁</button>
                </div>
            </div>
            <div class="pf"><label>NOUVEAU MOT DE PASSE</label>
                <div class="pwd-wrap">
                    <input type="password" id="pfNewPwd" placeholder="••••••••">
                    <button type="button" class="pwd-toggle" onclick="togglePwd('pfNewPwd',this)" title="Afficher/masquer">👁</button>
                </div>
            </div>
            <div class="auth-error hidden" id="pfPwdError"></div>
            <button class="btn-fire auth-submit" onclick="doChangePwd()"><span class="fire-glyph">🔑</span> CHANGER</button>
        </div>
        <div class="profile-section">
            <h3>// SUPPORT</h3>
            <div class="pf"><label>SUJET</label><input id="pfSupportSubject" placeholder="Problème ou suggestion…"></div>
            <div class="pf"><label>MESSAGE</label><textarea id="pfSupportBody" rows="4" placeholder="Décrivez votre problème…"></textarea></div>
            <div class="auth-error hidden" id="pfSupportMsg"></div>
            <button class="btn-fire auth-submit" onclick="doSendSupport()"><span class="fire-glyph">✉</span> ENVOYER</button>
        </div>
        <div class="profile-actions">
            <button class="btn-fire" onclick="saveProfile()"><span class="fire-glyph">💾</span> ENREGISTRER LE PROFIL</button>
            <div class="auth-error hidden" id="pfSaveMsg"></div>
        </div>`;

    document.getElementById('profileModal').classList.remove('hidden');

    // Load and inject personal stats (async)
    loadUserStats().then(stats => {
        const el = document.getElementById('pfStatsContent');
        if (el) el.innerHTML = renderStatsSection(stats);
    });
}

function closeProfileModal() {
    document.getElementById('profileModal').classList.add('hidden');
}

async function saveProfile() {
    const msgEl = document.getElementById('pfSaveMsg');
    msgEl.classList.add('hidden');
    const learnChecks = [...document.querySelectorAll('#profileModalBody .pf-checkboxes input[type=checkbox]')];
    const langChecks  = learnChecks.filter(c => Object.keys(LANG_LABELS).includes(c.value) && c.checked).map(c => c.value);

    try {
        const resp = await fetch('/api/users/me', {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                firstName:        document.getElementById('pfFirstName').value,
                lastName:         document.getElementById('pfLastName').value,
                nativeLanguage:   document.getElementById('pfNative').value,
                languagesToLearn: JSON.stringify(langChecks),
                birthYear:        document.getElementById('pfBirthYear').value || null,
                country:          document.getElementById('pfCountry').value,
            }),
        });
        const data = await resp.json();
        if (!resp.ok) { msgEl.textContent = data.error; msgEl.classList.remove('hidden'); return; }
        currentUser = data;
        renderUserBar();
        msgEl.textContent = '✓ Profil enregistré';
        msgEl.style.color = '#00ff88';
        msgEl.classList.remove('hidden');
    } catch (e) {
        msgEl.textContent = 'Erreur réseau'; msgEl.classList.remove('hidden');
    }
}

async function doChangePwd() {
    const errEl = document.getElementById('pfPwdError');
    errEl.classList.add('hidden');
    const currentPassword = document.getElementById('pfOldPwd').value;
    const newPassword     = document.getElementById('pfNewPwd').value;
    if (newPassword.length < 8) {
        errEl.textContent = 'Minimum 8 caractères.'; errEl.classList.remove('hidden'); return;
    }
    const resp = await fetch('/api/users/me/password', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ currentPassword, newPassword }),
    });
    const data = await resp.json();
    if (!resp.ok) { errEl.textContent = data.error; errEl.classList.remove('hidden'); return; }
    errEl.textContent = '✓ Mot de passe modifié';
    errEl.style.color = '#00ff88';
    errEl.classList.remove('hidden');
    document.getElementById('pfOldPwd').value = '';
    document.getElementById('pfNewPwd').value = '';
}

function togglePwd(inputId, btn) {
    const input = document.getElementById(inputId);
    if (input.type === 'password') {
        input.type = 'text';
        btn.textContent = '🙈';
    } else {
        input.type = 'password';
        btn.textContent = '👁';
    }
}

async function doSendSupport() {
    const msgEl = document.getElementById('pfSupportMsg');
    msgEl.classList.add('hidden');
    const subject = document.getElementById('pfSupportSubject').value.trim();
    const body    = document.getElementById('pfSupportBody').value.trim();
    if (!subject || !body) {
        msgEl.textContent = 'Sujet et message requis.'; msgEl.classList.remove('hidden'); return;
    }
    const resp = await fetch('/api/support/messages', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ subject, body }),
    });
    const data = await resp.json();
    if (!resp.ok) { msgEl.textContent = data.error; msgEl.classList.remove('hidden'); return; }
    msgEl.textContent = '✓ Message envoyé à l\'équipe support';
    msgEl.style.color = '#00ff88';
    msgEl.classList.remove('hidden');
    document.getElementById('pfSupportSubject').value = '';
    document.getElementById('pfSupportBody').value = '';
}

/* ── Admin panel ────────────────────────────────────────────── */
function showAdminPanel() {
    document.getElementById('adminPanel').classList.remove('hidden');
    showAdminTab('users');
}
function hideAdminPanel() {
    document.getElementById('adminPanel').classList.add('hidden');
}

async function showAdminTab(tab) {
    // Update tab styles
    document.querySelectorAll('.admin-tab').forEach(t => t.classList.remove('active'));
    document.getElementById('adminTab' + tab.charAt(0).toUpperCase() + tab.slice(1)).classList.add('active');

    const content = document.getElementById('adminContent');
    content.innerHTML = '<div class="admin-loading">Chargement…</div>';

    if (tab === 'users')   await renderAdminUsers(content);
    if (tab === 'support') await renderAdminSupport(content);
    if (tab === 'stats')   await renderAdminStats(content);
}

async function renderAdminUsers(container) {
    const resp = await fetch('/api/admin/users');
    const users = await resp.json();
    const ROLES = ['LIMITED','NORMAL','SUPER','ADMIN'];
    container.innerHTML = `
        <div class="admin-table-wrap">
        <table class="admin-table">
        <thead><tr>
            <th>NOM</th><th>EMAIL</th><th>RÔLE</th><th>QUOTA</th>
            <th>VUES SEMAINE</th><th>STATUT</th><th>DERNIÈRE CONNEXION</th><th>ACTIONS</th>
        </tr></thead>
        <tbody>
        ${users.map(u => `
            <tr class="${!u.active ? 'row-inactive' : ''} ${u.locked ? 'row-locked' : ''}">
                <td>${esc(u.firstName)} ${esc(u.lastName)}</td>
                <td class="td-email">${esc(u.email)}</td>
                <td>
                    <select class="admin-select role-select" data-uid="${u.id}" onchange="adminSetRole(${u.id},this.value)">
                        ${ROLES.map(r => `<option ${u.role===r?'selected':''}>${r}</option>`).join('')}
                    </select>
                </td>
                <td>
                    ${u.weeklyVideoLimit > 0
                        ? `<input class="admin-num-input" type="number" min="1" value="${u.weeklyVideoLimit}"
                              onchange="adminSetLimit(${u.id},this.value)">`
                        : '<span class="td-muted">∞</span>'}
                </td>
                <td>${u.weeklyViewCount}</td>
                <td>
                    <span class="status-badge ${u.active?'status-active':'status-inactive'}">${u.active?'ACTIF':'INACTIF'}</span>
                    ${u.locked ? `<span class="status-badge status-locked" title="Bloqué jusqu'au ${u.lockedUntil.substring(0,16).replace('T',' ')}">🔐 BLOQUÉ</span>` : ''}
                    ${!u.locked && u.failedAttempts > 0 ? `<span class="td-muted" title="${u.failedAttempts} tentative(s) échouée(s)">${u.failedAttempts}×⚠</span>` : ''}
                </td>
                <td class="td-date">${u.lastLoginAt ? u.lastLoginAt.substring(0,16).replace('T',' ') : '—'}</td>
                <td class="td-actions">
                    ${u.locked ? `<button class="admin-btn admin-btn-unlock" onclick="adminUnlock(${u.id})">🔓 Débloquer</button>` : ''}
                    <button class="admin-btn" onclick="adminToggleActive(${u.id},${!u.active})">
                        ${u.active ? '🚫 Désactiver' : '✅ Activer'}
                    </button>
                    <button class="admin-btn admin-btn-delete" onclick="adminDeleteUser(${u.id},'${esc(u.email)}')">
                        🗑 Supprimer
                    </button>
                </td>
            </tr>`).join('')}
        </tbody></table></div>`;
}

async function adminSetRole(userId, role) {
    await fetch(`/api/admin/users/${userId}/role`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ role }),
    });
    showAdminTab('users');
}

async function adminSetLimit(userId, limit) {
    await fetch(`/api/admin/users/${userId}/limit`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ limit: parseInt(limit) }),
    });
}

async function adminToggleActive(userId, active) {
    await fetch(`/api/admin/users/${userId}/active`, {
        method: 'PUT',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ active }),
    });
    showAdminTab('users');
}

async function adminUnlock(userId) {
    await fetch(`/api/admin/users/${userId}/unlock`, { method: 'POST' });
    showAdminTab('users');
}

async function adminDeleteUser(userId, email) {
    if (!confirm(`Supprimer définitivement le compte "${email}" ?\n\nToutes ses données seront effacées (historique, tokens, questions de sécurité, tickets support).\n\nCette action est irréversible.`)) return;
    const resp = await fetch(`/api/admin/users/${userId}`, { method: 'DELETE' });
    if (!resp.ok) {
        const data = await resp.json().catch(() => ({}));
        alert(data.error || 'Erreur lors de la suppression.');
        return;
    }
    showAdminTab('users');
}

async function renderAdminSupport(container) {
    const resp     = await fetch('/api/admin/support');
    const messages = await resp.json();
    const STATUS_LABELS = { OPEN:'OUVERT', IN_PROGRESS:'EN COURS', CLOSED:'FERMÉ' };
    if (messages.length === 0) {
        container.innerHTML = '<p class="admin-empty">Aucun ticket de support.</p>'; return;
    }
    container.innerHTML = messages.map(m => `
        <div class="support-card ${m.status.toLowerCase()}">
            <div class="support-card-header">
                <span class="support-badge support-${m.status.toLowerCase()}">${STATUS_LABELS[m.status]||m.status}</span>
                <span class="support-user">${esc(m.userName)} &lt;${esc(m.userEmail)}&gt;</span>
                <span class="support-date">${m.createdAt.substring(0,16).replace('T',' ')}</span>
            </div>
            <div class="support-subject">${esc(m.subject)}</div>
            <div class="support-body">${esc(m.body)}</div>
            ${m.adminResponse ? `<div class="support-response"><strong>Réponse :</strong> ${esc(m.adminResponse)}</div>` : ''}
            ${m.status !== 'CLOSED' ? `
            <div class="support-reply">
                <textarea id="replyText${m.id}" rows="2" placeholder="Votre réponse…"></textarea>
                <div class="support-reply-btns">
                    <button class="admin-btn" onclick="adminRespond(${m.id},'IN_PROGRESS')">Marquer En cours</button>
                    <button class="admin-btn admin-btn-close" onclick="adminRespond(${m.id},'CLOSED')">Répondre & Fermer</button>
                </div>
            </div>` : ''}
        </div>`).join('');
}

async function adminRespond(msgId, status) {
    const text = document.getElementById('replyText' + msgId).value.trim();
    await fetch(`/api/admin/support/${msgId}/respond`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ response: text, status }),
    });
    showAdminTab('support');
}

async function renderAdminStats(container) {
    const resp  = await fetch('/api/admin/stats');
    const stats = await resp.json();
    container.innerHTML = `
        <div class="stats-grid">
            <div class="stat-card"><div class="stat-val">${stats.totalUsers}</div><div class="stat-lbl">Utilisateurs total</div></div>
            <div class="stat-card"><div class="stat-val">${stats.activeUsers}</div><div class="stat-lbl">Comptes actifs</div></div>
            <div class="stat-card"><div class="stat-val">${stats.limitedUsers}</div><div class="stat-lbl">Profil LIMITED</div></div>
            <div class="stat-card"><div class="stat-val">${stats.normalUsers}</div><div class="stat-lbl">Profil NORMAL</div></div>
            <div class="stat-card"><div class="stat-val">${stats.superUsers}</div><div class="stat-lbl">Profil SUPER</div></div>
            <div class="stat-card"><div class="stat-val">${stats.adminUsers}</div><div class="stat-lbl">Administrateurs</div></div>
            <div class="stat-card ${stats.openTickets>0?'stat-warn':''}"><div class="stat-val">${stats.openTickets}</div><div class="stat-lbl">Tickets ouverts</div></div>
        </div>`;
}

function esc(str) {
    return String(str || '').replace(/&/g,'&amp;').replace(/</g,'&lt;').replace(/>/g,'&gt;').replace(/"/g,'&quot;');
}

/* ══════════════════════════════════════════════════════════════
   EXISTING APP CODE
   ══════════════════════════════════════════════════════════════ */

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
let currentSpeed       = 1;      // current playback rate
let transcriptVisible  = false;  // transcript panel open?
let lastTranscriptIdx  = -1;     // last highlighted transcript row
let loopStart          = null;   // phrase-loop start ms (null = off)
let loopEnd            = null;   // phrase-loop end ms
let focusMode          = false;  // AUTO-PAUSE mode on/off
let focusPaused        = false;  // currently waiting for user click
let _focusPauseSubIdx  = -1;     // subtitle index at which we last paused
let _currentVideoId    = null;   // video currently loaded
let _positionTimer     = null;   // setInterval handle for position autosave
let _pendingResume     = null;   // saved position ms to offer as resume
let searchMatches      = [];     // transcript row indices matching current search
let searchIndex        = -1;     // which match is active
let currentTheme       = localStorage.getItem('dualsubTheme') || 'light';

const LANG_LABELS = {
    fr: 'Français', en: 'English', es: 'Español',
    it: 'Italiano', de: 'Deutsch', pl: 'Polski',
};

/* ─── Internationalisation ───────────────────────────────────── */
const BOOKMARKLET_HINTS = {
    fr: 'Glisser ce lien dans la barre de favoris. Cliquez-le ensuite sur n\'importe quelle page YouTube pour ouvrir DualSub automatiquement.',
    en: 'Drag this link to your bookmarks bar. Click it on any YouTube page to open DualSub automatically.',
    es: 'Arrastra este enlace a la barra de favoritos. Haz clic en él en cualquier página de YouTube para abrir DualSub automáticamente.',
    it: 'Trascina questo link nella barra dei preferiti. Cliccalo su qualsiasi pagina YouTube per aprire DualSub automaticamente.',
    de: 'Ziehe diesen Link in die Lesezeichenleiste. Klicke ihn auf einer beliebigen YouTube-Seite, um DualSub automatisch zu öffnen.',
    pl: 'Przeciągnij ten link na pasek zakładek. Kliknij go na dowolnej stronie YouTube, aby automatycznie otworzyć DualSub.',
};

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

    // Bookmarklet hint text (localised)
    const hintEl = document.getElementById('bkmkHint');
    if (hintEl) {
        const msg = BOOKMARKLET_HINTS[uiLang] || BOOKMARKLET_HINTS.en;
        hintEl.dataset.msg  = msg;
        hintEl.textContent  = msg;
    }
}

/* ─── Immersion mode ────────────────────────────────────────── */
function toggleImmersion(cb) {
    immersionMode = cb.checked;
    document.getElementById('slot1').classList.toggle('imm-locked', immersionMode);
    document.getElementById('slot2').classList.toggle('imm-locked', immersionMode);
    savePreferences();
}

/* ─── Favicon (canvas PNG — picked up by Chrome when bookmarking) ── */
function setCanvasFavicon() {
    try {
        const SIZE = 64;
        const c   = document.createElement('canvas');
        c.width   = c.height = SIZE;
        const ctx = c.getContext('2d');

        // Rounded-square clip
        ctx.beginPath();
        ctx.roundRect(0, 0, SIZE, SIZE, SIZE * 0.16);
        ctx.clip();

        // Green left half (piste 1)
        ctx.fillStyle = '#00cc6e';
        ctx.fillRect(0, 0, SIZE / 2, SIZE);

        // Orange right half (piste 2)
        ctx.fillStyle = '#ff7b00';
        ctx.fillRect(SIZE / 2, 0, SIZE / 2, SIZE);

        // White play triangle centred on the split
        ctx.fillStyle = 'white';
        ctx.beginPath();
        ctx.moveTo(SIZE * 0.22, SIZE * 0.17);   // top-left
        ctx.lineTo(SIZE * 0.22, SIZE * 0.83);   // bottom-left
        ctx.lineTo(SIZE * 0.82, SIZE * 0.50);   // right apex
        ctx.closePath();
        ctx.fill();

        // Replace the SVG favicon with the canvas PNG
        const link = document.querySelector("link[rel='icon']")
                  || document.head.appendChild(
                       Object.assign(document.createElement('link'), { rel: 'icon' }));
        link.type = 'image/png';
        link.href = c.toDataURL('image/png');
    } catch (e) { /* keep SVG fallback */ }
}

/* ─── Boot ──────────────────────────────────────────────────── */
document.addEventListener('DOMContentLoaded', async () => {
    setCanvasFavicon();
    applyI18n();
    initTheme();
    initKeyboardShortcuts();

    // Render flag emoji as SVG images via Twemoji (Windows desktop doesn't support flag emoji natively)
    if (typeof twemoji !== 'undefined') {
        document.querySelectorAll('.flag-icon').forEach(el => {
            twemoji.parse(el, {
                folder: 'svg', ext: '.svg',
                base: 'https://cdn.jsdelivr.net/gh/twitter/twemoji@14.0.2/assets/'
            });
        });
    }

    // Load security questions and populate profile fields for the registration form
    loadSecurityQuestions();
    populateRegisterProfile();

    document.getElementById('videoUrl').addEventListener('keydown', e => {
        if (e.key === 'Enter') processVideo();
    });

    // Handle special URL params before auth check
    const params      = new URLSearchParams(window.location.search);
    const bookmarkUrl = params.get('v');
    const resetToken  = params.get('resetToken');
    const verified    = params.get('verified');
    const verifyError = params.get('verifyError');

    // Clean URL immediately
    history.replaceState(null, '', window.location.pathname);

    if (bookmarkUrl) {
        document.getElementById('videoUrl').value = bookmarkUrl;
    }

    // Auth check — shows app or login form
    await checkAuth();

    // After auth check, handle special cases (only if not logged in)
    if (!currentUser) {
        if (resetToken) {
            // Show password reset form
            _resetToken = resetToken;
            showAuthTab('recover');
            document.getElementById('recoverStep1').classList.add('hidden');
            document.getElementById('recoverStep2').classList.add('hidden');
            document.getElementById('recoverStep3').classList.remove('hidden');
        } else if (verified) {
            // Show success message on login tab
            showAuthTab('login');
            const errEl = document.getElementById('loginError');
            errEl.textContent = '✓ Compte activé avec succès ! Vous pouvez maintenant vous connecter.';
            errEl.style.color = '#00ff88';
            errEl.classList.remove('hidden');
        } else if (verifyError) {
            showAuthTab('login');
            const errEl = document.getElementById('loginError');
            errEl.textContent = decodeURIComponent(verifyError);
            errEl.classList.remove('hidden');
        }
    }

    if (currentUser) {
        await loadPreferences();
        loadHistory();
        if (bookmarkUrl) processVideo();
    }
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
        const data = JSON.parse(e.data);
        showError(data.error || t.errServer);
        setLoading(false);
        hideProgress();
        // Refresh user bar so the quota counter updates
        if (data.limitReached) {
            fetch('/api/auth/me').then(r => r.json()).then(u => { currentUser = u; renderUserBar(); });
        }
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
    _currentVideoId = videoId;
    clearLoop();
    focusPaused = false; _focusPauseSubIdx = -1;
    searchMatches = []; searchIndex = -1;
    if (document.getElementById('transcriptSearch')) {
        document.getElementById('transcriptSearch').value = '';
        document.getElementById('searchCount').textContent = '';
    }

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

    // Update export labels and render transcript
    const l1 = document.getElementById('lang1Badge').textContent;
    const l2 = document.getElementById('lang2Badge').textContent;
    document.getElementById('exportLabel1').textContent = l1;
    document.getElementById('exportLabel2').textContent = l2;
    document.getElementById('trHead1').textContent = l1;
    document.getElementById('trHead2').textContent = l2;
    renderTranscript();
    lastTranscriptIdx = -1;

    // Position memory: offer resume if we have a saved position > 10 s
    _pendingResume = getSavedPosition(videoId);
    startPositionSaving();

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
    // Re-apply speed whenever playback (re)starts — YT resets rate on new video
    if (ev.data === YT.PlayerState.PLAYING && currentSpeed !== 1) {
        player.setPlaybackRate(currentSpeed);
    }
    // Offer to resume saved position on first PLAYING event
    if (ev.data === YT.PlayerState.PLAYING && _pendingResume !== null) {
        const posMs = _pendingResume;
        _pendingResume = null;
        if (posMs > 10000) showResumeToast(posMs);
    }
    // AUTO-PAUSE: if the user manually resumes via the YouTube player, clear the
    // paused state so subtitle sync and future auto-pauses work normally again.
    if (ev.data === YT.PlayerState.PLAYING && focusPaused) {
        focusPaused = false;
        document.getElementById('focusOverlay').classList.add('hidden');
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
    // ── Phrase loop ──────────────────────────────────────────────
    if (loopStart !== null && nowMs >= loopEnd) {
        player.seekTo(loopStart / 1000, true);
        return;
    }
    document.getElementById('subtitleText1').textContent = find(subtitles1, nowMs);
    document.getElementById('subtitleText2').textContent = find(subtitles2, nowMs);
    document.getElementById('hudTime').textContent = formatMs(nowMs);
    syncTranscriptScroll(nowMs);
    if (focusMode && !focusPaused) checkFocusPause(nowMs);
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

/* ─── Playback speed ────────────────────────────────────────── */
function setSpeed(rate) {
    currentSpeed = rate;
    if (player && typeof player.setPlaybackRate === 'function') {
        player.setPlaybackRate(rate);
    }
    document.querySelectorAll('.speed-btn').forEach(b => {
        const active = parseFloat(b.dataset.rate) === rate;
        b.classList.toggle('speed-active', active);
    });
}

/* ─── Transcript panel ──────────────────────────────────────── */
function toggleTranscript() {
    transcriptVisible = !transcriptVisible;
    document.getElementById('transcriptPanel').classList.toggle('hidden', !transcriptVisible);
    document.getElementById('btnTranscript').classList.toggle('tb-active', transcriptVisible);
}

function renderTranscript() {
    const inner = document.getElementById('transcriptInner');
    inner.innerHTML = '';
    const len = Math.max(subtitles1.length, subtitles2.length);
    if (len === 0) return;
    const frag = document.createDocumentFragment();
    for (let i = 0; i < len; i++) {
        const s1 = subtitles1[i];
        const s2 = subtitles2[i];
        const startMs  = s1 ? s1.startMs  : s2.startMs;
        const durMs    = s1 ? s1.durationMs : (s2 ? s2.durationMs : 3000);
        const row = document.createElement('div');
        row.className = 'tr-row';
        row.dataset.index   = i;
        row.dataset.startMs = startMs;
        row.innerHTML =
            `<span class="tr-time">${formatMs(startMs)}</span>` +
            `<span class="tr-text tr-text1">${esc(s1 ? s1.text : '')}</span>` +
            `<span class="tr-sep"></span>` +
            `<span class="tr-text tr-text2">${esc(s2 ? s2.text : '')}</span>` +
            `<button class="tr-loop-btn" title="Boucler cette phrase (L pour annuler)">⟳</button>`;
        // Seek on row click
        row.addEventListener('click', () => {
            if (player && typeof player.seekTo === 'function') player.seekTo(startMs / 1000, true);
        });
        // Loop toggle on ⟳ button
        const loopBtn = row.querySelector('.tr-loop-btn');
        loopBtn.addEventListener('click', ev => {
            ev.stopPropagation();
            toggleLoop(startMs, durMs, loopBtn);
        });
        frag.appendChild(row);
    }
    inner.appendChild(frag);
}

function syncTranscriptScroll(nowMs) {
    if (!transcriptVisible) return;
    const rows = document.querySelectorAll('#transcriptInner .tr-row');
    if (!rows.length) return;
    // Binary search for the last row whose startMs ≤ nowMs
    let lo = 0, hi = rows.length - 1, best = 0;
    while (lo <= hi) {
        const mid = (lo + hi) >> 1;
        if (parseFloat(rows[mid].dataset.startMs) <= nowMs) { best = mid; lo = mid + 1; }
        else hi = mid - 1;
    }
    if (best === lastTranscriptIdx) return;
    rows[lastTranscriptIdx]?.classList.remove('tr-active');
    rows[best].classList.add('tr-active');
    rows[best].scrollIntoView({ block: 'nearest', behavior: 'smooth' });
    lastTranscriptIdx = best;
}

/* ─── Export ────────────────────────────────────────────────── */
function exportSRT(track) {
    const subs  = track === 1 ? subtitles1 : subtitles2;
    const label = track === 1
        ? document.getElementById('lang1Badge').textContent
        : document.getElementById('lang2Badge').textContent;
    if (!subs.length) return;
    let out = '';
    subs.forEach((s, i) => {
        out += `${i + 1}\r\n${srtTime(s.startMs)} --> ${srtTime(s.startMs + s.durationMs)}\r\n${s.text}\r\n\r\n`;
    });
    downloadFile(out, `DualSub_${label}.srt`, 'text/plain');
}

function exportTXT() {
    if (!subtitles1.length && !subtitles2.length) return;
    const l1 = document.getElementById('lang1Badge').textContent;
    const l2 = document.getElementById('lang2Badge').textContent;
    const len = Math.max(subtitles1.length, subtitles2.length);
    let out = '';
    for (let i = 0; i < len; i++) {
        const s1 = subtitles1[i];
        const s2 = subtitles2[i];
        out += `[${formatMs(s1 ? s1.startMs : s2.startMs)}]\r\n`;
        if (s1) out += `${l1}: ${s1.text}\r\n`;
        if (s2) out += `${l2}: ${s2.text}\r\n`;
        out += '\r\n';
    }
    downloadFile(out, `DualSub_${l1}_${l2}.txt`, 'text/plain');
}

function srtTime(ms) {
    const h  = Math.floor(ms / 3_600_000);
    const m  = Math.floor(ms % 3_600_000 / 60_000);
    const s  = Math.floor(ms % 60_000 / 1_000);
    const cs = ms % 1_000;
    return `${pad2(h)}:${pad2(m)}:${pad2(s)},${String(cs).padStart(3,'0')}`;
}
function pad2(n) { return String(n).padStart(2, '0'); }

function downloadFile(content, filename, mime) {
    const blob = new Blob(['﻿' + content], { type: mime + ';charset=utf-8' });
    const url  = URL.createObjectURL(blob);
    const a    = Object.assign(document.createElement('a'), { href: url, download: filename });
    document.body.appendChild(a);
    a.click();
    document.body.removeChild(a);
    URL.revokeObjectURL(url);
}

/* ─── Reset ─────────────────────────────────────────────────── */
function resetApp() {
    stopSync();
    stopPositionSaving();
    clearLoop();
    focusMode = false; focusPaused = false; _focusPauseSubIdx = -1;
    searchMatches = []; searchIndex = -1;
    _pendingResume = null;
    _currentVideoId = null;
    document.getElementById('focusOverlay').classList.add('hidden');
    document.getElementById('btnFocus').classList.remove('tb-active');
    subtitles1 = [];
    subtitles2 = [];
    // Hide resume toast
    document.getElementById('resumeToast').classList.add('hidden');
    // Reset speed UI (keep currentSpeed for next video)
    // Reset transcript
    transcriptVisible = false;
    lastTranscriptIdx = -1;
    document.getElementById('transcriptPanel').classList.add('hidden');
    document.getElementById('btnTranscript').classList.remove('tb-active');
    document.getElementById('transcriptInner').innerHTML = '';
    if (document.getElementById('transcriptSearch')) {
        document.getElementById('transcriptSearch').value = '';
        document.getElementById('searchCount').textContent = '';
    }
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

/* ══════════════════════════════════════════════════════════════
   FEATURE 4 — Keyboard shortcuts
   Space / k  → play/pause
   ← / →      → ±5 s
   + / =      → speed up
   -          → speed down
   t / T      → toggle transcript
   l / L      → clear phrase loop
   ══════════════════════════════════════════════════════════════ */
function initKeyboardShortcuts() {
    document.addEventListener('keydown', e => {
        // Escape: close any open modal
        if (e.key === 'Escape') {
            if (!document.getElementById('helpModal').classList.contains('hidden')) { closeHelp(); return; }
            if (!document.getElementById('profileModal').classList.contains('hidden')) { closeProfileModal(); return; }
        }

        // Player shortcuts — only when the player layout is visible
        if (document.getElementById('playerLayout').classList.contains('hidden')) return;
        if (!player || typeof player.getPlayerState !== 'function') return;
        // Ignore when focus is inside a text field
        const tag = (e.target.tagName || '').toLowerCase();
        if (['input', 'textarea', 'select'].includes(tag)) return;

        const RATES = [0.75, 1, 1.25, 1.5, 2];
        switch (e.key) {
            case ' ':
            case 'k':
                e.preventDefault();
                if (focusPaused) { resumeFromFocus(); break; }
                player.getPlayerState() === YT.PlayerState.PLAYING
                    ? player.pauseVideo() : player.playVideo();
                break;
            case 'ArrowLeft':
                e.preventDefault();
                player.seekTo(Math.max(0, player.getCurrentTime() - 5), true);
                break;
            case 'ArrowRight':
                e.preventDefault();
                player.seekTo(player.getCurrentTime() + 5, true);
                break;
            case '+': case '=':
                e.preventDefault();
                { const idx = RATES.indexOf(currentSpeed);
                  if (idx < RATES.length - 1) setSpeed(RATES[idx + 1]); }
                break;
            case '-':
                e.preventDefault();
                { const idx = RATES.indexOf(currentSpeed);
                  if (idx > 0) setSpeed(RATES[idx - 1]); }
                break;
            case 't': case 'T':
                e.preventDefault();
                toggleTranscript();
                break;
            case 'l': case 'L':
                e.preventDefault();
                clearLoop();
                break;
        }
    });
}

/* ══════════════════════════════════════════════════════════════
   FEATURE 5 — Phrase loop
   ══════════════════════════════════════════════════════════════ */
function toggleLoop(startMs, durMs, btn) {
    if (loopStart === startMs) {
        clearLoop();
    } else {
        loopStart = startMs;
        loopEnd   = startMs + durMs;
        document.querySelectorAll('.tr-loop-btn').forEach(b => b.classList.remove('loop-active'));
        btn.classList.add('loop-active');
        if (player && typeof player.seekTo === 'function') player.seekTo(startMs / 1000, true);
    }
}

function clearLoop() {
    loopStart = null;
    loopEnd   = null;
    document.querySelectorAll('.tr-loop-btn').forEach(b => b.classList.remove('loop-active'));
}

/* ══════════════════════════════════════════════════════════════
   FEATURE 6 — Position memory
   ══════════════════════════════════════════════════════════════ */
function savePosition(videoId, posMs) {
    if (!videoId || posMs < 5000) return;
    try {
        const data = JSON.parse(localStorage.getItem('dualsubPos') || '{}');
        data[videoId] = Math.floor(posMs);
        // Keep at most 50 entries
        const keys = Object.keys(data);
        if (keys.length > 50) delete data[keys[0]];
        localStorage.setItem('dualsubPos', JSON.stringify(data));
    } catch (e) { /* silent */ }
}

function getSavedPosition(videoId) {
    try {
        const data = JSON.parse(localStorage.getItem('dualsubPos') || '{}');
        return data[videoId] || null;
    } catch (e) { return null; }
}

function startPositionSaving() {
    stopPositionSaving();
    _positionTimer = setInterval(() => {
        if (_currentVideoId && player && typeof player.getCurrentTime === 'function') {
            savePosition(_currentVideoId, player.getCurrentTime() * 1000);
        }
    }, 5000);
}

function stopPositionSaving() {
    if (_positionTimer) { clearInterval(_positionTimer); _positionTimer = null; }
}

function showResumeToast(posMs) {
    const toast = document.getElementById('resumeToast');
    if (!toast) return;
    document.getElementById('resumeTime').textContent = formatMs(posMs);
    toast.classList.remove('hidden');
    document.getElementById('btnResume').onclick = () => {
        if (player && typeof player.seekTo === 'function') player.seekTo(posMs / 1000, true);
        toast.classList.add('hidden');
    };
    document.getElementById('btnResumeIgnore').onclick = () => toast.classList.add('hidden');
    // Auto-dismiss after 8 s
    setTimeout(() => toast.classList.add('hidden'), 8000);
}

/* ══════════════════════════════════════════════════════════════
   FEATURE 7 — Transcript search
   ══════════════════════════════════════════════════════════════ */
function searchTranscript() {
    const q = (document.getElementById('transcriptSearch')?.value || '').trim().toLowerCase();
    searchMatches = [];
    searchIndex   = -1;
    document.querySelectorAll('#transcriptInner .tr-row').forEach(row => {
        row.classList.remove('tr-match', 'tr-match-current');
        if (!q) return;
        const t1 = (row.querySelector('.tr-text1')?.textContent || '').toLowerCase();
        const t2 = (row.querySelector('.tr-text2')?.textContent || '').toLowerCase();
        if (t1.includes(q) || t2.includes(q)) {
            row.classList.add('tr-match');
            searchMatches.push(parseInt(row.dataset.index, 10));
        }
    });
    if (searchMatches.length > 0) { searchIndex = 0; scrollToSearchMatch(); }
    updateSearchCount();
}

function navigateSearch(dir) {
    if (!searchMatches.length) return;
    searchIndex = (searchIndex + dir + searchMatches.length) % searchMatches.length;
    scrollToSearchMatch();
    updateSearchCount();
}

function scrollToSearchMatch() {
    document.querySelectorAll('#transcriptInner .tr-row').forEach(r => r.classList.remove('tr-match-current'));
    if (searchIndex < 0 || searchIndex >= searchMatches.length) return;
    const row = document.querySelector(`#transcriptInner .tr-row[data-index="${searchMatches[searchIndex]}"]`);
    if (row) {
        row.classList.add('tr-match-current');
        row.scrollIntoView({ block: 'center', behavior: 'smooth' });
    }
}

function updateSearchCount() {
    const el = document.getElementById('searchCount');
    if (!el) return;
    const q = document.getElementById('transcriptSearch')?.value || '';
    el.textContent = q
        ? (searchMatches.length > 0 ? `${searchIndex + 1}/${searchMatches.length}` : '0')
        : '';
}

/* ══════════════════════════════════════════════════════════════
   FEATURE 8 — Light / dark theme
   ══════════════════════════════════════════════════════════════ */
function initTheme() {
    if (currentTheme === 'light') document.body.classList.add('theme-light');
    updateThemeBtn();
}

function toggleTheme() {
    currentTheme = currentTheme === 'dark' ? 'light' : 'dark';
    document.body.classList.toggle('theme-light', currentTheme === 'light');
    localStorage.setItem('dualsubTheme', currentTheme);
    updateThemeBtn();
}

function updateThemeBtn() {
    const btn = document.getElementById('btnTheme');
    if (btn) btn.textContent = currentTheme === 'dark' ? '☀ THÈME' : '🌙 THÈME';
}

/* ══════════════════════════════════════════════════════════════
   FEATURE 9 — Personal stats (backend already done)
   ══════════════════════════════════════════════════════════════ */
async function loadUserStats() {
    try {
        const resp = await fetch('/api/users/me/stats');
        if (!resp.ok) return null;
        return await resp.json();
    } catch (e) { return null; }
}

/* ══════════════════════════════════════════════════════════════
   AUTO-PAUSE — pause at every sentence boundary for language learners
   ══════════════════════════════════════════════════════════════ */

function toggleFocusMode() {
    focusMode = !focusMode;
    _focusPauseSubIdx = -1;
    document.getElementById('btnFocus').classList.toggle('tb-active', focusMode);
    if (!focusMode) {
        // Exiting focus: hide overlay and ensure playback continues
        focusPaused = false;
        document.getElementById('focusOverlay').classList.add('hidden');
        if (player && typeof player.getPlayerState === 'function'
            && player.getPlayerState() !== YT.PlayerState.PLAYING) {
            player.playVideo();
        }
    }
}

// AUTO-PAUSE timing constant
// Minimum silence (ms) needed to move the pause point into the gap rather than
// keeping it at the end of the entry.
const FOCUS_MIN_SIL = 350;

/**
 * Called every sync tick when AUTO-PAUSE mode is on and not already paused.
 *
 * Uses speechEndMs (set by the backend from actual ASR fragment end times)
 * to locate the silence gap after each sentence and pauses midway through it.
 * Falls back to 80 ms before the next phrase when no meaningful silence is
 * present or when speechEndMs is unavailable.
 */
function checkFocusPause(nowMs) {
    // Use the longer subtitle list for boundary detection
    const subs = subtitles1.length >= subtitles2.length ? subtitles1 : subtitles2;
    if (subs.length < 2) return;

    // Binary search: last entry whose startMs ≤ nowMs
    let lo = 0, hi = subs.length - 1, best = -1;
    while (lo <= hi) {
        const mid = (lo + hi) >> 1;
        if (subs[mid].startMs <= nowMs) { best = mid; lo = mid + 1; }
        else hi = mid - 1;
    }
    if (best < 0 || best >= subs.length - 1) return; // not in a subtitle, or last one
    if (best === _focusPauseSubIdx) return;           // already paused at this boundary

    const s        = subs[best];
    const entryEnd = s.startMs + s.durationMs;          // = next entry start (gapless)

    // speechEndMs comes from ASR fragment end times saved before the pin-pass.
    // Fall back to entryEnd when the field is absent or zero (HTTP fallback path).
    const speechEnd = (s.speechEndMs && s.speechEndMs > s.startMs)
        ? s.speechEndMs : entryEnd;
    const silenceMs = entryEnd - speechEnd;

    // Pause midway through the silence when it is long enough to be perceptible,
    // otherwise hold until 80 ms before the next phrase starts.
    const pauseAt = silenceMs >= FOCUS_MIN_SIL
        ? speechEnd + silenceMs * 0.5
        : entryEnd - 80;

    if (nowMs >= pauseAt) {
        _focusPauseSubIdx = best;
        player.pauseVideo();
        focusPaused = true;
        document.getElementById('focusOverlay').classList.remove('hidden');
    }
}

function resumeFromFocus() {
    if (!focusPaused) return;
    focusPaused = false;
    document.getElementById('focusOverlay').classList.add('hidden');
    if (player && typeof player.playVideo === 'function') player.playVideo();
}

/* ══════════════════════════════════════════════════════════════
   HELP MODAL
   ══════════════════════════════════════════════════════════════ */
function showHelp() {
    document.getElementById('helpModal').classList.remove('hidden');
    localStorage.setItem('dualsubHelpSeen', '1');
}

function closeHelp() {
    document.getElementById('helpModal').classList.add('hidden');
}

/* ══════════════════════════════════════════════════════════════
   COPY VIDEO URL
   ══════════════════════════════════════════════════════════════ */
async function copyVideoUrl() {
    if (!_currentVideoId) return;
    const url = `https://www.youtube.com/watch?v=${_currentVideoId}`;
    const btn = document.getElementById('btnCopyUrl');
    try {
        await navigator.clipboard.writeText(url);
        const orig = btn.textContent;
        btn.textContent = '✓ COPIÉ !';
        btn.classList.add('tb-active');
        setTimeout(() => { btn.textContent = orig; btn.classList.remove('tb-active'); }, 2200);
    } catch (e) {
        // Fallback for browsers where clipboard API is blocked
        const tmp = document.createElement('input');
        tmp.value = url;
        document.body.appendChild(tmp);
        tmp.select();
        document.execCommand('copy');
        document.body.removeChild(tmp);
        const orig = btn.textContent;
        btn.textContent = '✓ COPIÉ !';
        btn.classList.add('tb-active');
        setTimeout(() => { btn.textContent = orig; btn.classList.remove('tb-active'); }, 2200);
    }
}

function renderStatsSection(stats) {
    if (!stats) {
        return '<p style="color:rgba(255,255,255,.35);font-size:.8rem">Statistiques non disponibles.</p>';
    }
    const maxWeek = Math.max(...stats.weeklyHistory.map(w => w.count), 1);
    const bars = stats.weeklyHistory.map(w => `
        <div class="stats-week-col">
            <div class="stats-bar-wrap">
                <div class="stats-bar-fill" style="height:${Math.round(w.count / maxWeek * 100)}%"></div>
            </div>
            <div class="stats-week-lbl">${esc(w.label)}</div>
            <div class="stats-week-cnt">${w.count}</div>
        </div>`).join('');

    const pairs = stats.topPairs.length
        ? `<div class="stats-chart-label">// PAIRES PRÉFÉRÉES</div>
           <div class="stats-pairs">` +
          stats.topPairs.map(p =>
            `<div class="stats-pair">
                <span class="stats-pair-name">${esc(p.pair)}</span>
                <span class="stats-pair-cnt">${p.count}×</span>
             </div>`).join('') +
          `</div>`
        : '';

    return `
        <div class="stats-kpi-row">
            <div class="stats-kpi">
                <div class="stats-kpi-val">${stats.totalVideos}</div>
                <div class="stats-kpi-lbl">Vidéos vues</div>
            </div>
            <div class="stats-kpi">
                <div class="stats-kpi-val">${stats.videosThisWeek}</div>
                <div class="stats-kpi-lbl">Cette semaine</div>
            </div>
        </div>
        <div class="stats-chart-label">// ACTIVITÉ (8 DERNIÈRES SEMAINES)</div>
        <div class="stats-week-chart">${bars}</div>
        ${pairs}`;
}
