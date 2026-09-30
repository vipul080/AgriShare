import { html, mount, $, $$, toast, openModal, closeModal, formData, showFieldErrors } from './dom.js';
import { t, money, date, dateTime, setLang, currentLang } from './i18n.js';
import { session, get, post, put, del, ApiError } from './api.js';
import { renderNav, refreshUnread } from './app.js';

const HOME = { lat: 30.4384, lng: 77.6245 }; // Paonta Sahib — default map centre
const ICONS = {
    TRACTOR: '🚜', ROTAVATOR: '⚙️', CULTIVATOR: '🌱', PLOUGH: '🪨', SEED_DRILL: '🌾', HARVESTER: '🌾',
    THRESHER: '🌾', SPRAYER: '💧', WATER_PUMP: '🚰', POWER_TILLER: '🚜', TROLLEY: '🛻', OTHER: '🔧',
};
const STATUS_PILL = {
    AWAITING_PAYMENT: 'amber', REQUESTED: 'amber', CONFIRMED: '', REJECTED: 'red',
    CANCELLED: 'grey', COMPLETED: 'blue', EXPIRED: 'grey',
};

let languages = [];
let categories = [];

export async function preload(langs) {
    languages = langs;
    categories = await get('/api/equipment/categories');
}
export const cachedLanguages = () => languages;

/* ---------------------------------------------------------------- helpers */

function today() {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}

function daysBetween(start, end) {
    return Math.round((new Date(end + 'T00:00:00') - new Date(start + 'T00:00:00')) / 86_400_000) + 1;
}

function errorText(e) {
    if (e instanceof ApiError && e.code === 'offline') return t('ui.common.offline');
    return e.message || t('ui.common.error');
}

/** Runs an async action, showing the translated server error on failure. */
async function attempt(fn, form) {
    try {
        return await fn();
    } catch (e) {
        if (form && e instanceof ApiError) showFieldErrors(form, e.fields);
        toast(errorText(e), true);
        return undefined;
    }
}

function thumb(imageUrl, category, cls = 'thumb') {
    return html`<div class="${cls}">${imageUrl ? html`<img src="${imageUrl}" alt="" loading="lazy">` : ICONS[category] || '🔧'}</div>`;
}

function stars(avg, count) {
    if (!count) return html`<span class="pill grey">${t('ui.equipment.new')}</span>`;
    return html`<span class="stars">★</span> ${t('ui.equipment.rating', { avg: avg.toFixed(1), count })}`;
}

function categoryOptions(selected, placeholderKey) {
    return html`
        ${placeholderKey ? html`<option value="">${t(placeholderKey)}</option>` : ''}
        ${categories.map((c) => html`<option value="${c}" ${c === selected ? 'selected' : ''}>${t('ui.category.' + c)}</option>`)}`;
}

function locate() {
    return new Promise((resolve, reject) => {
        if (!navigator.geolocation) return reject(new Error('no geolocation'));
        navigator.geolocation.getCurrentPosition(
            (p) => resolve({ lat: p.coords.latitude, lng: p.coords.longitude }),
            reject,
            { enableHighAccuracy: false, timeout: 10_000, maximumAge: 300_000 });
    });
}

function makeMap(el, center, zoom = 11) {
    const map = L.map(el, { scrollWheelZoom: false }).setView([center.lat, center.lng], zoom);
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
        maxZoom: 18,
        attribution: '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>',
    }).addTo(map);
    return map;
}

function goAfterLogin() {
    const next = sessionStorage.getItem('agrishare.after-login');
    sessionStorage.removeItem('agrishare.after-login');
    location.hash = next || '#/browse';
}

/* ---------------------------------------------------------------- home */

export async function home(main) {
    const stats = await get('/api/stats/public').catch(() => null);
    mount(main, html`
        <section class="hero">
            <h1>${t('ui.home.title')}</h1>
            <p>${t('ui.home.subtitle')}</p>
            <div class="row">
                <a class="btn amber" href="#/browse">🔍 ${t('ui.home.find')}</a>
                <a class="btn" href="#/equipment/new">🚜 ${t('ui.home.list')}</a>
            </div>
        </section>
        ${stats ? html`
        <section class="grid three" style="margin-bottom:18px">
            <div class="card stat"><b>${stats.farmers}</b>${t('ui.home.stats.farmers')}</div>
            <div class="card stat"><b>${stats.machines}</b>${t('ui.home.stats.machines')}</div>
            <div class="card stat"><b>${stats.completedRentals}</b>${t('ui.home.stats.rentals')}</div>
        </section>` : ''}
        <h2>${t('ui.home.how')}</h2>
        <section class="grid three">
            ${[1, 2, 3].map((n) => html`
                <div class="card">
                    <div class="step-num">${n}</div>
                    <h3>${t(`ui.home.step${n}.title`)}</h3>
                    <p class="muted">${t(`ui.home.step${n}.text`)}</p>
                </div>`)}
        </section>`);
}

/* ---------------------------------------------------------------- browse */

const search = { lat: null, lng: null, radiusKm: 25, category: '', q: '' };

