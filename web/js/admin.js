import { api } from "./api.js";
import {
  $, $$, html, money, number, date, orderNo, cover, statusBadge, paymentBadge, STATUS_TEXT,
  requireUser, mountHeader, toast, showErrors, clearErrors, setLoading, formData,
} from "./ui.js";

const NEXT_STATUS = {
  PLACED: ["SHIPPED", "CANCELLED"],
  SHIPPED: ["DELIVERED"],
  DELIVERED: [],
  CANCELLED: [],
};

const formDialog = $("#book-form-dialog");
const form = $("#book-form");
const deleteDialog = $("#delete-dialog");

let categories = [];
let books = new Map();
let editingId = null;
let deletingId = null;
let query = { q: "", page: 1 };
let ordersLoaded = false;

/* ---------- Tabs ---------- */

function selectTab(name) {
  for (const tab of ["books", "orders"]) {
    $(`#tab-${tab}`).setAttribute("aria-selected", String(tab === name));
    $(`#panel-${tab}`).hidden = tab !== name;
  }
  if (name === "orders" && !ordersLoaded) loadOrders();
}
$("#tab-books").addEventListener("click", () => selectTab("books"));
$("#tab-orders").addEventListener("click", () => selectTab("orders"));

/* ---------- Books table ---------- */

const rowsMessage = (text, cols = 6) =>
  html`<tr><td colspan="${cols}" class="muted" style="padding:40px;text-align:center">${text}</td></tr>`;

async function loadBooks() {
  const tbody = $("#book-rows");
  tbody.innerHTML = rowsMessage("Loading…");
  try {
    const page = await api("/admin/books", { query: { q: query.q || null, page: query.page, size: 20 } });
    books = new Map(page.items.map((b) => [b.id, b]));
    $("#book-count").textContent = `${number(page.total)} ${page.total === 1 ? "title" : "titles"}`;
    tbody.innerHTML = page.items.length
      ? html`${page.items.map((b) => html`
        <tr data-id="${b.id}">
          <td><div class="title-cell">${cover(b, { thumb: true })}
            <div><strong>${b.title}</strong><span class="small muted">${b.author}</span></div></div></td>
          <td>${b.categoryName}</td>
          <td class="num small">${b.isbn}</td>
          <td class="r">${money(b.price)}</td>
          <td class="r"><span class="${b.stock === 0 ? "stock--out" : b.stock <= 5 ? "stock--low" : ""}">${b.stock}</span></td>
          <td class="actions">
            <button class="btn btn--ghost btn--sm" type="button" data-edit>Edit</button>
            <button class="btn btn--danger-text btn--sm" type="button" data-delete>Delete</button>
          </td>
        </tr>`)}`
      : rowsMessage(query.q ? `No books match “${query.q}”.` : "No books yet. Add the first one.");

    const pager = $("#admin-pager");
    pager.innerHTML = page.totalPages > 1 ? html`
      <button class="btn btn--secondary btn--sm" type="button" data-page="${page.page - 1}" ${page.page <= 1 ? "disabled" : ""}>Previous</button>
      <span class="num">Page ${page.page} of ${page.totalPages}</span>
      <button class="btn btn--secondary btn--sm" type="button" data-page="${page.page + 1}" ${page.page >= page.totalPages ? "disabled" : ""}>Next</button>` : "";
  } catch (err) {
    tbody.innerHTML = rowsMessage(err.message);
  }
}

let searchTimer;
$("#admin-q").addEventListener("input", (e) => {
  clearTimeout(searchTimer);
  searchTimer = setTimeout(() => {
    query = { q: e.target.value.trim(), page: 1 };
    loadBooks();
  }, 250);
});
$("#admin-pager").addEventListener("click", (e) => {
  const btn = e.target.closest("[data-page]");
  if (btn) {
    query.page = Number(btn.dataset.page);
    loadBooks();
  }
});
$("#book-rows").addEventListener("click", (e) => {
  const row = e.target.closest("tr[data-id]");
  if (!row) return;
  const book = books.get(Number(row.dataset.id));
  if (e.target.closest("[data-edit]")) openForm(book);
  if (e.target.closest("[data-delete]")) confirmDelete(book);
});

/* ---------- Add / edit form ---------- */

function renderPreview() {
  const d = formData(form);
  const color = /^#[0-9a-f]{6}$/i.test(d.coverColor) ? d.coverColor : "#2d5d8a";
  $("#cover-preview").innerHTML = cover({
    id: editingId ?? 0,
    title: d.title || "Untitled",
    subtitle: d.subtitle,
    author: d.author || "Author",
    edition: d.edition,
    coverColor: color,
  });
}

function openForm(book = null) {
  editingId = book?.id ?? null;
  form.reset();
  clearErrors(form);
  $("#book-form-title").textContent = book ? "Edit book" : "Add book";
  $("#book-form-submit").textContent = book ? "Save changes" : "Add book";
  $("#f-category").innerHTML = html`
    <option value="" disabled ${book ? "" : "selected"}>Choose…</option>
    ${categories.map((c) => html`<option value="${c.id}">${c.name}</option>`)}`;

  const values = book ?? { coverColor: "#2d5d8a", stock: 0 };
  for (const [name, key] of Object.entries({
    title: "title", subtitle: "subtitle", edition: "edition", author: "author", isbn: "isbn",
    categoryId: "categoryId", price: "price", listPrice: "listPrice", stock: "stock",
    description: "description", publishedYear: "publishedYear", coverColor: "coverColor",
  })) {
    form.elements[name].value = values[key] ?? "";
  }
  $("#f-color-pick").value = values.coverColor;
  renderPreview();
  formDialog.showModal();
  $("#f-title").focus();
}

