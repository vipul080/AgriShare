import { html, mount, $, $$, toast, openModal, closeModal, formData, showFieldErrors } from './dom.js';
import { t, money, date, dateTime, setLang, currentLang, canSpeak, speak, stopSpeaking, daysText } from './i18n.js';
import { session, get, post, put, del, ApiError } from './api.js';
import { renderNav, refreshUnread, chooseLanguage } from './app.js';

// Designed for farmers who may read little: every action has an icon, statuses have
// colours, key screens can be read aloud, and choices are big tiles instead of dropdowns.

const HOME = { lat: 30.4384, lng: 77.6245 }; // Paonta Sahib, the default map centre
const ICONS = {
    TRACTOR: '🚜', ROTAVATOR: '⚙️', CULTIVATOR: '🔱', PLOUGH: '🐂', SEED_DRILL: '🌱', HARVESTER: '🌾',
    THRESHER: '🌀', SPRAYER: '💦', WATER_PUMP: '🚰', POWER_TILLER: '🛞', TROLLEY: '🛻', OTHER: '🔧',
};
const STATUS = {
    AWAITING_PAYMENT: ['💳', 'amber'], REQUESTED: ['⏳', 'amber'], CONFIRMED: ['✅', 'green'],
    REJECTED: ['❌', 'red'], CANCELLED: ['🚫', 'grey'], COMPLETED: ['🏁', 'blue'], EXPIRED: ['⌛', 'grey'],
};
const NOTIFICATION_ICONS = {
    BOOKING_REQUESTED: '📥', BOOKING_CONFIRMED: '✅', BOOKING_REJECTED: '❌',
    BOOKING_CANCELLED: '🚫', BOOKING_COMPLETED: '⭐', BOOKING_EXPIRED: '⌛',
};
const MAX_DAYS = 30;

let languages = [];
let categories = [];
let payConfig = { onlineFeePercent: 0, onlineFeeCap: 0, boost: { enabled: false } };

export async function preload(langs) {
    languages = langs;
    [categories, payConfig] = await Promise.all([get('/api/equipment/categories'), get('/api/payments/config')]);
}

/** Same rule as the server: online payments only, whole rupees, capped. Cash is always free. */
function onlineFee(rent) {
    const pct = Number(payConfig.onlineFeePercent);
    if (!(pct > 0)) return 0;
    const fee = Math.round((rent * pct) / 100);
    const cap = Number(payConfig.onlineFeeCap);
    return cap > 0 ? Math.min(fee, cap) : fee;
}

const featuredPill = (e) => (e.featured ? html`<span class="pill amber">⭐ ${t('ui.equipment.featured')}</span>` : '');
export const cachedLanguages = () => languages;

/* ---------------------------------------------------------------- helpers */