export async function browse(main) {
    mount(main, html`
        <h1>${t('ui.nav.browse')}</h1>
        <form id="filters" class="filters">
            <button type="button" id="near-me" class="btn primary wide">📍 ${t('ui.browse.useLocation')}</button>
            <div>
                <label for="radius">${t('ui.browse.radius')}</label>
                <select id="radius" name="radiusKm">
                    ${[5, 10, 25, 50, 100].map((km) => html`<option value="${km}" ${km === search.radiusKm ? 'selected' : ''}>${t('ui.browse.km', { n: km })}</option>`)}
                </select>
            </div>
            <div>
                <label for="category">${t('ui.form.category')}</label>
                <select id="category" name="category">${categoryOptions(search.category, 'ui.browse.allCategories')}</select>
            </div>
            <div class="wide">
                <label for="q">${t('ui.browse.search')}</label>
                <input id="q" name="q" type="search" value="${search.q}" placeholder="${t('ui.browse.searchPlaceholder')}">
            </div>
            <button class="btn wide" type="submit">${t('ui.browse.searchButton')}</button>
        </form>
        <div id="map" class="map" style="margin-bottom:12px"></div>
        <p id="result-line" class="muted"></p>
        <div id="results" class="grid cards"></div>`);

    const map = makeMap($('#map'), search.lat ? search : HOME, search.lat ? 11 : 10);
    const markers = L.layerGroup().addTo(map);
    let meMarker = null;

    async function load() {
        const params = new URLSearchParams();
        if (search.lat != null) {
            params.set('lat', search.lat);
            params.set('lng', search.lng);
            params.set('radiusKm', search.radiusKm);
        }
        if (search.category) params.set('category', search.category);
        if (search.q) params.set('q', search.q);

        const items = await attempt(() => get('/api/equipment?' + params));
        if (!items) return;

        $('#result-line').textContent = search.lat != null
            ? t('ui.browse.results', { n: items.length, km: search.radiusKm })
            : t('ui.browse.newest');
        mount($('#results'), items.length
            ? items.map(equipmentCard)
            : html`<div class="empty card">${t('ui.browse.empty')}</div>`);

        markers.clearLayers();
        items.forEach((e) => {
            L.marker([e.latitude, e.longitude]).addTo(markers)
                .bindPopup(`<b>${escapeText(e.name)}</b><br>₹${escapeText(money(e.pricePerDay))}<br><a href="#/equipment/${e.id}">${escapeText(t('ui.browse.view'))}</a>`);
        });
        if (search.lat != null) {
            if (meMarker) meMarker.remove();
            meMarker = L.circle([search.lat, search.lng], { radius: search.radiusKm * 1000, color: '#2f6b3a', weight: 2, fillOpacity: 0.05 }).addTo(map);
            map.fitBounds(meMarker.getBounds());
        } else if (items.length) {
            map.fitBounds(L.latLngBounds(items.map((e) => [e.latitude, e.longitude])).pad(0.2), { maxZoom: 12 });
        }
    }

    $('#near-me').addEventListener('click', async () => {
        try {
            Object.assign(search, await locate());
            load();
        } catch {
            toast(t('ui.browse.locationDenied'), true);
        }
    });
    $('#filters').addEventListener('submit', (e) => {
        e.preventDefault();
        const f = formData(e.target);
        search.radiusKm = Number(f.radiusKm);
        search.category = f.category || '';
        search.q = f.q || '';
        load();
    });
    $('#radius').addEventListener('change', () => $('#filters').requestSubmit());
    $('#category').addEventListener('change', () => $('#filters').requestSubmit());
    load();
}

function escapeText(s) {
    const div = document.createElement('div');
    div.textContent = s;
    return div.innerHTML;
}

function equipmentCard(e) {
    return html`
        <a class="card eq-card" href="#/equipment/${e.id}">
            ${thumb(e.imageUrl, e.category)}
            <div class="body">
                <h3>${e.name}</h3>
                <div class="muted small">${t('ui.category.' + e.category)}${e.address ? html` · ${e.address}` : ''}</div>
                <div class="row" style="margin-top:6px">
                    <span class="price">${t('ui.equipment.perDay', { price: money(e.pricePerDay) })}</span>
                    <span class="spacer"></span>
                    <span class="small">${stars(e.averageRating, e.reviewCount)}</span>
                </div>
                <div class="row small" style="margin-top:4px">
                    ${e.distanceKm != null ? html`<span>📍 ${t('ui.equipment.distance', { km: e.distanceKm })}</span>` : ''}
                    ${!e.available ? html`<span class="pill red">${t('ui.equipment.unavailable')}</span>` : ''}
                </div>
            </div>
        </a>`;
}

/* ---------------------------------------------------------------- equipment detail + booking */

