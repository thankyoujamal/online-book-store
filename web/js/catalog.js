import { api } from "./api.js";
import {
  $, $$, html, icons, money, number, cover, stars, stockLabel, priceTag,
  mountHeader, setCartCount, toast, setLoading,
} from "./ui.js";

const PAGE_SIZE = 12;
const MAX_PER_TITLE = 10;
const PRICE_LABELS = { "-500": "Under ₹500", "500-1000": "₹500 – ₹1,000", "1000-": "Over ₹1,000" };

const grid = $("#grid");
const dialog = $("#book-dialog");

let user = null;
let categories = [];
let booksById = new Map();
let inCart = new Map(); // bookId -> quantity
let requestSeq = 0;

/* ---------- URL is the source of truth for filters ---------- */

function readState() {
  const p = new URLSearchParams(location.search);
  return {
    q: p.get("q") ?? "",
    category: p.getAll("category"),
    price: p.get("price") ?? "",
    stock: p.get("stock") === "1",
    sort: p.get("sort") ?? "popular",
    page: Math.max(1, Number(p.get("page")) || 1),
  };
}

function writeState(state, { push = true } = {}) {
  const p = new URLSearchParams();
  if (state.q) p.set("q", state.q);
  state.category.forEach((c) => p.append("category", c));
  if (state.price) p.set("price", state.price);
  if (state.stock) p.set("stock", "1");
  if (state.sort !== "popular") p.set("sort", state.sort);
  if (state.page > 1) p.set("page", state.page);
  const url = location.pathname + (p.toString() ? "?" + p : "");
  history[push ? "pushState" : "replaceState"](null, "", url);
}

function syncControls(state) {
  $("#q").value = state.q;
  $("#sort").value = state.sort;
  $$('input[name="category"]').forEach((el) => (el.checked = state.category.includes(el.value)));
  $$('input[name="price"]').forEach((el) => (el.checked = el.value === state.price));
  $('input[name="stock"]').checked = state.stock;
}

function update(patch) {
  const state = { ...readState(), page: 1, ...patch };
  writeState(state);
  loadBooks();
}

/* ---------- Rendering ---------- */

function renderCategories() {
  $("#category-options").innerHTML = html`${categories.map((c) => html`
    <label class="check">
      <input type="checkbox" name="category" value="${c.id}"> ${c.name}
      <span class="count num">${c.bookCount}</span>
    </label>`)}`;
  const total = categories.reduce((sum, c) => sum + c.bookCount, 0);
  $("#intro-count").textContent =
    `${number(total)} titles across ${categories.map((c) => c.name.toLowerCase()).join(", ").replace(/, ([^,]*)$/, " and $1")}.`;
}

function cartButton(book) {
  if (book.stock === 0) {
    return html`<button class="btn btn--secondary btn--sm" type="button" disabled>Sold out</button>`;
  }
  if (inCart.has(book.id)) {
    return html`<a class="btn btn--added btn--sm" href="/cart.html">In cart · ${inCart.get(book.id)}</a>`;
  }
  return html`<button class="btn btn--secondary btn--sm" type="button" data-add="${book.id}">Add to cart</button>`;
}

function bookCard(book) {
  return html`
    <article class="book" data-id="${book.id}">
      <button class="book__open" type="button" data-open="${book.id}" aria-label="View details for ${book.title}">
        ${cover(book, { ribbon: true })}
      </button>
      <div class="book__meta">
        <h3 class="book__title">${book.title}</h3>
        <p class="book__author">${book.author}</p>
        <p class="book__rating">${stars(Number(book.rating))}<span class="num">${book.rating} · ${number(book.ratingCount)}</span></p>
        ${stockLabel(book.stock)}
      </div>
      <div class="book__foot">${priceTag(book)}${cartButton(book)}</div>
    </article>`;
}

function renderSkeleton() {
  grid.setAttribute("aria-busy", "true");
  grid.innerHTML = html`${Array.from({ length: 8 }, () => html`
    <div aria-hidden="true">
      <div class="skeleton skeleton--cover"></div>
      <div class="skeleton skeleton--line" style="width:80%"></div>
      <div class="skeleton skeleton--line" style="width:50%"></div>
    </div>`)}`;
}

