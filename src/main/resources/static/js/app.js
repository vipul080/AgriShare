import { html, mount, $, toast } from './dom.js';
import { t, setLang, initialLang, loadLanguages, currentLang } from './i18n.js';
import { session, get, put } from './api.js';
import * as views from './views.js';

// Hash routes keep this a static site: Spring serves index.html, the browser does the rest.
const routes = [
    [/^\/?$/, views.home],
    [/^\/browse$/, views.browse],
    [/^\/equipment\/new$/, views.equipmentForm, { auth: true }],
    [/^\/equipment\/(\d+)\/edit$/, views.equipmentForm, { auth: true }],
    [/^\/equipment\/(\d+)$/, views.equipmentDetail],
    [/^\/login$/, views.login],
    [/^\/register$/, views.register],
    [/^\/dashboard(?:\/(bookings|requests|equipment|notifications))?$/, views.dashboard, { auth: true }],
    [/^\/profile$/, views.profile, { auth: true }],
    [/^\/delete-account$/, views.deleteAccount],
];

let unreadTimer;

export async function route() {
    const path = (location.hash.slice(1) || '/').split('?')[0];
    const main = $('#main');
    for (const [pattern, view, opts = {}] of routes) {
        const match = path.match(pattern);
        if (!match) continue;
        if (opts.auth && !session.loggedIn) {
            sessionStorage.setItem('agrishare.after-login', location.hash);
            location.hash = '#/login';
            return;
        }
        renderNav();
        mount(main, html`<div class="loading">${t('ui.common.loading')}</div>`);
        try {
            await view(main, ...match.slice(1));
        } catch (e) {
            console.error(e);
            mount(main, html`<div class="empty">${e.message || t('ui.common.error')}</div>`);
        }
        window.scrollTo(0, 0);
        return;
    }
    location.hash = '#/';
}

export function renderNav() {
    const path = location.hash.slice(1) || '/';
    const active = (prefix) => (path.startsWith(prefix) ? 'active' : '');
    const langs = views.cachedLanguages();
    const langSelect = html`
        <select id="lang-select" aria-label="${t('ui.lang.label')}">
            ${langs.map((l) => html`<option value="${l.code}" ${l.code === currentLang() ? 'selected' : ''}>${l.nativeName}</option>`)}
        </select>`;

    mount($('#nav'), session.loggedIn
        ? html`
            <a href="#/browse" class="${active('/browse')}">🔍 <span class="nav-label">${t('ui.nav.browse')}</span></a>
            <a href="#/dashboard" class="${active('/dashboard')}">📋 <span class="nav-label">${t('ui.nav.dashboard')}</span></a>
            <a href="#/dashboard/notifications" aria-label="${t('ui.nav.notifications')}">🔔<span id="unread" class="badge-dot" hidden></span></a>
            <a href="#/profile" class="${active('/profile')}">👤 <span class="nav-label">${t('ui.nav.profile')}</span></a>
            ${langSelect}`
        : html`
            <a href="#/browse" class="${active('/browse')}">🔍 <span class="nav-label">${t('ui.nav.browse')}</span></a>
            <a href="#/login" class="${active('/login')}">${t('ui.auth.login')}</a>
            ${langSelect}`);

    $('#lang-select').addEventListener('change', async (e) => {
        await setLang(e.target.value);
        if (session.loggedIn) {
            // remember the choice on the account so pushes arrive in this language too
            put('/api/users/me', { ...(await get('/api/users/me')), preferredLanguage: currentLang() }).catch(() => {});
            session.updateUser({ preferredLanguage: currentLang() });
        }
        renderFooter();
        route();
    });
    refreshUnread();
}

export async function refreshUnread() {
    clearTimeout(unreadTimer);
    if (!session.loggedIn) return;
    try {
        const { count } = await get('/api/notifications/unread-count');
        const badge = $('#unread');
        if (badge) {
            badge.hidden = count === 0;
            badge.textContent = count > 99 ? '99+' : count;
        }
    } catch { /* offline — try again later */ }
    unreadTimer = setTimeout(refreshUnread, 60_000);
}

function renderFooter() {
    mount($('#footer'), html`
        <div>${t('ui.footer.made')}</div>
        <div style="margin-top:6px">
            <a href="/privacy.html">${t('ui.footer.privacy')}</a>
            <a href="#/delete-account">${t('ui.footer.deleteAccount')}</a>
        </div>`);
}

window.addEventListener('agrishare:logout', () => {
    toast(t('ui.auth.sessionExpired'), true);
    location.hash = '#/login';
});
window.addEventListener('hashchange', route);

(async function start() {
    const saved = session.user?.preferredLanguage;
    await setLang(saved || initialLang());
    await views.preload(await loadLanguages());
    renderFooter();
    route();
})();