export async function equipmentDetail(main, id) {
    const [e, reviews, booked] = await Promise.all([
        get(`/api/equipment/${id}`),
        get(`/api/equipment/${id}/reviews`),
        get(`/api/equipment/${id}/booked-dates`),
    ]);
    const mine = session.user && session.user.id === e.owner.id;

    mount(main, html`
        <p><a href="#/browse">← ${t('ui.common.back')}</a></p>
        <div class="grid two">
            <div class="stack">
                ${thumb(e.imageUrl, e.category, 'detail-photo card')}
                <div class="card">
                    <h1>${e.name}</h1>
                    <div class="row">
                        <span class="pill">${t('ui.category.' + e.category)}</span>
                        <span>${stars(e.averageRating, e.reviewCount)}</span>
                    </div>
                    <p class="price" style="margin-top:10px">${t('ui.equipment.perDay', { price: money(e.pricePerDay) })}</p>
                    ${e.description ? html`<p style="white-space:pre-line">${e.description}</p>` : ''}
                    <p class="muted">👤 ${t('ui.equipment.owner')}: ${e.owner.name}${e.address ? html`<br>📍 ${e.address}` : ''}</p>
                    <div id="detail-map" class="map"></div>
                </div>
            </div>
            <div class="stack">
                <div class="card" id="book-card"></div>
                <div class="card">
                    <h2>${t('ui.reviews.title')}</h2>
                    ${reviews.reviews.length ? reviews.reviews.map((r) => html`
                        <div style="border-top:1px solid var(--line);padding:10px 0">
                            <div class="row"><b>${r.reviewer.name}</b><span class="spacer"></span><span class="stars">${'★'.repeat(r.rating)}${'☆'.repeat(5 - r.rating)}</span></div>
                            ${r.comment ? html`<p style="margin:4px 0 0">${r.comment}</p>` : ''}
                            <div class="muted small">${date(r.createdAt)}</div>
                        </div>`) : html`<p class="muted">${t('ui.reviews.empty')}</p>`}
                </div>
            </div>
        </div>`);

    const map = makeMap($('#detail-map'), { lat: e.latitude, lng: e.longitude }, 13);
    L.marker([e.latitude, e.longitude]).addTo(map);

    const card = $('#book-card');
    const takenList = booked.length ? html`
        <p class="small muted" style="margin:10px 0 0">${t('ui.book.taken')}</p>
        <ul class="booked-list">${booked.map((b) => html`<li class="pill amber">${date(b.startDate)} – ${date(b.endDate)}</li>`)}</ul>` : '';

    if (mine) {
        mount(card, html`<p>${t('ui.book.own')}</p>${takenList}<a class="btn block" href="#/equipment/${e.id}/edit">✏️ ${t('ui.book.edit')}</a>`);
        return;
    }
    if (!e.available) {
        mount(card, html`<p class="pill red">${t('ui.equipment.unavailable')}</p>`);
        return;
    }
    if (!session.loggedIn) {
        sessionStorage.setItem('agrishare.after-login', location.hash);
        mount(card, html`<h2>${t('ui.book.title')}</h2>${takenList}<a class="btn primary block" href="#/login">${t('ui.book.loginFirst')}</a>`);
        return;
    }

    mount(card, html`
        <h2>${t('ui.book.title')}</h2>
        <form id="book-form" novalidate>
            <div class="grid two">
                <div class="field"><label for="startDate">${t('ui.book.start')}</label>
                    <input id="startDate" name="startDate" type="date" min="${today()}" value="${today()}" required></div>
                <div class="field"><label for="endDate">${t('ui.book.end')}</label>
                    <input id="endDate" name="endDate" type="date" min="${today()}" value="${today()}" required></div>
            </div>
            ${takenList}
            <div class="field" style="margin-top:10px">
                <label>${t('ui.book.payment')}</label>
                <label class="choice"><input type="radio" name="paymentMethod" value="CASH" checked> 💵 ${t('ui.book.cash')}</label>
                <label class="choice"><input type="radio" name="paymentMethod" value="ONLINE"> 📱 ${t('ui.book.online')}</label>
            </div>
            <div class="field"><label for="note">${t('ui.book.note')}</label>
                <textarea id="note" name="note" maxlength="500"></textarea></div>
            <p id="total" class="price"></p>
            <button class="btn primary block" type="submit">${t('ui.book.submit')}</button>
        </form>`);

    const form = $('#book-form');
    const updateTotal = () => {
        const { startDate, endDate } = formData(form);
        const days = startDate && endDate ? daysBetween(startDate, endDate) : 0;
        $('#total').textContent = days > 0 ? t('ui.book.total', { days, total: money(days * Number(e.pricePerDay)) }) : '';
    };
    form.startDate.addEventListener('change', () => {
        if (form.endDate.value < form.startDate.value) form.endDate.value = form.startDate.value;
        form.endDate.min = form.startDate.value;
        updateTotal();
    });
    form.endDate.addEventListener('change', updateTotal);
    updateTotal();

    form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const data = { ...formData(form), equipmentId: Number(id) };
        const booking = await attempt(() => post('/api/bookings', data), form);
        if (!booking) return;
        if (booking.checkout) {
            await checkout(booking);
        } else {
            toast(t('ui.book.sent'));
        }
        location.hash = '#/dashboard/bookings';
    });
}

/* ---------------------------------------------------------------- payments */

