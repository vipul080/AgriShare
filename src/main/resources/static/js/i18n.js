// Loads /api/i18n/{lang} (the same bundles the server uses for error messages)
// and swaps in the Noto font for that script.

const FONTS = {
    hi: 'Noto+Sans+Devanagari', mr: 'Noto+Sans+Devanagari', pa: 'Noto+Sans+Gurmukhi',
    gu: 'Noto+Sans+Gujarati', bn: 'Noto+Sans+Bengali', ta: 'Noto+Sans+Tamil',
    te: 'Noto+Sans+Telugu', kn: 'Noto+Sans+Kannada',
};
const SUPPORTED = ['en', 'hi', 'pa', 'mr', 'gu', 'bn', 'ta', 'te', 'kn'];
const STORAGE_KEY = 'agrishare.lang';

let lang = 'en';
let messages = {};
let languages = [];

function storedLang() {
    try { return localStorage.getItem(STORAGE_KEY); } catch { return null; }
}

/** First visit: pick up the phone's language if we support it. */
export function initialLang() {
    const saved = storedLang();
    if (saved && SUPPORTED.includes(saved)) return saved;
    const browser = (navigator.language || 'en').slice(0, 2).toLowerCase();
    return SUPPORTED.includes(browser) ? browser : 'en';
}

export async function setLang(code) {
    if (!SUPPORTED.includes(code)) code = 'en';
    const res = await fetch(`/api/i18n/${code}`);
    messages = await res.json();
    lang = code;
    try { localStorage.setItem(STORAGE_KEY, code); } catch { /* private mode */ }
    document.documentElement.lang = code;

    const family = FONTS[code];
    document.getElementById('script-font').href = family
        ? `https://fonts.googleapis.com/css2?family=${family}:wght@400;600;700&display=swap` : '';
    document.documentElement.style.setProperty('--script-font', family ? `"${family.replace(/\+/g, ' ')}"` : 'sans-serif');
}

export async function loadLanguages() {
    if (!languages.length) languages = await (await fetch('/api/i18n')).json();
    return languages;
}

export const currentLang = () => lang;

/** t('ui.book.total', {days: 3, total: '4,500'}) — fills {named} placeholders. */
export function t(key, params) {
    let text = messages[key] ?? key;
    if (params) {
        for (const [name, value] of Object.entries(params)) {
            text = text.split(`{${name}}`).join(value ?? '');
        }
    }
    return text;
}

const locale = () => (lang === 'en' ? 'en-IN' : `${lang}-IN`);

/** Indian grouping: 1,00,000. */
export function money(amount) {
    return new Intl.NumberFormat(locale(), { maximumFractionDigits: 2 }).format(Number(amount));
}

export function date(iso) {
    if (!iso) return '';
    const d = iso.length === 10 ? new Date(iso + 'T00:00:00') : new Date(iso);
    return new Intl.DateTimeFormat(locale(), { day: 'numeric', month: 'short' }).format(d);
}

export function dateTime(iso) {
    return new Intl.DateTimeFormat(locale(), { day: 'numeric', month: 'short', hour: 'numeric', minute: '2-digit' })
        .format(new Date(iso));
}