function isoDate(d) {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
}
const today = () => isoDate(new Date());
function addDays(iso, n) {
    const d = new Date(iso + 'T00:00:00');
    d.setDate(d.getDate() + n);
    return isoDate(d);
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

/** 🔊 button; the text to read is kept on the button itself. */
function speakButton(text) {
    if (!canSpeak()) return '';
    return html`<button type="button" class="speak" data-say="${text}">🔊 ${t('ui.speak')}</button>`;
}

document.addEventListener('click', (e) => {
    const button = e.target.closest('.speak');
    if (!button) return;
    if (button.classList.contains('speaking')) {
        stopSpeaking();
        button.classList.remove('speaking');
        return;
    }
    $$('.speak.speaking').forEach((b) => b.classList.remove('speaking'));
    button.classList.add('speaking');
    speak(button.dataset.say, () => button.classList.remove('speaking'));
});

/** On-screen yes/no with big buttons (browser confirm() boxes are tiny and look foreign). */
function confirmDialog(message, okKey, danger = false) {
    return new Promise((resolve) => {
        const dialog = openModal(html`
            <h2>${message}</h2>
            <div class="row" style="margin-top:16px">
                <button class="btn ${danger ? 'danger solid' : 'primary'}" id="yes">${t(okKey || 'ui.common.yes')}</button>
                <button class="btn" id="no">${t('ui.common.no')}</button>
            </div>`);
        $('#yes', dialog).onclick = () => { closeModal(); resolve(true); };
        $('#no', dialog).onclick = () => { closeModal(); resolve(false); };
    });
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

function escapeText(s) {
    const div = document.createElement('div');
    div.textContent = s;
    return div.innerHTML;
}

function passwordField(id, name, autocomplete, labelHtml) {
    return html`
        <div class="field"><label for="${id}">${labelHtml}</label>
            <div class="pw-wrap">
                <input id="${id}" name="${name}" type="password" autocomplete="${autocomplete}">
                <button type="button" class="pw-toggle" data-for="${id}">👁 ${t('ui.auth.show')}</button>
            </div>
        </div>`;
}

document.addEventListener('click', (e) => {
    const toggle = e.target.closest('.pw-toggle');
    if (!toggle) return;
    const input = document.getElementById(toggle.dataset.for);
    const show = input.type === 'password';
    input.type = show ? 'text' : 'password';
    toggle.textContent = `${show ? '🙈' : '👁'} ${t(show ? 'ui.auth.hide' : 'ui.auth.show')}`;
});

function goAfterLogin() {
    const next = sessionStorage.getItem('agrishare.after-login');
    sessionStorage.removeItem('agrishare.after-login');
    location.hash = next || '#/';
}

/* ---------------------------------------------------------------- home */

export async function home(main) {
    const stats = await get('/api/stats/public').catch(() => null);
    const steps = [['🔍', 1], ['📅', 2], ['🤝', 3]];
    const howText = steps.map(([, n]) => `${t(`ui.home.step${n}.title`)}. ${t(`ui.home.step${n}.text`)}`).join(' ');
    const name = session.user?.name;

    mount(main, html`
        <div class="title-row">
            <h1>${name ? t('ui.home.hello', { name }) : t('ui.home.title')}</h1>
            ${speakButton(`${t('ui.home.need')}. ${t('ui.home.needSub')}. ${t('ui.home.have')}. ${t('ui.home.haveSub')}.`)}
        </div>
        <div class="grid two" style="margin-bottom:18px">
            <a class="big-choice need" href="#/browse">
                <span class="emoji">🚜</span>
                <div><b>${t('ui.home.need')}</b><span>${t('ui.home.needSub')}</span></div>
            </a>
            <a class="big-choice have" href="#/equipment/new">
                <span class="emoji">💰</span>
                <div><b>${t('ui.home.have')}</b><span>${t('ui.home.haveSub')}</span></div>
            </a>
        </div>
        ${stats && stats.machines > 0 ? html`
        <section class="stats" style="margin-bottom:18px">
            <div class="card stat"><b>👨‍🌾 ${stats.farmers}</b>${t('ui.home.stats.farmers')}</div>
            <div class="card stat"><b>🚜 ${stats.machines}</b>${t('ui.home.stats.machines')}</div>
            <div class="card stat"><b>🤝 ${stats.completedRentals}</b>${t('ui.home.stats.rentals')}</div>
        </section>` : ''}
        <div class="title-row"><h2>${t('ui.home.how')}</h2>${speakButton(howText)}</div>
        <section class="grid three">
            ${steps.map(([emoji, n]) => html`
                <div class="card">
                    <div class="row"><span style="font-size:2.4rem">${emoji}</span><span class="step-num">${n}</span></div>
                    <h3>${t(`ui.home.step${n}.title`)}</h3>
                    <p class="muted">${t(`ui.home.step${n}.text`)}</p>
                </div>`)}
        </section>`);
}

/* ---------------------------------------------------------------- browse */

const search = { lat: null, lng: null, radiusKm: 25, category: '', q: '', showMap: false };

export async function browse(main) {
    mount(main, html`
        <div class="title-row"><h1>${t('ui.nav.find')}</h1><span id="browse-speak"></span></div>
        <div class="cat-strip" id="cats">
            <button type="button" class="cat-tile ${search.category ? '' : 'active'}" data-cat=""><span class="emoji">🧰</span>${t('ui.browse.all')}</button>
            ${categories.map((c) => html`
                <button type="button" class="cat-tile ${c === search.category ? 'active' : ''}" data-cat="${c}">
                    <span class="emoji">${ICONS[c]}</span>${t('ui.category.' + c)}</button>`)}
        </div>
        <div class="card" style="margin-bottom:12px">
            <div id="where" class="row" style="margin-bottom:10px"></div>
            <div class="chips scroll" id="radius">
                ${[5, 10, 25, 50].map((km) => html`<button type="button" class="chip ${km === search.radiusKm ? 'active' : ''}" data-km="${km}">📍 ${t('ui.browse.km', { n: km })}</button>`)}
            </div>
            <form id="search-form" class="row" style="margin-top:10px">
                <input name="q" type="search" value="${search.q}" placeholder="🔍 ${t('ui.browse.searchPlaceholder')}" style="flex:1;min-width:0">
                <button class="btn" type="submit">${t('ui.browse.searchButton')}</button>
            </form>
        </div>
        <div class="row" style="margin-bottom:10px">
            <p id="result-line" class="muted" style="margin:0;flex:1"></p>
            <button type="button" class="btn small" id="map-toggle"></button>
        </div>
        <div id="map" class="map" style="margin-bottom:12px" ${search.showMap ? '' : 'hidden'}></div>
        <div id="results" class="grid cards"></div>`);

    const map = makeMap($('#map'), search.lat ? search : HOME, search.lat ? 11 : 10);
    const markers = L.layerGroup().addTo(map);
    let circle = null;
    let lastItems = [];

    const renderWhere = () => mount($('#where'), search.lat != null
        ? html`<span class="dist">📍 ${t('ui.browse.nearYou')}</span>`
        : html`<button type="button" id="near-me" class="btn primary block">📍 ${t('ui.browse.useLocation')}</button>`);
    const renderToggle = () => { $('#map-toggle').textContent = search.showMap ? `📋 ${t('ui.browse.showList')}` : `🗺️ ${t('ui.browse.showMap')}`; };

    function drawMap(items) {
        markers.clearLayers();
        items.forEach((e) => {
            L.marker([e.latitude, e.longitude]).addTo(markers)
                .bindPopup(`<b>${escapeText(e.name)}</b><br>${escapeText(t('ui.equipment.perDay', { price: money(e.pricePerDay) }))}<br><a href="#/equipment/${e.id}">${escapeText(t('ui.browse.view'))}</a>`);
        });
        if (circle) circle.remove();
        if (search.lat != null) {
            circle = L.circle([search.lat, search.lng], { radius: search.radiusKm * 1000, color: '#2f6b3a', weight: 2, fillOpacity: 0.05 }).addTo(map);
            map.fitBounds(circle.getBounds());
        } else if (items.length) {
            map.fitBounds(L.latLngBounds(items.map((e) => [e.latitude, e.longitude])).pad(0.2), { maxZoom: 12 });
        }
    }

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
        lastItems = items;

        const summary = search.lat != null
            ? t('ui.browse.results', { n: items.length, km: search.radiusKm })
            : t('ui.browse.newest');
        $('#result-line').textContent = summary;
        mount($('#browse-speak'), speakButton(items.length
            ? `${summary}. ${items.slice(0, 5).map((e) => `${e.name}, ${t('ui.equipment.perDay', { price: money(e.pricePerDay) })}${e.distanceKm != null ? ', ' + t('ui.equipment.distance', { km: e.distanceKm }) : ''}`).join('. ')}`
            : t('ui.browse.empty')));
        mount($('#results'), items.length
            ? items.map(equipmentCard)
            : html`<div class="empty card"><div style="font-size:3rem">🔍</div>${t('ui.browse.empty')}</div>`);
        if (search.showMap) drawMap(items);
    }

    async function useLocation(silent) {
        $('#result-line').textContent = t('ui.browse.finding');
        try {
            Object.assign(search, await locate());
        } catch {
            if (!silent) toast(t('ui.browse.locationDenied'), true);
        }
        renderWhere();
        load();
    }

    $('#cats').addEventListener('click', (e) => {
        const tile = e.target.closest('.cat-tile');
        if (!tile) return;
        search.category = tile.dataset.cat;
        $$('.cat-tile').forEach((x) => x.classList.toggle('active', x === tile));
        load();
    });
    $('#radius').addEventListener('click', (e) => {
        const chip = e.target.closest('.chip');
        if (!chip) return;
        search.radiusKm = Number(chip.dataset.km);
        $$('#radius .chip').forEach((x) => x.classList.toggle('active', x === chip));
        if (search.lat == null) useLocation(false); else load();
    });
    $('#where').addEventListener('click', (e) => { if (e.target.closest('#near-me')) useLocation(false); });
    $('#search-form').addEventListener('submit', (e) => {
        e.preventDefault();
        search.q = formData(e.target).q || '';
        load();
    });
    $('#map-toggle').addEventListener('click', () => {
        search.showMap = !search.showMap;
        $('#map').hidden = !search.showMap;
        renderToggle();
        if (search.showMap) {
            map.invalidateSize();
            drawMap(lastItems);
        }
    });

    renderWhere();
    renderToggle();
    // First visit: ask for location straight away so the nearest machines come first.
    if (search.lat == null && !sessionStorage.getItem('agrishare.asked-location')) {
        sessionStorage.setItem('agrishare.asked-location', '1');
        useLocation(true);
    } else {
        load();
    }
}

function equipmentCard(e) {
    return html`
        <a class="card eq-card" href="#/equipment/${e.id}">
            ${thumb(e.imageUrl, e.category)}
            <div class="body">
                <h3>${e.name}</h3>
                <div class="muted small">${ICONS[e.category]} ${t('ui.category.' + e.category)}${e.address ? html` · ${e.address}` : ''}</div>
                <div class="row" style="margin-top:6px">
                    <span class="price">${t('ui.equipment.perDay', { price: money(e.pricePerDay) })}</span>
                    <span class="spacer"></span>
                    <span class="small">${stars(e.averageRating, e.reviewCount)}</span>
                </div>
                <div class="row" style="margin-top:4px">
                    ${featuredPill(e)}
                    ${e.distanceKm != null ? html`<span class="dist">📍 ${t('ui.equipment.distance', { km: e.distanceKm })}</span>` : ''}
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
    const say = [e.name, t('ui.category.' + e.category), t('ui.equipment.perDay', { price: money(e.pricePerDay) }),
        `${t('ui.equipment.owner')}: ${e.owner.name}`, e.address || '', e.description || ''].filter(Boolean).join('. ');

    mount(main, html`
        <p><a href="#/browse" class="btn small">← ${t('ui.common.back')}</a></p>
        <div class="grid two">
            <div class="stack">
                ${thumb(e.imageUrl, e.category, 'detail-photo card')}
                <div class="card">
                    <div class="title-row"><h1>${e.name}</h1>${speakButton(say)}</div>
                    <div class="row">
                        <span class="pill">${ICONS[e.category]} ${t('ui.category.' + e.category)}</span>
                        ${featuredPill(e)}
                        <span>${stars(e.averageRating, e.reviewCount)}</span>
                    </div>
                    <p class="price" style="margin-top:10px;font-size:1.6rem">${t('ui.equipment.perDay', { price: money(e.pricePerDay) })}</p>
                    <p>👨‍🌾 <b>${e.owner.name}</b>${e.address ? html`<br>📍 ${e.address}` : ''}</p>
                    ${e.description ? html`<p style="white-space:pre-line">${e.description}</p>` : ''}
                </div>
                <div class="card" id="book-card"></div>
            </div>
            <div class="stack">
                <div class="card"><div id="detail-map" class="map"></div></div>
                <div class="card">
                    <h2>⭐ ${t('ui.reviews.title')}</h2>
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
        <p class="small muted" style="margin:10px 0 4px">📅 ${t('ui.book.taken')}</p>
        <ul class="booked-list">${booked.map((b) => html`<li class="pill amber">${date(b.startDate)} – ${date(b.endDate)}</li>`)}</ul>` : '';

    if (mine) {
        mount(card, html`<p>${t('ui.book.own')}</p>${takenList}<a class="btn block" href="#/equipment/${e.id}/edit">✏️ ${t('ui.book.edit')}</a>
            ${boostBox(e)}`);
        $('#boost-btn')?.addEventListener('click', () => startBoost(e));
        return;
    }
    if (!e.available) {
        mount(card, html`<div class="status-banner red"><span class="emoji">🚫</span><b>${t('ui.equipment.unavailable')}</b></div>`);
        return;
    }
    if (!session.loggedIn) {
        sessionStorage.setItem('agrishare.after-login', location.hash);
        mount(card, html`<h2>📅 ${t('ui.book.title')}</h2>${takenList}<a class="btn primary block" href="#/login">👤 ${t('ui.book.loginFirst')}</a>`);
        return;
    }
    bookingForm(card, e, booked, takenList);
}

function bookingForm(card, e, booked, takenList) {
    const state = { start: today(), days: 1, pay: 'CASH' };

    mount(card, html`
        <h2>📅 ${t('ui.book.title')}</h2>
        <form id="book-form" novalidate>
            <label>${t('ui.book.when')}</label>
            <div class="chips" id="when">
                <button type="button" class="chip active" data-when="0">${t('ui.book.today')}</button>
                <button type="button" class="chip" data-when="1">${t('ui.book.tomorrow')}</button>
                <button type="button" class="chip" data-when="other">📅 ${t('ui.book.otherDay')}</button>
            </div>
            <input type="date" id="other-date" min="${today()}" max="${addDays(today(), 180)}" hidden style="margin-top:8px">
            ${takenList}

            <label style="margin-top:16px">${t('ui.book.howLong')}</label>
            <div class="stepper">
                <button type="button" id="minus" aria-label="-">−</button>
                <output id="days"></output>
                <button type="button" id="plus" aria-label="+">+</button>
            </div>
            <p class="muted" id="until" style="margin:6px 0 0"></p>
            <div id="clash" class="warn" hidden>⚠️ ${t('ui.book.clash')}</div>

            <label style="margin-top:16px">${t('ui.book.payment')}</label>
            <div class="pay-cards">
                <label class="pay-card"><input type="radio" name="pay" value="CASH" checked>
                    <span class="emoji">💵</span><b>${t('ui.book.cash')}</b><small>${t('ui.book.cashSub')}</small></label>
                <label class="pay-card" ${payConfig.mode === 'off' ? 'hidden' : ''}><input type="radio" name="pay" value="ONLINE">
                    <span class="emoji">📱</span><b>${t('ui.book.online')}</b><small>${t('ui.book.onlineSub')}</small></label>
            </div>

            <p class="muted small" id="fee-line" hidden style="margin:10px 0 0"></p>
            <div class="total-box"><span>${t('ui.book.totalLabel')}</span><b id="total"></b></div>

            <details style="margin-bottom:12px"><summary class="muted">💬 ${t('ui.book.note')}</summary>
                <textarea id="note" maxlength="500" style="margin-top:8px"></textarea></details>
            <button class="btn primary block" type="submit" id="book-submit" style="min-height:60px;font-size:1.2rem">✅ ${t('ui.book.submit')}</button>
        </form>`);

    const end = () => addDays(state.start, state.days - 1);
    const clashes = () => booked.some((b) => b.startDate <= end() && b.endDate >= state.start);

    function update() {
        const rent = state.days * Number(e.pricePerDay);
        const fee = $('#book-form').pay.value === 'ONLINE' ? onlineFee(rent) : 0;
        $('#days').textContent = daysText(state.days);
        $('#until').textContent = t('ui.book.until', { date: date(end()) });
        $('#fee-line').hidden = fee === 0;
        $('#fee-line').textContent = t('ui.book.fee', { rent: money(rent), fee: money(fee) });
        $('#total').textContent = `₹${money(rent + fee)}`;
        const clash = clashes();
        $('#clash').hidden = !clash;
        $('#book-submit').disabled = clash;
    }

    $('#when').addEventListener('click', (ev) => {
        const chip = ev.target.closest('.chip');
        if (!chip) return;
        $$('#when .chip').forEach((c) => c.classList.toggle('active', c === chip));
        const other = chip.dataset.when === 'other';
        $('#other-date').hidden = !other;
        if (other) {
            $('#other-date').value = state.start;
            $('#other-date').showPicker?.();
        } else {
            state.start = addDays(today(), Number(chip.dataset.when));
        }
        update();
    });
    $('#other-date').addEventListener('change', (ev) => {
        if (ev.target.value) state.start = ev.target.value;
        update();
    });
    $('#book-form').addEventListener('change', (ev) => { if (ev.target.name === 'pay') update(); });
    $('#minus').addEventListener('click', () => { state.days = Math.max(1, state.days - 1); update(); });
    $('#plus').addEventListener('click', () => { state.days = Math.min(MAX_DAYS, state.days + 1); update(); });
    update();

    $('#book-form').addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const form = ev.target;
        const body = {
            equipmentId: e.id,
            startDate: state.start,
            endDate: end(),
            paymentMethod: form.pay.value,
            note: $('#note').value.trim() || null,
        };
        const booking = await attempt(() => post('/api/bookings', body), form);
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

function checkout(booking) {
    return pay(booking.checkout, `${booking.equipment.name} · ${date(booking.startDate)} – ${date(booking.endDate)}`,
        `/api/bookings/${booking.id}/payment/verify`, t('ui.pay.held'), t('ui.pay.success'));
}

/**
 * Shared checkout for bookings and boosts. Resolves with the verify response, or
 * undefined if the farmer closed it. Mock mode shows a stand-in dialog (no real money).
 */
async function pay(c, description, verifyPath, note, successText) {
    const verify = async (fields) => {
        const done = await attempt(() => post(verifyPath, fields));
        if (done) toast(successText);
        return done;
    };
    if (c.mode === 'razorpay') return razorpayCheckout(c, description, verify);

    return new Promise((resolve) => {
        const dialog = openModal(html`
            <h2>📱 ${t('ui.pay.title', { amount: money(c.amountPaise / 100) })}</h2>
            <p>${description}</p>
            ${note ? html`<p class="muted small">🔒 ${note}</p>` : ''}
            <p class="pill amber">${t('ui.pay.mockNote')}</p>
            <div class="row" style="margin-top:14px">
                <button class="btn primary" id="pay-now">✅ ${t('ui.pay.payNow')}</button>
                <button class="btn" id="pay-later">${t('ui.common.close')}</button>
            </div>`);
        $('#pay-later', dialog).onclick = () => { closeModal(); resolve(undefined); };
        $('#pay-now', dialog).onclick = async () => {
            const done = await verify({
                orderId: c.orderId,
                paymentId: 'pay_mock_' + Math.random().toString(36).slice(2, 12),
                signature: 'mock',
            });
            closeModal();
            resolve(done);
        };
    });
}

/** Optional paid listing boost, offered only to the owner and only when switched on. */
function boostBox(e) {
    const b = payConfig.boost;
    if (e.featured) {
        return html`<div class="status-banner amber" style="margin-top:12px"><span class="emoji">⭐</span>
            <div><b>${t('ui.boost.active', { date: date(e.featuredUntil) })}</b></div></div>`;
    }
    if (!b || !b.enabled) return '';
    return html`
        <div class="card" style="margin-top:12px;background:var(--amber-soft);border-color:var(--amber)">
            <h3>⭐ ${t('ui.boost.title')}</h3>
            <p class="small">${t('ui.boost.text', { days: b.days })}</p>
            <button type="button" class="btn amber block" id="boost-btn">⭐ ${t('ui.boost.button', { price: money(b.price) })}</button>
        </div>`;
}

async function startBoost(e) {
    const started = await attempt(() => post(`/api/equipment/${e.id}/boost`));
    if (!started) return;
    const done = await pay(started.checkout, `⭐ ${e.name} · ${daysText(started.days)}`,
        `/api/equipment/boosts/${started.boostId}/verify`, null, t('ui.boost.done'));
    if (done) equipmentDetail($('#main'), e.id);
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

async function razorpayCheckout(c, description, verify) {
    await loadRazorpay();
    return new Promise((resolve) => {
        const rzp = new window.Razorpay({
            key: c.keyId,
            amount: c.amountPaise,
            currency: c.currency,
            order_id: c.orderId,
            name: document.title,
            description,
            handler: async (resp) => resolve(await verify(resp)),
            modal: { ondismiss: () => resolve(undefined) },
            theme: { color: '#2f6b3a' },
        });
        rzp.open();
    });
}

/* ---------------------------------------------------------------- auth */

export async function login(main) {
    mount(main, html`
        <div class="card" style="max-width:460px;margin:0 auto">
            <h1>👤 ${t('ui.auth.login')}</h1>
            <form id="login-form" novalidate>
                <div class="field"><label for="identifier">📱 ${t('ui.auth.identifier')}</label>
                    <input id="identifier" name="identifier" inputmode="tel" autocomplete="username" required></div>
                ${passwordField('password', 'password', 'current-password', html`🔒 ${t('ui.auth.password')}`)}
                <button class="btn primary block" type="submit" style="min-height:56px">${t('ui.auth.login')}</button>
            </form>
            <a class="btn block" href="#/register" style="margin-top:14px">✨ ${t('ui.auth.noAccount')}</a>
        </div>`);
    $('#login-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const auth = await attempt(() => post('/api/auth/login', formData(e.target)), e.target);
        if (auth) await signedIn(auth);
    });
}

async function signedIn(auth) {
    session.save(auth);
    if (auth.preferredLanguage && auth.preferredLanguage !== currentLang()) await setLang(auth.preferredLanguage);
    renderNav();
    goAfterLogin();
}

function roleChoices(selected) {
    const roles = [['RENTER', '🚜'], ['OWNER', '💰'], ['BOTH', '🔁']];
    return html`
        <div class="field"><label>${t('ui.profile.role')}</label>
            <div class="grid three">
                ${roles.map(([role, emoji]) => html`
                    <label class="pay-card"><input type="radio" name="role" value="${role}" ${role === selected ? 'checked' : ''}>
                        <span class="emoji">${emoji}</span><b>${t('ui.role.' + role)}</b></label>`)}
            </div>
        </div>`;
}

export async function register(main) {
    mount(main, html`
        <div class="card" style="max-width:520px;margin:0 auto">
            <h1>✨ ${t('ui.auth.register')}</h1>
            <form id="register-form" novalidate>
                <div class="field"><label for="name">👤 ${t('ui.auth.name')}</label>
                    <input id="name" name="name" autocomplete="name" required></div>
                <div class="field"><label for="phone">📱 ${t('ui.auth.phone')}</label>
                    <input id="phone" name="phone" type="tel" inputmode="numeric" maxlength="10" autocomplete="tel-national" placeholder="98765 43210" required>
                    <div class="hint">${t('ui.auth.whyPhone')}</div></div>
                ${passwordField('password', 'password', 'new-password', html`🔒 ${t('ui.auth.password')} <span class="hint">${t('ui.auth.passwordHint')}</span>`)}
                ${roleChoices('BOTH')}
                <div class="field">
                    <button type="button" id="reg-locate" class="btn block">📍 ${t('ui.auth.shareLocation')}</button>
                    <div id="reg-located" class="hint" hidden>✅ ${t('ui.auth.locationSet')}</div>
                </div>
                <details class="field"><summary class="muted">✉️ ${t('ui.auth.email')}</summary>
                    <input id="email" name="email" type="email" autocomplete="email" style="margin-top:8px"></details>
                <p class="small muted">${t('ui.auth.agree')}
                    <a href="/terms.html" target="_blank" rel="noopener">${t('ui.footer.terms')}</a> ·
                    <a href="/privacy.html" target="_blank" rel="noopener">${t('ui.footer.privacy')}</a></p>
                <button class="btn primary block" type="submit" style="min-height:56px">✅ ${t('ui.auth.register')}</button>
            </form>
            <a class="btn block" href="#/login" style="margin-top:14px">${t('ui.auth.haveAccount')}</a>
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
        if (data.phone) data.phone = data.phone.replace(/\D/g, '');
        if (coords) Object.assign(data, { latitude: coords.lat, longitude: coords.lng });
        const auth = await attempt(() => post('/api/auth/register', data), e.target);
        if (auth) await signedIn(auth);
    });
}