async function checkout(booking) {
    const c = booking.checkout;
    if (c.mode === 'razorpay') return razorpayCheckout(booking);

    // Mock mode: a stand-in dialog so the whole flow can be demoed without real money.
    return new Promise((resolve) => {
        const dialog = openModal(html`
            <h2>${t('ui.pay.title', { amount: money(c.amountPaise / 100) })}</h2>
            <p>${booking.equipment.name} · ${date(booking.startDate)} – ${date(booking.endDate)}</p>
            <p class="muted small">${t('ui.pay.held')}</p>
            <p class="pill amber">${t('ui.pay.mockNote')}</p>
            <div class="row" style="margin-top:14px">
                <button class="btn primary" id="pay-now">${t('ui.pay.payNow')}</button>
                <button class="btn" id="pay-later">${t('ui.common.close')}</button>
            </div>`);
        $('#pay-later', dialog).onclick = () => { closeModal(); resolve(); };
        $('#pay-now', dialog).onclick = async () => {
            const done = await attempt(() => post(`/api/bookings/${booking.id}/payment/verify`, {
                orderId: c.orderId,
                paymentId: 'pay_mock_' + Math.random().toString(36).slice(2, 12),
                signature: 'mock',
            }));
            closeModal();
            if (done) toast(t('ui.pay.success'));
            resolve();
        };
    });
}

function loadRazorpay() {
    if (window.Razorpay) return Promise.resolve();
    return new Promise((resolve, reject) => {
        const s = document.createElement('script');
        s.src = 'https://checkout.razorpay.com/v1/checkout.js';
        s.onload = resolve;
        s.onerror = reject;
        document.head.appendChild(s);
    });
}

async function razorpayCheckout(booking) {
    const c = booking.checkout;
    await loadRazorpay();
    return new Promise((resolve) => {
        const rzp = new window.Razorpay({
            key: c.keyId,
            amount: c.amountPaise,
            currency: c.currency,
            order_id: c.orderId,
            name: 'AgriShare',
            description: booking.equipment.name,
            handler: async (resp) => {
                const done = await attempt(() => post(`/api/bookings/${booking.id}/payment/verify`, resp));
                if (done) toast(t('ui.pay.success'));
                resolve();
            },
            modal: { ondismiss: resolve },
            theme: { color: '#2f6b3a' },
        });
        rzp.open();
    });
}

/* ---------------------------------------------------------------- auth */

export async function login(main) {
    mount(main, html`
        <div class="card" style="max-width:440px;margin:0 auto">
            <h1>${t('ui.auth.login')}</h1>
            <form id="login-form" novalidate>
                <div class="field"><label for="identifier">${t('ui.auth.identifier')}</label>
                    <input id="identifier" name="identifier" inputmode="email" autocomplete="username" required></div>
                <div class="field"><label for="password">${t('ui.auth.password')}</label>
                    <input id="password" name="password" type="password" autocomplete="current-password" required></div>
                <button class="btn primary block" type="submit">${t('ui.auth.login')}</button>
            </form>
            <p class="center" style="margin-top:14px"><a href="#/register">${t('ui.auth.noAccount')}</a></p>
        </div>`);
    $('#login-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const auth = await attempt(() => post('/api/auth/login', formData(e.target)), e.target);
        if (!auth) return;
        await signedIn(auth);
    });
}

async function signedIn(auth) {
    session.save(auth);
    if (auth.preferredLanguage && auth.preferredLanguage !== currentLang()) await setLang(auth.preferredLanguage);
    renderNav();
    goAfterLogin();
}

export async function register(main) {
    mount(main, html`
        <div class="card" style="max-width:480px;margin:0 auto">
            <h1>${t('ui.auth.register')}</h1>
            <form id="register-form" novalidate>
                <div class="field"><label for="name">${t('ui.auth.name')}</label>
                    <input id="name" name="name" autocomplete="name" required></div>
                <div class="field"><label for="phone">${t('ui.auth.phone')}</label>
                    <input id="phone" name="phone" type="tel" inputmode="numeric" maxlength="10" autocomplete="tel-national" required></div>
                <div class="field"><label for="email">${t('ui.auth.email')}</label>
                    <input id="email" name="email" type="email" autocomplete="email"></div>
                <div class="field"><label for="password">${t('ui.auth.password')} <span class="hint">${t('ui.auth.passwordHint')}</span></label>
                    <input id="password" name="password" type="password" autocomplete="new-password" required></div>
                <div class="field"><label for="role">${t('ui.profile.role')}</label>
                    <select id="role" name="role">
                        ${['BOTH', 'RENTER', 'OWNER'].map((r) => html`<option value="${r}">${t('ui.role.' + r)}</option>`)}
                    </select></div>
                <div class="field">
                    <button type="button" id="reg-locate" class="btn block">📍 ${t('ui.auth.shareLocation')}</button>
                    <div id="reg-located" class="hint" hidden>✓ ${t('ui.auth.locationSet')}</div>
                </div>
                <button class="btn primary block" type="submit">${t('ui.auth.register')}</button>
            </form>
            <p class="center" style="margin-top:14px"><a href="#/login">${t('ui.auth.haveAccount')}</a></p>
        </div>`);

    let coords = null;
    $('#reg-locate').addEventListener('click', async () => {
        try {
            coords = await locate();
            $('#reg-located').hidden = false;
        } catch {
            toast(t('ui.browse.locationDenied'), true);
        }
    });
    $('#register-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const data = { ...formData(e.target), preferredLanguage: currentLang() };
        if (coords) Object.assign(data, { latitude: coords.lat, longitude: coords.lng });
        const auth = await attempt(() => post('/api/auth/register', data), e.target);
        if (auth) await signedIn(auth);
    });
}