function renderActiveFilters(state) {
  const tags = [];
  if (state.q) tags.push({ label: `“${state.q}”`, clear: { q: "" } });
  for (const id of state.category) {
    const c = categories.find((x) => String(x.id) === id);
    if (c) tags.push({ label: c.name, clear: { category: state.category.filter((x) => x !== id) } });
  }
  if (state.price) tags.push({ label: PRICE_LABELS[state.price] ?? state.price, clear: { price: "" } });
  if (state.stock) tags.push({ label: "In stock", clear: { stock: false } });

  const host = $("#active-filters");
  host.innerHTML = html`${tags.map((t, i) => html`
    <button class="tag" type="button" data-tag="${i}" aria-label="Remove filter ${t.label}">${t.label}${icons.close}</button>`)}`;
  $$("[data-tag]", host).forEach((el) =>
    el.addEventListener("click", () => update(tags[Number(el.dataset.tag)].clear)));
}

function heading(state, page) {
  const parts = [];
  const cats = state.category.map((id) => categories.find((c) => String(c.id) === id)?.name).filter(Boolean);
  if (cats.length) parts.push(html` in <strong>${cats.join(", ")}</strong>`);
  if (state.q) parts.push(html` for <strong>“${state.q}”</strong>`);
  if (page.total === 0) return html`No books${parts}`;
  const from = (page.page - 1) * page.pageSize + 1;
  const to = from + page.items.length - 1;
  return html`Showing <strong class="num">${from}–${to}</strong> of <strong class="num">${number(page.total)}</strong> ${page.total === 1 ? "book" : "books"}${parts}`;
}

function renderPager(page) {
  const pager = $("#pager");
  if (page.totalPages <= 1) {
    pager.innerHTML = "";
    return;
  }
  pager.innerHTML = html`
    <button class="btn btn--secondary btn--sm" type="button" data-page="${page.page - 1}" ${page.page <= 1 ? "disabled" : ""}>Previous</button>
    <span class="num">Page ${page.page} of ${page.totalPages}</span>
    <button class="btn btn--secondary btn--sm" type="button" data-page="${page.page + 1}" ${page.page >= page.totalPages ? "disabled" : ""}>Next</button>`;
}

async function loadBooks() {
  const state = readState();
  syncControls(state);
  renderActiveFilters(state);
  renderSkeleton();
  const seq = ++requestSeq;

  try {
    const page = await api("/books", {
      query: {
        q: state.q || null,
        category: state.category,
        price: state.price || null,
        stock: state.stock ? "1" : null,
        sort: state.sort,
        page: state.page,
        size: PAGE_SIZE,
      },
    });
    if (seq !== requestSeq) return; // a newer request has started

    booksById = new Map(page.items.map((b) => [b.id, b]));
    $("#results-heading").innerHTML = heading(state, page);
    grid.removeAttribute("aria-busy");

    if (page.items.length === 0) {
      const filtered = state.q || state.category.length || state.price || state.stock;
      grid.innerHTML = html`
        <div class="state">
          <h2>${filtered ? "Nothing matches these filters" : "The shelves are empty"}</h2>
          <p>${filtered
            ? "Try a shorter search, another category, or widen the price range."
            : "No books have been added yet. An admin can add titles from the Admin page."}</p>
          ${filtered ? html`<button class="btn btn--secondary" type="button" id="clear-all">Clear all filters</button>` : ""}
        </div>`;
      $("#clear-all")?.addEventListener("click", () =>
        update({ q: "", category: [], price: "", stock: false }));
    } else {
      grid.innerHTML = html`${page.items.map(bookCard)}`;
    }
    renderPager(page);
  } catch (err) {
    if (seq !== requestSeq) return;
    grid.removeAttribute("aria-busy");
    $("#results-heading").textContent = "Couldn't load books";
    grid.innerHTML = html`
      <div class="state">
        <h2>The catalogue didn't load</h2>
        <p>${err.message}</p>
        <button class="btn btn--secondary" type="button" id="retry">Try again</button>
      </div>`;
    $("#retry").addEventListener("click", loadBooks);
    $("#pager").innerHTML = "";
  }
}

function refreshCard(bookId) {
  const book = booksById.get(bookId);
  const card = $(`.book[data-id="${bookId}"]`);
  if (book && card) $(".book__foot", card).innerHTML = html`${priceTag(book)}${cartButton(book)}`;
}

/* ---------- Cart ---------- */

async function addToCart(bookId, quantity, button) {
  if (!user) {
    location.href = "/login.html?next=" + encodeURIComponent(location.pathname + location.search);
    return false;
  }
  setLoading(button, true);
  try {
    const cart = await api("/cart/items", { method: "POST", body: { bookId, quantity } });
    inCart = new Map(cart.items.map((i) => [i.book.id, i.quantity]));
    setCartCount(cart.itemCount);
    refreshCard(bookId);
    const book = booksById.get(bookId);
    toast(`Added “${book?.title ?? "book"}” to your cart`, { action: { href: "/cart.html", label: "View cart" } });
    return true;
  } catch (err) {
    toast(err.message, { error: true });
    return false;
  } finally {
    if (button.isConnected) setLoading(button, false);
  }
}