$("#add-book").addEventListener("click", () => openForm());
form.addEventListener("input", (e) => {
  if (e.target.id === "f-color-pick") form.elements.coverColor.value = e.target.value;
  if (e.target.name === "coverColor" && /^#[0-9a-f]{6}$/i.test(e.target.value)) $("#f-color-pick").value = e.target.value;
  e.target.removeAttribute("aria-invalid");
  const slot = $(`[data-error-for="${e.target.name}"]`, form);
  if (slot) slot.textContent = "";
  renderPreview();
});

form.addEventListener("submit", async (e) => {
  e.preventDefault();
  const data = formData(form);
  const fields = {};
  if (!data.title.trim()) fields.title = "This field is required.";
  if (data.author.trim().length < 2) fields.author = "This field is required.";
  if (!data.isbn.trim()) fields.isbn = "This field is required.";
  if (!data.categoryId) fields.categoryId = "Choose a category.";
  if (!(Number(data.price) > 0)) fields.price = "Enter a price greater than zero.";
  if (data.listPrice && Number(data.listPrice) <= Number(data.price))
    fields.listPrice = "List price must be higher than the selling price, or leave it empty.";
  if (!/^\d+$/.test(data.stock.trim())) fields.stock = "Enter a whole number, 0 or more.";
  if (Object.keys(fields).length) {
    showErrors(form, { message: "", fields });
    return;
  }

  const button = $("#book-form-submit");
  setLoading(button, true);
  try {
    const saved = editingId
      ? await api(`/admin/books/${editingId}`, { method: "PUT", body: data })
      : await api("/admin/books", { method: "POST", body: data });
    formDialog.close();
    toast(editingId ? `Saved changes to “${saved.title}”` : `Added “${saved.title}” to the catalogue`);
    loadBooks();
  } catch (err) {
    showErrors(form, err);
  } finally {
    setLoading(button, false);
  }
});

/* ---------- Delete ---------- */

function confirmDelete(book) {
  deletingId = book.id;
  $("#delete-text").textContent =
    `“${book.title}” will be removed from the catalogue and from customers' carts. Past orders keep their copy of the title and price.`;
  deleteDialog.showModal();
}

$("#confirm-delete").addEventListener("click", async (e) => {
  const button = e.currentTarget;
  setLoading(button, true);
  try {
    const title = books.get(deletingId)?.title;
    await api(`/admin/books/${deletingId}`, { method: "DELETE" });
    deleteDialog.close();
    toast(`Deleted “${title}”`);
    loadBooks();
  } catch (err) {
    deleteDialog.close();
    toast(err.message, { error: true });
  } finally {
    setLoading(button, false);
  }
});

$$("dialog").forEach((d) => {
  $$("[data-close]", d).forEach((b) => b.addEventListener("click", () => d.close()));
  d.addEventListener("click", (e) => {
    if (e.target === d) d.close();
  });
});

/* ---------- Orders ---------- */

function orderRow(o) {
  const awaitingPayment = o.paymentMethod === "ONLINE" && o.paymentStatus === "UNPAID";
  // The server refuses to ship an unpaid online order; don't offer it.
  const next = NEXT_STATUS[o.status].filter((s) => !(awaitingPayment && s === "SHIPPED"));
  return html`
    <tr data-id="${o.id}">
      <td class="num"><strong>${orderNo(o.id)}</strong></td>
      <td>${o.customerName}<div class="small muted">${o.shippingPhone}</div></td>
      <td class="num">${date(o.createdAt)}</td>
      <td class="r">${o.items.reduce((n, i) => n + i.quantity, 0)}</td>
      <td class="r">${money(o.total)}</td>
      <td>${paymentBadge(o)}</td>
      <td>${next.length
        ? html`<label class="visually-hidden" for="status-${o.id}">Status of ${orderNo(o.id)}</label>
          <select class="select" id="status-${o.id}" data-status>
            <option value="${o.status}" selected>${STATUS_TEXT[o.status]}</option>
            ${next.map((s) => html`<option value="${s}">Mark ${STATUS_TEXT[s].toLowerCase()}</option>`)}
          </select>`
        : statusBadge(o.status)}</td>
    </tr>`;
}

async function loadOrders() {
  const tbody = $("#order-rows");
  tbody.innerHTML = rowsMessage("Loading…", 7);
  try {
    const orders = await api("/admin/orders");
    ordersLoaded = true;
    tbody.innerHTML = orders.length ? html`${orders.map(orderRow)}` : rowsMessage("No orders have been placed yet.", 7);
  } catch (err) {
    tbody.innerHTML = rowsMessage(err.message, 7);
  }
}

$("#order-rows").addEventListener("change", async (e) => {
  if (!e.target.matches("[data-status]")) return;
  const row = e.target.closest("tr");
  const id = row.dataset.id;
  const status = e.target.value;
  if (status === "CANCELLED" && !confirm(`Cancel ${orderNo(id)}? Its copies go back into stock.`)) {
    e.target.selectedIndex = 0;
    return;
  }
  e.target.disabled = true;
  try {
    const order = await api(`/admin/orders/${id}/status`, { method: "PUT", body: { status } });
    row.outerHTML = orderRow(order).toString();
    toast(`${orderNo(id)} marked ${STATUS_TEXT[order.status].toLowerCase()}`);
    if (status === "CANCELLED") loadBooks();
  } catch (err) {
    e.target.disabled = false;
    e.target.selectedIndex = 0;
    toast(err.message, { error: true });
  }
});

(async function init() {
  await requireUser({ admin: true });
  await mountHeader("admin");
  try {
    categories = await api("/categories");
  } catch (err) {
    toast(err.message, { error: true });
  }
  loadBooks();
})();