/* ---------------------------------------------------------------- dashboard */

export async function dashboard(main, tab = 'bookings') {
    const tabs = ['bookings', 'requests', 'equipment', 'notifications'];
    mount(main, html`
        <h1>${t('ui.dash.title')}</h1>
        <nav class="tabs">
            ${tabs.map((name) => html`<a href="#/dashboard/${name}" class="${name === tab ? 'active' : ''}">${t('ui.dash.' + name)}</a>`)}
        </nav>
        <div id="tab" class="stack"><div class="loading">${t('ui.common.loading')}</div></div>`);
    const el = $('#tab');

    if (tab === 'bookings' || tab === 'requests') {
        const asOwner = tab === 'requests';
        const list = await get(asOwner ? '/api/bookings/incoming' : '/api/bookings/mine');
        mount(el, list.length
            ? list.map((b) => bookingCard(b, asOwner))
            : html`<div class="card empty">${t(asOwner ? 'ui.dash.noRequests' : 'ui.dash.noBookings')}
                ${asOwner ? '' : html`<p style="margin-top:12px"><a class="btn primary" href="#/browse">${t('ui.home.find')}</a></p>`}</div>`);
        el.onclick = (ev) => {
            const button = ev.target.closest('button[data-action]');
            if (button) bookingAction(button.dataset.action, list.find((b) => b.id === Number(button.dataset.id)), asOwner);
        };
    } else if (tab === 'equipment') {
        const list = await get('/api/equipment/mine');
        mount(el, html`
            <p><a class="btn primary" href="#/equipment/new">${t('ui.dash.addEquipment')}</a></p>
            ${list.length ? html`<div class="grid cards">${list.map(equipmentCard)}</div>` : html`<div class="card empty">${t('ui.dash.noEquipment')}</div>`}`);
    } else {
        const list = await get('/api/notifications');
        mount(el, html`
            ${list.some((n) => !n.read) ? html`<p><button class="btn small" id="read-all">${t('ui.dash.markAllRead')}</button></p>` : ''}
            ${list.length ? list.map((n) => html`
                <a class="card notif ${n.read ? '' : 'unread'}" href="#" data-id="${n.id}" data-type="${n.type}" data-owner="${n.params.owner || ''}" style="text-decoration:none;color:inherit">
                    <div style="flex:1">
                        <b>${t(`notification.${n.type}.title`)}</b>
                        <div>${notificationBody(n)}</div>
                    </div>
                    <span class="muted small when">${dateTime(n.createdAt)}</span>
                </a>`) : html`<div class="card empty">${t('ui.dash.noNotifications')}</div>`}`);
        $('#read-all')?.addEventListener('click', async () => {
            await attempt(() => post('/api/notifications/read-all'));
            refreshUnread();
            dashboard(main, 'notifications');
        });
        el.addEventListener('click', async (ev) => {
            const item = ev.target.closest('a.notif');
            if (!item) return;
            ev.preventDefault();
            post(`/api/notifications/${item.dataset.id}/read`).catch(() => {});
            // owners act on requests; renters follow their own bookings
            const ownerSide = item.dataset.type === 'BOOKING_REQUESTED'
                || (['BOOKING_CANCELLED', 'BOOKING_EXPIRED'].includes(item.dataset.type) && item.dataset.owner === session.user?.name);
            location.hash = ownerSide ? '#/dashboard/requests' : '#/dashboard/bookings';
        });
    }
}

/** Same {named} placeholders as the server-side push text; dates shown in the reader's language. */
function notificationBody(n) {
    const p = { ...n.params, start: date(n.params.start), end: date(n.params.end) };
    return t(`notification.${n.type}.body`, p);
}