/* ---------------------------------------------------------------- dashboard */

export async function dashboard(main, tab = 'bookings') {
    const tabs = [['bookings', '📋'], ['requests', '📥'], ['equipment', '🚜'], ['notifications', '🔔']];
    mount(main, html`
        <nav class="tabs">
            ${tabs.map(([name, emoji]) => html`<a href="#/dashboard/${name}" class="${name === tab ? 'active' : ''}">${emoji} ${t('ui.dash.' + name)}</a>`)}
        </nav>
        <div id="tab" class="stack"><div class="loading">${t('ui.common.loading')}</div></div>`);
    const el = $('#tab');

    if (tab === 'bookings' || tab === 'requests') {
        const asOwner = tab === 'requests';
        const list = await get(asOwner ? '/api/bookings/incoming' : '/api/bookings/mine');
        mount(el, list.length
            ? list.map((b) => bookingCard(b, asOwner))
            : html`<div class="card empty"><div style="font-size:3rem">${asOwner ? '📥' : '📋'}</div>
                ${t(asOwner ? 'ui.dash.noRequests' : 'ui.dash.noBookings')}
                ${asOwner ? '' : html`<p style="margin-top:12px"><a class="btn primary" href="#/browse">🔍 ${t('ui.home.find')}</a></p>`}</div>`);
        el.onclick = (ev) => {
            const button = ev.target.closest('button[data-action]');
            if (button) bookingAction(button.dataset.action, list.find((b) => b.id === Number(button.dataset.id)), asOwner);
        };
    } else if (tab === 'equipment') {
        const list = await get('/api/equipment/mine');
        mount(el, html`
            <a class="btn primary block" href="#/equipment/new" style="min-height:56px">➕ ${t('ui.dash.addEquipment')}</a>
            ${list.length ? html`<div class="grid cards">${list.map(equipmentCard)}</div>`
                : html`<div class="card empty"><div style="font-size:3rem">🚜</div>${t('ui.dash.noEquipment')}</div>`}`);
    } else {
        const list = await get('/api/notifications');
        mount(el, html`
            ${list.some((n) => !n.read) ? html`<p><button class="btn small" id="read-all">✔️ ${t('ui.dash.markAllRead')}</button></p>` : ''}
            ${list.length ? list.map((n) => {
                const body = notificationBody(n);
                return html`
                <div class="card notif ${n.read ? '' : 'unread'}">
                    <span style="font-size:2rem">${NOTIFICATION_ICONS[n.type] || '🔔'}</span>
                    <a href="#" data-id="${n.id}" data-type="${n.type}" data-owner="${n.params.owner || ''}" class="notif-link" style="flex:1;text-decoration:none;color:inherit">
                        <b>${t(`notification.${n.type}.title`)}</b>
                        <div>${body}</div>
                        <div class="muted small">${dateTime(n.createdAt)}</div>
                    </a>
                    ${speakButton(`${t(`notification.${n.type}.title`)}. ${body}`)}
                </div>`;
            }) : html`<div class="card empty"><div style="font-size:3rem">🔔</div>${t('ui.dash.noNotifications')}</div>`}`);
        $('#read-all')?.addEventListener('click', async () => {
            await attempt(() => post('/api/notifications/read-all'));
            refreshUnread();
            dashboard(main, 'notifications');
        });
        el.addEventListener('click', (ev) => {
            const item = ev.target.closest('a.notif-link');
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

function statusHelp(status, asOwner) {
    if (asOwner && (status === 'REQUESTED' || status === 'CONFIRMED')) return t(`ui.statusHelp.owner.${status}`);
    return t(`ui.statusHelp.${status}`);
}

function bookingCard(b, asOwner) {
    const other = asOwner ? b.renter : b.owner;
    const otherPhone = asOwner ? b.renterPhone : b.ownerPhone;
    const started = today() >= b.startDate;
    const [emoji, colour] = STATUS[b.status];
    const help = statusHelp(b.status, asOwner);
    const range = t('ui.booking.range', { start: date(b.startDate), end: date(b.endDate) });
    const actions = [];

    if (!asOwner) {
        if (b.status === 'AWAITING_PAYMENT') actions.push(['pay', 'primary', '📱', 'ui.booking.pay']);
        if (['AWAITING_PAYMENT', 'REQUESTED'].includes(b.status) || (b.status === 'CONFIRMED' && !started)) {
            actions.push(['cancel', 'danger', '🚫', 'ui.booking.cancel']);
        }
    } else {
        if (b.status === 'REQUESTED') actions.push(['approve', 'primary', '✅', 'ui.booking.approve'], ['reject', 'danger', '❌', 'ui.booking.reject']);
        if (b.status === 'CONFIRMED' && started) actions.push(['complete', 'primary', '🏁', 'ui.booking.complete']);
        if (b.status === 'CONFIRMED' && !started) actions.push(['cancel', 'danger', '🚫', 'ui.booking.cancel']);
    }
    if (b.status === 'COMPLETED' && !b.reviewedByMe) actions.push(['review', 'amber', '⭐', 'ui.booking.rate']);

    const say = [t('ui.status.' + b.status), help, b.equipment.name, range, daysText(b.days),
        t('ui.booking.total', { total: money(b.totalAmount) }), `${t(asOwner ? 'ui.booking.renter' : 'ui.booking.owner')}: ${other.name}`].join('. ');

    return html`
        <div class="card booking-card">
            <div class="status-banner ${colour}">
                <span class="emoji">${emoji}</span>
                <div style="flex:1"><b>${t('ui.status.' + b.status)}</b><span>${help}</span></div>
                ${speakButton(say)}
            </div>
            <div class="top">
                ${thumb(b.equipment.imageUrl, b.equipment.category, 'mini')}
                <div style="flex:1;min-width:0">
                    <b><a href="#/equipment/${b.equipment.id}">${b.equipment.name}</a></b>
                    <div>📅 ${range} · ${daysText(b.days)}</div>
                    <div class="row">
                        <span class="price">₹${money(b.amountPayable)}</span>
                        ${Number(b.platformFee) > 0 ? html`<span class="muted small">${t('ui.booking.fee', { fee: money(b.platformFee) })}</span>` : ''}
                        <span class="pill grey">${b.paymentMethod === 'CASH' ? '💵' : '📱'} ${t('ui.payment.' + b.paymentStatus)}</span>
                    </div>
                    <div class="muted small">${asOwner ? '👨‍🌾' : '🚜'} ${t(asOwner ? 'ui.booking.renter' : 'ui.booking.owner')}: ${other.name}</div>
                </div>
            </div>
            ${b.note ? html`<p class="small" style="margin:8px 0 0">💬 ${b.note}</p>` : ''}
            ${b.rejectReason ? html`<p class="small" style="margin:8px 0 0">${t('ui.booking.reason', { reason: b.rejectReason })}</p>` : ''}
            ${otherPhone ? html`
                <a class="btn block call-btn" href="tel:+91${otherPhone}" style="margin-top:12px">📞 ${t('ui.booking.call')} ${other.name} · ${otherPhone}</a>
                ${b.paymentMethod === 'CASH' && !asOwner && b.status === 'CONFIRMED' ? html`<p class="small muted" style="margin:6px 0 0">💵 ${t('ui.booking.cashNote')}</p>` : ''}` : ''}
            ${b.status === 'COMPLETED' && b.reviewedByMe ? html`<p class="small muted" style="margin:8px 0 0">${t('ui.booking.reviewed')}</p>` : ''}
            ${actions.length ? html`<div class="actions">
                ${actions.map(([action, style, icon, key]) => html`<button class="btn ${style}" data-action="${action}" data-id="${b.id}">${icon} ${t(key)}</button>`)}
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
            <h2>❌ ${t('ui.booking.reject')}</h2>
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
    if (action === 'cancel' && !(await confirmDialog(t('ui.booking.confirmCancel'), 'ui.booking.cancel', true))) return;

    const done = await attempt(() => post(`${base}/${action}`));
    if (done) {
        toast(`${STATUS[done.status][0]} ${t('ui.status.' + done.status)}`);
        reload();
    }
}

function reviewDialog(b, asOwner, done) {
    const who = asOwner ? b.renter.name : b.owner.name;
    let rating = 0;
    const dialog = openModal(html`
        <h2>⭐ ${t('ui.review.title', { name: who })}</h2>
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
            <h1>${e ? '✏️' : '➕'} ${t(e ? 'ui.form.editTitle' : 'ui.form.newTitle')}</h1>
            <form id="eq-form" novalidate>
                <div class="field"><label>1. ${t('ui.form.category')}</label>
                    <div class="cat-strip" style="flex-wrap:wrap">
                        ${categories.map((c) => html`
                            <label class="cat-tile ${c === e?.category ? 'active' : ''}">
                                <input type="radio" name="category" value="${c}" ${c === e?.category ? 'checked' : ''} hidden>
                                <span class="emoji">${ICONS[c]}</span>${t('ui.category.' + c)}</label>`)}
                    </div>
                </div>
                <div class="field"><label for="name">2. ${t('ui.form.name')}</label>
                    <input id="name" name="name" maxlength="120" value="${e?.name ?? ''}" placeholder="${t('ui.form.namePlaceholder')}" required></div>
                <div class="field"><label for="pricePerDay">3. ${t('ui.form.price')}</label>
                    <input id="pricePerDay" name="pricePerDay" type="number" inputmode="numeric" min="1" step="1" value="${e?.pricePerDay ?? ''}" placeholder="₹ 1500" style="font-size:1.4rem;font-weight:700" required></div>
                <div class="field">
                    <label>4. 📍 ${t('ui.form.location')} <span class="hint">${t('ui.form.locationHelp')}</span></label>
                    <button type="button" id="pick-me" class="btn primary block" style="margin-bottom:8px">📍 ${t('ui.form.useMyLocation')}</button>
                    <div id="pick-map" class="map tall"></div>
                </div>
                <div class="field"><label for="photo">5. 📷 ${t('ui.form.photo')}</label>
                    <input id="photo" name="photo" type="file" accept="image/jpeg,image/png,image/webp" capture="environment"></div>
                <details class="field" ${e?.description || e?.address ? 'open' : ''}><summary class="muted">📝 ${t('ui.form.description')}</summary>
                    <div class="field" style="margin-top:8px"><label for="address">${t('ui.form.address')}</label>
                        <input id="address" name="address" maxlength="255" value="${e?.address ?? ''}"></div>
                    <textarea id="description" name="description" maxlength="2000">${e?.description ?? ''}</textarea>
                </details>
                <label class="check"><input type="checkbox" name="available" ${!e || e.available ? 'checked' : ''}> ✅ ${t('ui.form.available')}</label>
                <button class="btn primary block" type="submit" style="margin-top:14px;min-height:60px;font-size:1.2rem">💾 ${t('ui.form.save')}</button>
                ${e ? html`<button class="btn danger block" type="button" id="remove" style="margin-top:10px">🗑️ ${t('ui.form.delete')}</button>` : ''}
            </form>
        </div>`);

    const form = $('#eq-form');
    form.addEventListener('change', (ev) => {
        if (ev.target.name === 'category') {
            $$('.cat-tile', form).forEach((tile) => tile.classList.toggle('active', tile.contains(ev.target)));
        }
    });

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

    form.addEventListener('submit', async (ev) => {
        ev.preventDefault();
        const data = formData(form);
        if (!data.category) { toast(t('validation.equipment.category.required'), true); return; }
        if (!point) { toast(t('ui.form.locationRequired'), true); return; }
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
        toast(`✅ ${t('ui.form.saved')}`);
        location.hash = `#/equipment/${saved.id}`;
    });
    $('#remove')?.addEventListener('click', async () => {
        if (!(await confirmDialog(t('ui.form.confirmDelete'), 'ui.form.delete', true))) return;
        const ok = await attempt(() => del(`/api/equipment/${e.id}`).then(() => true));
        if (ok) location.hash = '#/dashboard/equipment';
    });
}

/* ---------------------------------------------------------------- profile & account */

export async function profile(main) {
    const me = await get('/api/users/me');
    const lang = languages.find((l) => l.code === me.preferredLanguage);
    mount(main, html`
        <div class="stack" style="max-width:640px;margin:0 auto">
            <div class="card">
                <h1>👤 ${me.name}</h1>
                <p class="muted">📱 ${me.phone} · ⭐ ${stars(me.averageRating, me.reviewCount)}</p>
                <button type="button" class="btn block" id="change-lang">🌐 ${lang ? lang.nativeName : ''}</button>
            </div>
            <div class="card">
                <h2>✏️ ${t('ui.profile.title')}</h2>
                <form id="profile-form" novalidate>
                    <div class="field"><label for="name">👤 ${t('ui.auth.name')}</label><input id="name" name="name" value="${me.name}" required></div>
                    <div class="field"><label for="address">🏡 ${t('ui.form.address')}</label><input id="address" name="address" value="${me.address ?? ''}"></div>
                    ${roleChoices(me.role)}
                    <div class="field">
                        <button type="button" id="prof-locate" class="btn block">📍 ${t('ui.auth.shareLocation')}</button>
                        <div id="prof-located" class="hint" ${me.latitude != null ? '' : 'hidden'}>✅ ${t('ui.auth.locationSet')}</div>
                    </div>
                    <details class="field" ${me.email ? 'open' : ''}><summary class="muted">✉️ ${t('ui.auth.email')}</summary>
                        <input id="email" name="email" type="email" value="${me.email ?? ''}" style="margin-top:8px"></details>
                    <button class="btn primary block" type="submit">💾 ${t('ui.form.save')}</button>
                </form>
            </div>

            <div class="card">
                <h2>🔒 ${t('ui.profile.password')}</h2>
                <form id="password-form" novalidate>
                    ${passwordField('currentPassword', 'currentPassword', 'current-password', t('ui.profile.currentPassword'))}
                    ${passwordField('newPassword', 'newPassword', 'new-password', html`${t('ui.profile.newPassword')} <span class="hint">${t('ui.auth.passwordHint')}</span>`)}
                    <button class="btn block" type="submit">${t('ui.profile.password')}</button>
                </form>
            </div>

            <div class="card"><button class="btn block" id="logout">🚪 ${t('ui.auth.logout')}</button></div>

            <details class="card" style="border-color:var(--red)">
                <summary style="color:var(--red);font-weight:700">🗑️ ${t('ui.profile.delete')}</summary>
                <div style="margin-top:10px">${deleteForm()}</div>
            </details>
        </div>`);

    $('#change-lang').addEventListener('click', () => chooseLanguage());
    let coords = me.latitude != null ? { lat: me.latitude, lng: me.longitude } : null;
    $('#prof-locate').addEventListener('click', async () => {
        try { coords = await locate(); $('#prof-located').hidden = false; } catch { toast(t('ui.browse.locationDenied'), true); }
    });
    $('#profile-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const data = { ...formData(e.target), preferredLanguage: currentLang() };
        if (coords) Object.assign(data, { latitude: coords.lat, longitude: coords.lng });
        const saved = await attempt(() => put('/api/users/me', data), e.target);
        if (!saved) return;
        session.updateUser({ name: saved.name });
        toast(`✅ ${t('ui.form.saved')}`);
    });
    $('#password-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        const ok = await attempt(() => put('/api/users/me/password', formData(e.target)).then(() => true), e.target);
        if (ok) { e.target.reset(); toast(`✅ ${t('ui.profile.passwordChanged')}`); }
    });
    $('#logout').addEventListener('click', () => {
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
            ${passwordField('del-password', 'password', 'current-password', t('ui.profile.deleteConfirm'))}
            <button class="btn danger solid block" type="submit">🗑️ ${t('ui.profile.delete')}</button>
        </form>`;
}

function wireDeleteForm() {
    $('#delete-form').addEventListener('submit', async (e) => {
        e.preventDefault();
        if (!(await confirmDialog(t('ui.profile.delete') + '?', 'ui.profile.delete', true))) return;
        const ok = await attempt(() => del('/api/users/me', formData(e.target)).then(() => true), e.target);
        if (!ok) return;
        session.clear();
        renderNav();
        mount($('#main'), html`<div class="card empty"><div style="font-size:3rem">👋</div><h2>${t('ui.profile.deleted')}</h2></div>`);
    });
}

/** Public page for Google Play's "account deletion URL" requirement. */
export async function deleteAccount(main) {
    mount(main, html`
        <div class="card prose" style="margin:0 auto">
            <h1>🗑️ ${t('ui.deletePage.title')}</h1>
            <p>${t('ui.deletePage.intro')}</p>
            <p>${t('ui.deletePage.what')}</p>
            ${session.loggedIn ? deleteForm() : html`<a class="btn primary" href="#/login" id="del-login">👤 ${t('ui.deletePage.login')}</a>`}
        </div>`);
    if (session.loggedIn) wireDeleteForm();
    else $('#del-login').addEventListener('click', () => sessionStorage.setItem('agrishare.after-login', '#/delete-account'));
}
