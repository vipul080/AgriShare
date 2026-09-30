import { html, mount, $, toast, openModal, closeModal } from './dom.js';
import { t, setLang, initialLang, loadLanguages, currentLang, hasChosenLang, stopSpeaking } from './i18n.js';
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
    stopSpeaking();
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

/** Bottom tab bar: four big icons, the way WhatsApp and PhonePe work. */
export function renderNav() {
    const path = location.hash.slice(1) || '/';
    const tab = (href, icon, key, active, extra = '') => html`
        <a href="${href}" class="${active ? 'active' : ''}">
            <span class="ic">${icon}</span>${t(key)}${extra}
        </a>`;
    const onBookings = path.startsWith('/dashboard');

    mount($('#tabbar'), html`
        ${tab('#/', '🏠', 'ui.nav.home', path === '/' || path === '')}
        ${tab('#/browse', '🔍', 'ui.nav.find', path.startsWith('/browse') || path.startsWith('/equipment/'))}
        ${tab('#/dashboard', '📋', 'ui.nav.bookings', onBookings, html`<span id="unread" class="badge-dot" hidden></span>`)}
        ${session.loggedIn
            ? tab('#/profile', '👤', 'ui.nav.me', path.startsWith('/profile'))
            : tab('#/login', '👤', 'ui.auth.login', path.startsWith('/login') || path.startsWith('/register'))}`);

    const current = views.cachedLanguages().find((l) => l.code === currentLang());
    $('#lang-btn').textContent = `🌐 ${current ? current.nativeName : ''}`;
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

/** Big script tiles — the farmer recognises their own script, no reading of English needed. */
export function chooseLanguage(firstRun = false) {
    return new Promise((resolve) => {
        const dialog = openModal(html`
            <h2 class="center">🌐</h2>
            <div class="lang-grid">
                ${views.cachedLanguages().map((l) => html`
                    <button type="button" class="lang-tile ${l.code === currentLang() ? 'active' : ''}" data-code="${l.code}">${l.nativeName}</button>`)}
            </div>`);
        if (firstRun) dialog.addEventListener('cancel', (e) => e.preventDefault(), { once: true });
        dialog.querySelector('.lang-grid').onclick = async (e) => {
            const code = e.target.closest('.lang-tile')?.dataset.code;
            if (!code) return;
            await setLang(code);
            closeModal();
            if (session.loggedIn) {
                // remember it on the account so push notifications arrive in this language too
                get('/api/users/me')
                    .then((me) => put('/api/users/me', { ...me, preferredLanguage: code }))
                    .catch(() => {});
                session.updateUser({ preferredLanguage: code });
            }
            renderFooter();
            await route();
            resolve();
        };
    });
}

function renderFooter() {
    mount($('#footer'), html`
        <div>${t('ui.footer.made')}</div>
        <div style="margin-top:6px">
            <a href="/terms.html">${t('ui.footer.terms')}</a>
            <a href="/privacy.html">${t('ui.footer.privacy')}</a>
            <a href="#/delete-account">${t('ui.footer.deleteAccount')}</a>
        </div>`);
}

window.addEventListener('agrishare:logout', () => {
    toast(t('ui.auth.sessionExpired'), true);
    location.hash = '#/login';
});
window.addEventListener('hashchange', route);
$('#lang-btn').addEventListener('click', () => chooseLanguage());

(async function start() {
    const saved = session.user?.preferredLanguage;
    const firstRun = !saved && !hasChosenLang(); // check before setLang() remembers anything
    await setLang(saved || initialLang());
    await views.preload(await loadLanguages());
    renderFooter();
    await route();
    if (firstRun) chooseLanguage(true);
})();