function bookingCard(b, asOwner) {
    const other = asOwner ? b.renter : b.owner;
    const otherPhone = asOwner ? b.renterPhone : b.ownerPhone;
    const started = today() >= b.startDate;
    const actions = [];

    if (!asOwner) {
        if (b.status === 'AWAITING_PAYMENT') actions.push(['pay', 'primary', 'ui.booking.pay']);
        if (['AWAITING_PAYMENT', 'REQUESTED'].includes(b.status) || (b.status === 'CONFIRMED' && !started)) {
            actions.push(['cancel', 'danger', 'ui.booking.cancel']);
        }
    } else {
        if (b.status === 'REQUESTED') actions.push(['approve', 'primary', 'ui.booking.approve'], ['reject', 'danger', 'ui.booking.reject']);
        if (b.status === 'CONFIRMED' && started) actions.push(['complete', 'primary', 'ui.booking.complete']);
        if (b.status === 'CONFIRMED' && !started) actions.push(['cancel', 'danger', 'ui.booking.cancel']);
    }
    if (b.status === 'COMPLETED' && !b.reviewedByMe) actions.push(['review', 'amber', 'ui.booking.rate']);

    return html`
        <div class="card booking-card">
            <div class="top">
                ${thumb(b.equipment.imageUrl, b.equipment.category, 'mini')}
                <div style="flex:1;min-width:0">
                    <div class="row"><b><a href="#/equipment/${b.equipment.id}">${b.equipment.name}</a></b>
                        <span class="spacer"></span><span class="pill ${STATUS_PILL[b.status]}">${t('ui.status.' + b.status)}</span></div>
                    <div>${t('ui.booking.dates', { start: date(b.startDate), end: date(b.endDate), days: b.days })}</div>
                    <div class="row small">
                        <span>${t('ui.booking.total', { total: money(b.totalAmount) })}</span>
                        <span class="pill grey">${t('ui.payment.' + b.paymentStatus)}</span>
                    </div>
                    <div class="muted small">${t(asOwner ? 'ui.booking.renter' : 'ui.booking.owner')}: ${other.name}</div>
                </div>
            </div>
            ${b.note ? html`<p class="small" style="margin:8px 0 0">💬 ${b.note}</p>` : ''}
            ${b.rejectReason ? html`<p class="small" style="margin:8px 0 0">${t('ui.booking.reason', { reason: b.rejectReason })}</p>` : ''}
            ${otherPhone ? html`
                <div class="phone-box row">
                    <span>📞 ${other.name}: <b>${otherPhone}</b></span>
                    <span class="spacer"></span>
                    <a class="btn small primary" href="tel:+91${otherPhone}">${t('ui.booking.call')}</a>
                </div>
                ${b.paymentMethod === 'CASH' && !asOwner && b.status === 'CONFIRMED' ? html`<p class="small muted" style="margin:6px 0 0">${t('ui.booking.cashNote')}</p>` : ''}` : ''}
            ${b.status === 'COMPLETED' && b.reviewedByMe ? html`<p class="small muted" style="margin:8px 0 0">${t('ui.booking.reviewed')}</p>` : ''}
            ${actions.length ? html`<div class="actions">
                ${actions.map(([action, style, key]) => html`<button class="btn small ${style}" data-action="${action}" data-id="${b.id}">${t(key)}</button>`)}
            </div>` : ''}
        </div>`;
}

async function bookingAction(action, b, asOwner) {
    const reload = () => dashboard($('#main'), asOwner ? 'requests' : 'bookings');
    const base = `/api/bookings/${b.id}`;

    if (action === 'pay') {
        const fresh = await attempt(() => get(base));
        if (fresh?.checkout) await checkout(fresh);
        return reload();
    }
    if (action === 'review') return reviewDialog(b, asOwner, reload);
    if (action === 'reject') {
        const dialog = openModal(html`
            <h2>${t('ui.booking.reject')}</h2>
            <div class="field"><label for="reason">${t('ui.booking.rejectReason')}</label><textarea id="reason" maxlength="500"></textarea></div>
            <div class="row"><button class="btn danger solid" id="ok">${t('ui.booking.reject')}</button><button class="btn" id="no">${t('ui.common.cancel')}</button></div>`);
        $('#no', dialog).onclick = closeModal;
        $('#ok', dialog).onclick = async () => {
            const done = await attempt(() => post(`${base}/reject`, { reason: $('#reason', dialog).value || null }));
            closeModal();
            if (done) reload();
        };
        return;
    }
    if (action === 'cancel' && !confirm(t('ui.booking.confirmCancel'))) return;

    const done = await attempt(() => post(`${base}/${action}`));
    if (done) {
        toast(t('ui.status.' + done.status));
        reload();
    }
}

function reviewDialog(b, asOwner, done) {
    const who = asOwner ? b.renter.name : b.owner.name;
    let rating = 0;
    const dialog = openModal(html`
        <h2>${t('ui.review.title', { name: who })}</h2>
        <div class="star-input" id="stars">${[1, 2, 3, 4, 5].map((n) => html`<button type="button" data-n="${n}" aria-label="${n}">★</button>`)}</div>
        <div class="field"><label for="comment">${t('ui.review.comment')}</label><textarea id="comment" maxlength="1000"></textarea></div>
        <div class="row"><button class="btn primary" id="send" disabled>${t('ui.review.submit')}</button><button class="btn" id="no">${t('ui.common.cancel')}</button></div>`);
    $('#stars', dialog).onclick = (e) => {
        const n = Number(e.target.dataset.n);
        if (!n) return;
        rating = n;
        $$('#stars button', dialog).forEach((s) => s.classList.toggle('on', Number(s.dataset.n) <= n));
        $('#send', dialog).disabled = false;
    };
    $('#no', dialog).onclick = closeModal;
    $('#send', dialog).onclick = async () => {
        const ok = await attempt(() => post(`/api/bookings/${b.id}/review`, { rating, comment: $('#comment', dialog).value || null }));
        closeModal();
        if (ok) {
            toast(t('ui.review.thanks'));
            done();
        }
    };
}

