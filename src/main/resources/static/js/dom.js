// Tiny templating: html`...${value}...` escapes every interpolated value unless it
// is itself the result of html`` (or raw()). Farmers' names, descriptions and
// reviews are user input, so nothing reaches innerHTML unescaped.

class Safe {
    constructor(s) { this.s = s; }
    toString() { return this.s; }
}

const ESC = { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' };
export const esc = (s) => String(s).replace(/[&<>"']/g, (c) => ESC[c]);

function render(v) {
    if (v == null || v === false) return '';
    if (v instanceof Safe) return v.s;
    if (Array.isArray(v)) return v.map(render).join('');
    return esc(v);
}

export function html(strings, ...values) {
    let out = '';
    strings.forEach((s, i) => { out += s + (i < values.length ? render(values[i]) : ''); });
    return new Safe(out);
}

export const raw = (s) => new Safe(s);

export function mount(el, content) {
    el.innerHTML = render(content);
}

export const $ = (sel, root = document) => root.querySelector(sel);
export const $$ = (sel, root = document) => [...root.querySelectorAll(sel)];

let toastTimer;
export function toast(message, isError = false) {
    const el = $('#toast');
    el.textContent = message;
    el.className = 'toast' + (isError ? ' error' : '');
    el.hidden = false;
    clearTimeout(toastTimer);
    toastTimer = setTimeout(() => { el.hidden = true; }, isError ? 6000 : 3500);
}

/** Opens the shared <dialog>; returns it so callers can wire up buttons. */
export function openModal(content) {
    const dialog = $('#modal');
    mount(dialog, content);
    if (!dialog.open) dialog.showModal();
    return dialog;
}

export function closeModal() {
    const dialog = $('#modal');
    if (dialog.open) dialog.close();
}

/** Reads a form into a plain object; empty strings become null. */
export function formData(form) {
    const data = {};
    new FormData(form).forEach((value, key) => {
        if (typeof value === 'string') data[key] = value.trim() === '' ? null : value.trim();
    });
    return data;
}

/** Shows server-side field errors under the matching inputs. */
export function showFieldErrors(form, fields = {}) {
    $$('.error', form).forEach((e) => e.remove());
    Object.entries(fields).forEach(([name, message]) => {
        const input = form.elements[name];
        const field = input && (input.closest ? input.closest('.field') : null);
        if (field) field.insertAdjacentHTML('beforeend', `<div class="error">${esc(message)}</div>`);
    });
}
