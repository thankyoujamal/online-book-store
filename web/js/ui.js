import { api } from "./api.js";

/* ---------- Safe HTML templating ---------- */

class Safe {
  constructor(value) { this.value = value; }
  toString() { return this.value; }
}

const ESCAPES = { "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;", "'": "&#39;" };
export const escape = (s) => String(s).replace(/[&<>"']/g, (c) => ESCAPES[c]);

function render(value) {
  if (value == null || value === false) return "";
  if (Array.isArray(value)) return value.map(render).join("");
  if (value instanceof Safe) return value.value;
  return escape(value);
}

/** Tagged template that escapes every interpolation unless it is itself html`` output. */
export function html(strings, ...values) {
  let out = strings[0];
  for (let i = 0; i < values.length; i++) out += render(values[i]) + strings[i + 1];
  return new Safe(out);
}

export const $ = (selector, root = document) => root.querySelector(selector);
export const $$ = (selector, root = document) => [...root.querySelectorAll(selector)];

/* ---------- Formatting ---------- */

const inr = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", maximumFractionDigits: 0 });
const inrPaise = new Intl.NumberFormat("en-IN", { style: "currency", currency: "INR", minimumFractionDigits: 2 });
const count = new Intl.NumberFormat("en-IN");
const dateFmt = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" });

export const money = (n) => {
  const v = Number(n);
  return Number.isInteger(v) ? inr.format(v) : inrPaise.format(v);
};
export const number = (n) => count.format(n);
export const date = (iso) => dateFmt.format(new Date(iso));
export const orderNo = (id) => "OBS-" + String(id).padStart(5, "0");

/* ---------- Icons (one 1.6px stroke set) ---------- */

const icon = (path, extra = "") =>
  html`<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.6" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true" ${new Safe(extra)}>${new Safe(path)}</svg>`;

export const icons = {
  bag: icon('<path d="M5 8h14l-1.2 11.1a2 2 0 0 1-2 1.9H8.2a2 2 0 0 1-2-1.9L5 8Z"/><path d="M9 8V6.5a3 3 0 0 1 6 0V8"/>'),
  search: icon('<circle cx="11" cy="11" r="6.5"/><path d="m20 20-4.2-4.2"/>'),
  close: icon('<path d="M6 6l12 12M18 6 6 18"/>'),
  book: icon('<path d="M4 5.5A1.5 1.5 0 0 1 5.5 4H11v16H5.5A1.5 1.5 0 0 1 4 18.5v-13Z"/><path d="M20 5.5A1.5 1.5 0 0 0 18.5 4H13v16h5.5a1.5 1.5 0 0 0 1.5-1.5v-13Z"/>'),
};

const STAR = '<path d="M12 3.6l2.6 5.3 5.8.8-4.2 4.1 1 5.8L12 16.9l-5.2 2.7 1-5.8-4.2-4.1 5.8-.8L12 3.6Z"/>';

export function stars(rating) {
  const out = [];
  for (let i = 1; i <= 5; i++) {
    const fill = rating >= i - 0.25 ? "currentColor" : "none";
    out.push(new Safe(`<svg viewBox="0 0 24 24" fill="${fill}" stroke="currentColor" stroke-width="1.6" stroke-linejoin="round">${STAR}</svg>`));
  }
  return html`<span class="stars" role="img" aria-label="Rated ${rating} out of 5">${out}</span>`;
}

/* ---------- Generated book cover ---------- */

/** Picks dark or light text from the cover colour's relative luminance. */
function inkFor(hex) {
  const [r, g, b] = [1, 3, 5].map((i) => {
    const c = parseInt(hex.slice(i, i + 2), 16) / 255;
    return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4;
  });
  return 0.2126 * r + 0.7152 * g + 0.0722 * b > 0.4 ? "#1d2330" : "#ffffff";
}

export const isBestseller = (book) => book.ratingCount >= 3000 && Number(book.rating) >= 4.7;

export function cover(book, { thumb = false, ribbon = false } = {}) {
  const color = /^#[0-9a-f]{6}$/i.test(book.coverColor ?? "") ? book.coverColor : "#2d5d8a";
  const style = `--cover:${color};--ink-on:${inkFor(color)}`;
  if (thumb) return html`<div class="cover cover--thumb" style="${style}" aria-hidden="true"></div>`;

  const titleClass = book.title.length > 28 ? "cover__title cover__title--long" : "cover__title";
  const variant = ["plain", "band", "frame"][book.id % 3];
  const badge = ribbon && isBestseller(book) ? html`<span class="badge-ribbon">Bestseller</span>` : "";
  const edition = book.edition ? html`<span class="cover__edition">${book.edition}</span>` : "";
  const sub = book.subtitle && book.subtitle.length < 60 ? html`<span class="cover__sub">${book.subtitle}</span>` : "";
  const author = html`<span class="cover__author">${book.author.split(",")[0]}</span>`;

  if (variant === "band") {
    return html`<div class="cover cover--band" style="${style}" aria-hidden="true">${badge}
      <div class="cover__inner">
        <div class="cover__top">${edition}</div>
        <div class="cover__band"><span class="${titleClass}">${book.title}</span>${sub}${author}</div>
      </div>
    </div>`;
  }
  return html`<div class="cover cover--${variant}" style="${style}" aria-hidden="true">${badge}
    <div class="cover__inner">
      ${edition}<span class="${titleClass}">${book.title}</span>${sub}<span class="cover__rule"></span>${author}
    </div>
  </div>`;
}

export function stockLabel(stock) {
  if (stock === 0) return html`<span class="stock stock--out">Out of stock</span>`;
  if (stock <= 5) return html`<span class="stock stock--low">Only ${stock} left</span>`;
  return html`<span class="stock stock--ok">In stock</span>`;
}

export function priceTag(book) {
  const list = book.listPrice ? html`<s>${money(book.listPrice)}</s>` : "";
  return html`<span class="price">${money(book.price)}${list}</span>`;
}

export const STATUS_TEXT = { PLACED: "Placed", SHIPPED: "Shipped", DELIVERED: "Delivered", CANCELLED: "Cancelled" };

export function statusBadge(status) {
  return html`<span class="status status--${status.toLowerCase()}">${STATUS_TEXT[status] ?? status}</span>`;
}

export function paymentBadge(order) {
  if (order.paymentStatus === "PAID") {
    return html`<span class="status status--delivered">${order.paymentMethod === "ONLINE" ? "Paid online" : "Paid on delivery"}</span>`;
  }
  if (order.paymentMethod === "COD") return html`<span class="status status--cancelled">Cash on delivery</span>`;
  if (order.status === "CANCELLED") return html`<span class="status status--cancelled">Not paid</span>`;
  return html`<span class="status status--shipped">Payment pending</span>`;
}

/* ---------- Session and header ---------- */

let mePromise;
export function currentUser() {
  mePromise ??= api("/auth/me").then((r) => r.user).catch(() => null);
  return mePromise;
}

/** Redirects to sign-in (or home for non-admins) when the page needs it. Returns the user. */
export async function requireUser({ admin = false } = {}) {
  const user = await currentUser();
  if (!user) {
    location.replace("/login.html?next=" + encodeURIComponent(location.pathname + location.search));
    return new Promise(() => {});
  }
  if (admin && user.role !== "ADMIN") {
    location.replace("/");
    return new Promise(() => {});
  }
  return user;
}

export function setCartCount(n) {
  const el = $("#cart-count");
  if (!el) return;
  el.textContent = n;
  el.toggleAttribute("data-empty", n === 0);
  $("#cart-link")?.setAttribute("aria-label", `Cart, ${n} ${n === 1 ? "item" : "items"}`);
}

export async function mountHeader(active) {
  const header = $("#site-header");
  const user = await currentUser();
  const link = (href, label, key) =>
    html`<a href="${href}" ${active === key ? new Safe('aria-current="page"') : ""}>${label}</a>`;

  header.className = "site-header";
  header.innerHTML = html`
    <div class="container site-header__inner">
      <a class="brand" href="/">
        <span class="brand__mark">${icons.book}</span>
        <span class="brand__name">Online Book Store</span>
      </a>
      <nav class="nav" aria-label="Main">
        ${link("/", "Browse", "browse")}
        ${user ? link("/orders.html", "My orders", "orders") : ""}
        ${user?.role === "ADMIN" ? link("/admin.html", "Admin", "admin") : ""}
      </nav>
      <div class="header-actions">
        ${user
          ? html`<span class="user-chip">Hi, <strong>${user.fullName.split(" ")[0]}</strong></span>
                 <button class="link-button" id="logout" type="button">Sign out</button>`
          : html`<a class="btn btn--ghost btn--sm" href="/login.html">Sign in</a>`}
        <a class="cart-link" id="cart-link" href="/cart.html" ${active === "cart" ? new Safe('aria-current="page"') : ""}>
          ${icons.bag}<span>Cart</span><span class="cart-count" id="cart-count" data-empty>0</span>
        </a>
      </div>
    </div>`;

  $("#logout")?.addEventListener("click", async () => {
    await api("/auth/logout", { method: "POST" }).catch(() => {});
    location.href = "/";
  });

  if (user) {
    api("/cart").then((cart) => setCartCount(cart.itemCount)).catch(() => {});
  }
  return user;
}

/* ---------- Toasts ---------- */

export function toast(message, { error = false, action } = {}) {
  let host = $(".toasts");
  if (!host) {
    host = document.createElement("div");
    host.className = "toasts";
    host.setAttribute("role", "status");
    host.setAttribute("aria-live", "polite");
    document.body.append(host);
  }
  const el = document.createElement("div");
  el.className = "toast" + (error ? " toast--error" : "");
  el.innerHTML = html`<span>${message}</span>${action ? html`<a href="${action.href}">${action.label}</a>` : ""}`;
  host.append(el);
  setTimeout(() => el.remove(), error ? 6000 : 3500);
}

/* ---------- Forms ---------- */

/** Shows server-side field errors next to inputs and the general message in the form alert. */
export function showErrors(form, err) {
  clearErrors(form);
  const fields = err.fields ?? {};
  let first = null;
  for (const [name, message] of Object.entries(fields)) {
    const input = form.elements[name];
    const slot = $(`[data-error-for="${name}"]`, form);
    if (input) {
      input.setAttribute("aria-invalid", "true");
      first ??= input;
    }
    if (slot) slot.textContent = message;
  }
  const alert = $(".form-alert", form);
  // Field messages are enough when they map onto inputs; otherwise show the general message.
  if (alert) alert.textContent = first ? "" : err.message;
  (first ?? alert)?.focus?.();
}

export function clearErrors(form) {
  $$("[aria-invalid]", form).forEach((el) => el.removeAttribute("aria-invalid"));
  $$("[data-error-for]", form).forEach((el) => (el.textContent = ""));
  const alert = $(".form-alert", form);
  if (alert) alert.textContent = "";
}

export function setLoading(button, loading) {
  button.disabled = loading;
  button.toggleAttribute("data-loading", loading);
}

export const formData = (form) => Object.fromEntries(new FormData(form).entries());
