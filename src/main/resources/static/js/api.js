import { currentLang } from './i18n.js';

const TOKEN_KEY = 'agrishare.token';
const USER_KEY = 'agrishare.user';

export class ApiError extends Error {
    constructor(status, body) {
        super(body?.message || `HTTP ${status}`);
        this.status = status;
        this.code = body?.code;
        this.fields = body?.fields || {};
    }
}

function read(key) {
    try { return localStorage.getItem(key); } catch { return null; }
}

function write(key, value) {
    try { value == null ? localStorage.removeItem(key) : localStorage.setItem(key, value); } catch { /* private mode */ }
}

export const session = {
    get token() { return read(TOKEN_KEY); },
    get user() {
        try { return JSON.parse(read(USER_KEY) || 'null'); } catch { return null; }
    },
    get loggedIn() { return !!read(TOKEN_KEY); },
    save(auth) {
        write(TOKEN_KEY, auth.token);
        write(USER_KEY, JSON.stringify({ id: auth.userId, name: auth.name, preferredLanguage: auth.preferredLanguage }));
    },
    updateUser(patch) {
        write(USER_KEY, JSON.stringify({ ...(this.user || {}), ...patch }));
    },
    clear() {
        write(TOKEN_KEY, null);
        write(USER_KEY, null);
    },
};

/** JSON API call. Errors come back already translated (Accept-Language). */
export async function api(method, path, body) {
    const headers = { 'Accept-Language': currentLang() };
    const options = { method, headers };
    if (session.token) headers.Authorization = `Bearer ${session.token}`;
    if (body instanceof FormData) {
        options.body = body;
    } else if (body !== undefined) {
        headers['Content-Type'] = 'application/json';
        options.body = JSON.stringify(body);
    }

    let res;
    try {
        res = await fetch(path, options);
    } catch {
        throw new ApiError(0, { message: navigator.onLine ? null : 'offline', code: 'offline' });
    }

    if (res.status === 401 && session.token) {
        session.clear(); // expired or deleted account
        window.dispatchEvent(new Event('agrishare:logout'));
    }
    const text = await res.text();
    const data = text ? JSON.parse(text) : null;
    if (!res.ok) throw new ApiError(res.status, data);
    return data;
}

export const get = (path) => api('GET', path);
export const post = (path, body) => api('POST', path, body);
export const put = (path, body) => api('PUT', path, body);
export const del = (path, body) => api('DELETE', path, body);