/* ---------------------------------------------------------------- add / edit equipment */

export async function equipmentForm(main, id) {
    const e = id ? await get(`/api/equipment/${id}`) : null;
    if (e && e.owner.id !== session.user?.id) {
        location.hash = `#/equipment/${id}`;
        return;
    }
    let point = e ? { lat: e.latitude, lng: e.longitude } : null;

    mount(main, html`
        <div class="card" style="max-width:720px;margin:0 auto">
            <h1>${t(e ? 'ui.form.editTitle' : 'ui.form.newTitle')}</h1>
            <form id="eq-form" novalidate>
                <div class="field"><label for="name">${t('ui.form.name')}</label>
                    <input id="name" name="name" maxlength="120" value="${e?.name ?? ''}" placeholder="${t('ui.form.namePlaceholder')}" required></div>
                <div class="grid two">
                    <div class="field"><label for="category">${t('ui.form.category')}</label>
                        <select id="category" name="category" required>${categoryOptions(e?.category, e ? null : 'ui.form.choose')}</select></div>
                    <div class="field"><label for="pricePerDay">${t('ui.form.price')}</label>
                        <input id="pricePerDay" name="pricePerDay" type="number" inputmode="decimal" min="1" step="1" value="${e?.pricePerDay ?? ''}" required></div>
                </div>
                <div class="field"><label for="description">${t('ui.form.description')}</label>
                    <textarea id="description" name="description" maxlength="2000">${e?.description ?? ''}</textarea></div>
                <div class="field"><label for="address">${t('ui.form.address')}</label>
                    <input id="address" name="address" maxlength="255" value="${e?.address ?? ''}"></div>
                <div class="field">
                    <label>${t('ui.form.location')} <span class="hint">${t('ui.form.locationHelp')}</span></label>
                    <div id="pick-map" class="map tall"></div>
                    <button type="button" id="pick-me" class="btn block" style="margin-top:8px">📍 ${t('ui.form.useMyLocation')}</button>
                    <input type="hidden" name="latitude"><input type="hidden" name="longitude">
                </div>
                <div class="field"><label for="photo">${t('ui.form.photo')}</label>
                    <input id="photo" name="photo" type="file" accept="image/jpeg,image/png,image/webp"></div>
                <label class="check"><input type="checkbox" name="available" ${!e || e.available ? 'checked' : ''}> ${t('ui.form.available')}</label>
                <div class="row" style="margin-top:14px">
                    <button class="btn primary" type="submit">${t('ui.form.save')}</button>
                    ${e ? html`<span class="spacer"></span><button class="btn danger" type="button" id="remove">${t('ui.form.delete')}</button>` : ''}
                </div>
            </form>
        </div>`);

    const map = makeMap($('#pick-map'), point || HOME, point ? 14 : 10);
    let marker = point ? L.marker([point.lat, point.lng]).addTo(map) : null;
    const setPoint = (p, zoom) => {
        point = p;
        if (marker) marker.setLatLng([p.lat, p.lng]); else marker = L.marker([p.lat, p.lng]).addTo(map);
        if (zoom) map.setView([p.lat, p.lng], zoom);
    };
    map.on('click', (ev) => setPoint({ lat: ev.latlng.lat, lng: ev.latlng.lng }));
    $('#pick-me').addEventListener('click', async () => {
        try { setPoint(await locate(), 15); } catch { toast(t('ui.browse.locationDenied'), true); }
    });

    const form = $('#eq-form');
    form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        if (!point) { toast(t('ui.form.locationRequired'), true); return; }
        const data = formData(form);
        delete data.photo;
        Object.assign(data, { latitude: point.lat, longitude: point.lng, available: form.available.checked });

        const saved = await attempt(() => (e ? put(`/api/equipment/${e.id}`, data) : post('/api/equipment', data)), form);
        if (!saved) return;
        const file = form.photo.files[0];
        if (file) {
            const body = new FormData();
            body.append('file', file);
            await attempt(() => post(`/api/equipment/${saved.id}/image`, body));
        }
        toast(t('ui.form.saved'));
        location.hash = `#/equipment/${saved.id}`;
    });
    $('#remove')?.addEventListener('click', async () => {
        if (!confirm(t('ui.form.confirmDelete'))) return;
        const ok = await attempt(() => del(`/api/equipment/${e.id}`).then(() => true));
        if (ok) location.hash = '#/dashboard/equipment';
    });
}

/* ---------------------------------------------------------------- profile & account */