/* ---------- Detail dialog ---------- */

function openDetail(book) {
  const max = Math.min(book.stock, MAX_PER_TITLE - (inCart.get(book.id) ?? 0));
  let qty = 1;

  dialog.innerHTML = html`
    <button class="dialog__close" type="button" data-close aria-label="Close">${icons.close}</button>
    <div class="dialog__body detail">
      <div>${cover(book)}</div>
      <div>
        <p class="eyebrow">${book.categoryName}${book.publishedYear ? html` · ${book.publishedYear}` : ""}</p>
        <h2 class="detail__title" id="detail-title">${book.title}</h2>
        ${book.subtitle ? html`<p class="detail__sub">${book.subtitle}</p>` : ""}
        <p class="detail__author">by ${book.author}</p>
        <p class="book__rating">${stars(Number(book.rating))}<span class="num">${book.rating} from ${number(book.ratingCount)} ratings</span></p>
        ${book.description ? html`<p class="detail__desc">${book.description}</p>` : ""}
        <dl class="detail__facts">
          ${book.edition ? html`<dt>Edition</dt><dd>${book.edition}</dd>` : ""}
          <dt>ISBN</dt><dd>${book.isbn}</dd>
          <dt>Availability</dt><dd>${stockLabel(book.stock)}</dd>
        </dl>
        <div class="detail__buy">
          ${priceTag(book)}
          ${max > 0 ? html`
            <div class="stepper" role="group" aria-label="Quantity">
              <button type="button" data-step="-1" aria-label="Decrease quantity">−</button>
              <output id="qty" aria-live="polite">1</output>
              <button type="button" data-step="1" aria-label="Increase quantity">+</button>
            </div>
            <button class="btn btn--primary" type="button" id="detail-add">Add to cart</button>`
          : html`<button class="btn btn--secondary" type="button" disabled>
              ${book.stock === 0 ? "Out of stock" : "Limit reached in cart"}</button>`}
        </div>
      </div>
    </div>`;

  const sync = () => {
    $("#qty", dialog).textContent = qty;
    $('[data-step="-1"]', dialog).disabled = qty <= 1;
    $('[data-step="1"]', dialog).disabled = qty >= max;
  };
  if (max > 0) {
    sync();
    $$("[data-step]", dialog).forEach((b) => b.addEventListener("click", () => {
      qty = Math.min(max, Math.max(1, qty + Number(b.dataset.step)));
      sync();
    }));
    $("#detail-add", dialog).addEventListener("click", async (e) => {
      if (await addToCart(book.id, qty, e.currentTarget)) dialog.close();
    });
  }
  $("[data-close]", dialog).addEventListener("click", () => dialog.close());
  dialog.showModal();
}

dialog.addEventListener("click", (e) => {
  if (e.target === dialog) dialog.close(); // backdrop click
});

/* ---------- Wiring ---------- */

$("#search-form").addEventListener("submit", (e) => {
  e.preventDefault();
  update({ q: $("#q").value.trim() });
});
$("#sort").addEventListener("change", (e) => update({ sort: e.target.value }));
$(".filters").addEventListener("change", (e) => {
  const t = e.target;
  if (t.name === "category") update({ category: $$('input[name="category"]:checked').map((el) => el.value) });
  if (t.name === "price") update({ price: t.value });
  if (t.name === "stock") update({ stock: t.checked });
});
grid.addEventListener("click", (e) => {
  const open = e.target.closest("[data-open]");
  if (open) openDetail(booksById.get(Number(open.dataset.open)));
  const add = e.target.closest("[data-add]");
  if (add) addToCart(Number(add.dataset.add), 1, add);
});
$("#pager").addEventListener("click", (e) => {
  const btn = e.target.closest("[data-page]");
  if (!btn) return;
  update({ ...readState(), page: Number(btn.dataset.page) });
  $("#results-heading").scrollIntoView({ block: "start", behavior: "smooth" });
});
window.addEventListener("popstate", loadBooks);

(async function init() {
  user = await mountHeader("browse");
  try {
    categories = await api("/categories");
    renderCategories();
  } catch {
    $("#category-filter").hidden = true;
  }
  if (user) {
    try {
      const cart = await api("/cart");
      inCart = new Map(cart.items.map((i) => [i.book.id, i.quantity]));
    } catch {
      // The grid still works without cart state.
    }
  }
  loadBooks();
})();
