import { api } from "./api.js";
import {
  $, html, money, date, orderNo, cover, statusBadge, paymentBadge, requireUser, mountHeader, toast, setLoading,
} from "./ui.js";
import { payForOrder } from "./payment.js";

const root = $("#orders-root");
let orders = [];

function orderCard(order) {
  const count = order.items.reduce((n, i) => n + i.quantity, 0);
  return html`
    <article class="order" id="order-${order.id}">
      <dl class="order__head">
        <div><dt>Order</dt><dd>${orderNo(order.id)}</dd></div>
        <div><dt>Placed on</dt><dd>${date(order.createdAt)}</dd></div>
        <div><dt>Items</dt><dd>${count}</dd></div>
        <div><dt>Total</dt><dd>${money(order.total)}</dd></div>
        <div><dt>Payment</dt><dd>${paymentBadge(order)}</dd></div>
        <div>${statusBadge(order.status)}</div>
      </dl>
      ${awaitingPayment(order) ? html`
        <div class="order__pay">
          <p><strong>Payment pending.</strong> Your books are reserved. Pay to get this order shipped.</p>
          <button class="btn btn--primary btn--sm" type="button" data-pay="${order.id}">Pay ${money(order.total)}</button>
        </div>` : ""}
      <div class="order__items">
        ${order.items.map((item) => html`
          <div class="order__item">
            ${cover({ title: item.title, coverColor: item.coverColor ?? "#cec7b8" }, { thumb: true })}
            <div>
              <p style="font-weight:600">${item.title}</p>
              <p class="small muted">${item.author} · Qty ${item.quantity}</p>
            </div>
            <span class="num">${money(Number(item.unitPrice) * item.quantity)}</span>
          </div>`)}
      </div>
      <p class="order__ship">
        Delivering to <strong>${order.shippingName}</strong>, ${order.shippingAddress} · ${order.shippingPhone}
        ${Number(order.shipping) > 0 ? html` · Delivery ${money(order.shipping)}` : html` · Free delivery`}
        ${order.paymentRef ? html`<br>Payment reference <span class="ref">${order.paymentRef}</span>` : ""}
      </p>
      ${order.paymentStatus === "PAID" ? invoiceLinks(order.id) : ""}
    </article>`;
}

function invoiceLinks(id) {
  return html`
    <div class="order__invoice">
      <span class="small muted">Invoice for ${orderNo(id)}</span>
      <a class="btn btn--ghost btn--sm" href="/api/orders/${id}/invoice" target="_blank" rel="noopener">View invoice</a>
      <a class="btn btn--secondary btn--sm" href="/api/orders/${id}/invoice?download=1" download>Download PDF</a>
    </div>`;
}

const awaitingPayment = (o) => o.paymentMethod === "ONLINE" && o.paymentStatus === "UNPAID" && o.status === "PLACED";

function banner(placed, payment) {
  const order = orders.find((o) => o.id === placed);
  if (!order) return "";
  const no = orderNo(placed);
  if (order.paymentStatus === "PAID" && order.paymentMethod === "ONLINE") {
    return html`<div class="banner" role="status"><strong>Payment received. Order ${no} is confirmed.</strong>
      <span>We'll ship it soon. Your invoice is ready:</span>
      <a class="banner__link" href="/api/orders/${placed}/invoice?download=1" download>Download invoice (PDF)</a></div>`;
  }
  if (awaitingPayment(order)) {
    const text = payment === "failed"
      ? "The payment didn't go through, and no money was taken."
      : "The payment window was closed before paying.";
    return html`<div class="banner banner--warn" role="status"><strong>Order ${no} is waiting for payment.</strong>
      <span>${text} Use “Pay” below to try again.</span></div>`;
  }
  return html`<div class="banner" role="status"><strong>Order ${no} is confirmed.</strong>
    <span>Pay by cash or UPI on delivery. Status updates will appear here.</span></div>`;
}

function render(placed, payment) {
  if (orders.length === 0) {
    $("#orders-sub").textContent = "You haven't ordered anything yet.";
    root.innerHTML = html`
      <div class="state">
        <h2>No orders yet</h2>
        <p>When you check out, your orders and their delivery status will appear here.</p>
        <a class="btn btn--primary" href="/">Browse books</a>
      </div>`;
    return;
  }
  $("#orders-sub").textContent = `${orders.length} ${orders.length === 1 ? "order" : "orders"}, newest first.`;
  root.innerHTML = html`${placed ? banner(placed, payment) : ""}${orders.map(orderCard)}`;
}

root.addEventListener("click", async (e) => {
  const btn = e.target.closest("[data-pay]");
  if (!btn) return;
  const id = Number(btn.dataset.pay);
  setLoading(btn, true);
  try {
    const outcome = await payForOrder(id);
    orders = await api("/orders");
    render(outcome === "paid" ? id : null, null);
    if (outcome === "paid") toast(`Payment received for ${orderNo(id)}`);
  } catch (err) {
    toast(err.message, { error: true });
  } finally {
    if (btn.isConnected) setLoading(btn, false);
  }
});

(async function init() {
  await requireUser();
  await mountHeader("orders");

  const params = new URLSearchParams(location.search);
  const placed = Number(params.get("placed")) || null;
  const payment = params.get("payment");
  try {
    orders = await api("/orders");
    render(placed, payment);
    if (placed) history.replaceState(null, "", location.pathname);
  } catch (err) {
    $("#orders-sub").textContent = "";
    root.innerHTML = html`
      <div class="state">
        <h2>We couldn't load your orders</h2>
        <p>${err.message}</p>
        <button class="btn btn--secondary" type="button" onclick="location.reload()">Try again</button>
      </div>`;
  }
})();