export async function profile(main) {
    const me = await get('/api/users/me');
    mount(main, html`
        <div class="stack" style="max-width:640px;margin:0 auto">
            <div class="card">
                <h1>${t('ui.profile.title')}</h1>
                <p class="muted">📞 ${me.phone} · ${t('ui.profile.rating')}: ${stars(me.averageRating, me.reviewCount)}</p>
                <form id="profile-form" novalidate>
                    <div class="field"><label for="name">${t('ui.auth.name')}</label><input id="name" name="name" value="${me.name}" required></div>
                    <div class="field"><label for="email">${t('ui.auth.email')}</label><input id="email" name="email" type="email" value="${me.email ?? ''}"></div>
                    <div class="field"><label for="address">${t('ui.form.address')}</label><input id="address" name="address" value="${me.address ?? ''}"></div>
                    <div class="grid two">
                        <div class="field"><label for="role">${t('ui.profile.role')}</label>
                            <select id="role" name="role">${['BOTH', 'RENTER', 'OWNER'].map((r) => html`<option value="${r}" ${r === me.role ? 'selected' : ''}>${t('ui.role.' + r)}</option>`)}</select></div>
                        <div class="field"><label for="preferredLanguage">${t('ui.lang.label')}</label>
                            <select id="preferredLanguage" name="preferredLanguage">${languages.map((l) => html`<option value="${l.code}" ${l.code === me.preferredLanguage ? 'selected' : ''}>${l.nativeName}</option>`)}</select></div>
                    </div>
                    <div class="field">
                        <button type="button" id="prof-locate" class="btn block">📍 ${t('ui.auth.shareLocation')}</button>
                        <div id="prof-located" class="hint" ${me.latitude != null ? '' : 'hidden'}>✓ ${t('ui.auth.locationSet')}</div>
                    </div>
                    <button class="btn primary" type="submit">${t('ui.form.save')}</button>
                </form>
            </div>

            <div class="card">
                <h2>${t('ui.profile.password')}</h2>
                <form id="password-form" novalidate>
                    <div class="field"><label for="currentPassword">${t('ui.profile.currentPassword')}</label>
                        <input id="currentPassword" name="currentPassword" type="password" autocomplete="current-password"></div>
                    <div class="field"><label for="newPassword">${t('ui.profile.newPassword')} <span class="hint">${t('ui.auth.passwordHint')}</span></label>
                        <input id="newPassword" name="newPassword" type="password" autocomplete="new-password"></div>
                    <button class="btn" type="submit">${t('ui.profile.password')}</button>
                </form>
            </div>

            <div class="card"><button class="btn block" id="logout">${t('ui.auth.logout')}</button></div>

            <div class="card" style="border-color:var(--red)">
                <h2>${t('ui.profile.delete')}</h2>
                ${deleteForm()}
            </div>
        </div>`);

    let coords = me.latitude != null ? { lat: me.latitude, lng: me.longitude } : null;
    $('#prof-locate').addEventListener('click', async () => {
        try { coords = await locate(); $('#prof-located').hidden = false; } catch { toast(t('ui.browse.locationDenied'), true); }
    });
    $('#profile-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const data = formData(e.target);
        if (coords) Object.assign(data, { latitude: coords.lat, longitude: coords.lng });
        const saved = await attempt(() => put('/api/users/me', data), e.target);
        if (!saved) return;
        session.updateUser({ name: saved.name, preferredLanguage: saved.preferredLanguage });
        if (saved.preferredLanguage !== currentLang()) {
            await setLang(saved.preferredLanguage);
            renderNav();
            profile(main);
        }
        toast(t('ui.form.saved'));
    });
    $('#password-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const ok = await attempt(() => put('/api/users/me/password', formData(e.target)).then(() => true), e.target);
        if (ok) { e.target.reset(); toast(t('ui.profile.passwordChanged')); }
    });
    $('#logout').addEventListener('click', async () => {
        session.clear();
        renderNav();
        location.hash = '#/';
    });
    wireDeleteForm();
}

function deleteForm() {
    return html`
        <p class="small">${t('ui.profile.deleteHelp')}</p>
        <form id="delete-form" novalidate>
            <div class="field"><label for="del-password">${t('ui.profile.deleteConfirm')}</label>
                <input id="del-password" name="password" type="password" autocomplete="current-password"></div>
            <button class="btn danger solid" type="submit">${t('ui.profile.delete')}</button>
        </form>`;
}

function wireDeleteForm() {
    $('#delete-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const ok = await attempt(() => del('/api/users/me', formData(e.target)).then(() => true), e.target);
        if (!ok) return;
        session.clear();
        renderNav();
        mount($('#main'), html`<div class="card empty"><h2>${t('ui.profile.deleted')}</h2></div>`);
    });
}

/** Public page for Google Play's "account deletion URL" requirement. */
export async function deleteAccount(main) {
    mount(main, html`
        <div class="card prose" style="margin:0 auto">
            <h1>${t('ui.deletePage.title')}</h1>
            <p>${t('ui.deletePage.intro')}</p>
            <p>${t('ui.deletePage.what')}</p>
            ${session.loggedIn ? deleteForm() : html`<a class="btn primary" href="#/login" id="del-login">${t('ui.deletePage.login')}</a>`}
        </div>`);
    if (session.loggedIn) wireDeleteForm();
    else $('#del-login').addEventListener('click', () => sessionStorage.setItem('agrishare.after-login', '#/delete-account'));
}
