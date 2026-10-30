import { api } from "./api.js";
import {
  $, html, money, cover, stockLabel, requireUser, mountHeader, setCartCount,
  toast, showErrors, clearErrors, setLoading, formData,
} from "./ui.js";
import { paymentConfig, payForOrder } from "./payment.js";

const MAX_PER_TITLE = 10;
const root = $("#cart-root");
let user;
let cart;
let payments = { onlineEnabled: false, testMode: false };
// Keep what the user typed if the cart re-renders (quantity change, removal).
let draft = {};

function line(item) {
  const { book, quantity } = item;
  const max = Math.min(book.stock, MAX_PER_TITLE);
  return html`
    <li class="line" data-id="${book.id}">
      ${cover(book)}
      <div>
        <p class="line__title">${book.title}</p>
        <p class="line__author">${book.author}${book.edition ? html` · ${book.edition}` : ""}</p>
        ${book.stock < quantity ? html`<p class="stock stock--out">Only ${book.stock} left. Lower the quantity to check out.</p>` : stockLabel(book.stock)}
        <div class="line__controls">
          <div class="stepper stepper--sm" role="group" aria-label="Quantity for ${book.title}">
            <button type="button" data-qty="${quantity - 1}" aria-label="Decrease">−</button>
            <output class="num">${quantity}</output>
            <button type="button" data-qty="${quantity + 1}" aria-label="Increase" ${quantity >= max ? "disabled" : ""}>+</button>
          </div>
          <button class="btn btn--ghost btn--sm" type="button" data-remove>Remove</button>
        </div>
      </div>
      <div class="line__price">
        <span class="price">${money(Number(book.price) * quantity)}</span>
        ${quantity > 1 ? html`<p class="line__each num">${money(book.price)} each</p>` : ""}
      </div>
    </li>`;
}

function summary() {
  const remaining = Number(cart.freeShippingThreshold) - Number(cart.subtotal);
  return html`
    <aside class="summary" aria-labelledby="summary-title">
      <h2 class="h3" id="summary-title">Order summary</h2>
      <div class="summary__rows" style="margin-top:16px">
        <div class="summary__row"><span>Subtotal · ${cart.itemCount} ${cart.itemCount === 1 ? "item" : "items"}</span><span>${money(cart.subtotal)}</span></div>
        <div class="summary__row"><span>Delivery</span><span>${Number(cart.shipping) === 0 ? "Free" : money(cart.shipping)}</span></div>
        <div class="summary__row summary__row--total"><span>Total</span><span>${money(cart.total)}</span></div>
      </div>
      ${remaining > 0 ? html`<p class="summary__note">Add ${money(remaining)} more for free delivery.</p>` : ""}

      <form id="checkout" novalidate>
        <h3 class="h3">Delivery details</h3>
        <div class="form-alert" role="alert" tabindex="-1"></div>
        <div class="field">
          <label for="ship-name">Full name</label>
          <input class="input" id="ship-name" name="shippingName" autocomplete="name" required maxlength="80" value="${draft.shippingName ?? user.fullName}">
          <span class="error" data-error-for="shippingName"></span>
        </div>
        <div class="field">
          <label for="ship-phone">Mobile number</label>
          <input class="input" id="ship-phone" name="shippingPhone" type="tel" inputmode="tel" autocomplete="tel" required maxlength="16" placeholder="98765 43210" value="${draft.shippingPhone ?? ""}">
          <span class="error" data-error-for="shippingPhone"></span>
        </div>
        <div class="field">
          <label for="ship-address">Address</label>
          <textarea class="textarea" id="ship-address" name="shippingAddress" autocomplete="street-address" required maxlength="400" rows="3" placeholder="House no., street, area, city, PIN code">${draft.shippingAddress ?? ""}</textarea>
          <span class="error" data-error-for="shippingAddress"></span>
        </div>
        ${paymentChoice()}
        <button class="btn btn--primary btn--block" type="submit" id="place-order">${submitLabel(selectedMethod())}</button>
      </form>
    </aside>`;
}

function selectedMethod() {
  if (!payments.onlineEnabled) return "COD";
  return draft.paymentMethod ?? "ONLINE";
}

function submitLabel(method) {
  return method === "ONLINE" ? `Pay ${money(cart.total)}` : `Place order · ${money(cart.total)}`;
}

function paymentChoice() {
  if (!payments.onlineEnabled) {
    return html`<input type="hidden" name="paymentMethod" value="COD">
      <p class="faint small">Pay by cash or UPI when your books arrive.</p>`;
  }
  const method = selectedMethod();
  const option = (value, title, detail) => html`
    <label class="pay-option">
      <input type="radio" name="paymentMethod" value="${value}" ${method === value ? "checked" : ""}>
      <span><strong>${title}</strong><span class="small muted">${detail}</span></span>
    </label>`;
  return html`
    <fieldset class="pay-options">
      <legend class="field-label">Payment</legend>
      ${option("ONLINE", "Pay online", payments.gateway === "RAZORPAY"
        ? "UPI, cards, netbanking and wallets via Razorpay"
        : "UPI, cards and netbanking")}
      ${option("COD", "Cash on delivery", "Pay by cash or UPI when your books arrive")}
      ${payments.testMode ? html`<p class="test-note">${payments.gateway === "RAZORPAY"
        ? html`Test mode: no real money is charged. Use UPI ID <code>success@razorpay</code> or card <code>4111 1111 1111 1111</code>.`
        : "Demo payments: no real money is charged."}</p>` : ""}
    </fieldset>`;
}

function render() {
  setCartCount(cart.itemCount);
  if (cart.items.length === 0) {
    $("#cart-sub").textContent = "Nothing here yet.";
    root.innerHTML = html`
      <div class="state">
        <h2>Your cart is empty</h2>
        <p>Find something worth reading. Books you add will wait here until you check out.</p>
        <a class="btn btn--primary" href="/">Browse books</a>
      </div>`;
    return;
  }
  $("#cart-sub").textContent = `${cart.itemCount} ${cart.itemCount === 1 ? "book" : "books"} ready for checkout.`;
  root.innerHTML = html`
    <div class="cart">
      <ul class="lines" style="list-style:none;margin:0;padding:0">${cart.items.map(line)}</ul>
      ${summary()}
    </div>`;
  $("#checkout").addEventListener("submit", checkout);
  $("#checkout").addEventListener("input", (e) => {
    draft[e.target.name] = e.target.value;
    e.target.removeAttribute("aria-invalid");
    if (e.target.name === "paymentMethod") $("#place-order").textContent = submitLabel(e.target.value);
  });
}

async function mutate(li, request) {
  li.setAttribute("data-busy", "");
  try {
    cart = await request;
    render();
  } catch (err) {
    li.removeAttribute("data-busy");
    toast(err.message, { error: true });
  }
}

root.addEventListener("click", (e) => {
  const li = e.target.closest(".line");
  if (!li) return;
  const id = li.dataset.id;
  const qtyBtn = e.target.closest("[data-qty]");
  if (qtyBtn) {
    const quantity = Number(qtyBtn.dataset.qty);
    mutate(li, quantity < 1
      ? api(`/cart/items/${id}`, { method: "DELETE" })
      : api(`/cart/items/${id}`, { method: "PUT", body: { quantity } }));
  }
  if (e.target.closest("[data-remove]")) {
    mutate(li, api(`/cart/items/${id}`, { method: "DELETE" }));
  }
});

async function checkout(e) {
  e.preventDefault();
  const form = e.currentTarget;
  const data = formData(form);
  const fields = {};
  if (data.shippingName.trim().length < 2) fields.shippingName = "Enter the recipient's name.";
  if (!/^[+]?[0-9 ]{10,15}$/.test(data.shippingPhone.trim())) fields.shippingPhone = "Enter a 10-digit mobile number.";
  if (data.shippingAddress.trim().length < 10) fields.shippingAddress = "Enter the full address, including city and PIN code.";
  if (Object.keys(fields).length) {
    showErrors(form, { message: "", fields });
    return;
  }

  clearErrors(form);
  const button = $('button[type="submit"]', form);
  setLoading(button, true);
  let order;
  try {
    order = await api("/orders", { method: "POST", body: data });
  } catch (err) {
    setLoading(button, false);
    showErrors(form, err);
    // Stock may have changed under us; refresh lines so the problem is visible.
    if (err.status === 409) {
      try {
        cart = await api("/cart");
        render();
        if ($("#checkout")) showErrors($("#checkout"), err);
      } catch {
        // Keep the current view; the error is already shown on the form.
      }
    }
    return;
  }

  if (order.paymentMethod !== "ONLINE") {
    location.href = `/orders.html?placed=${order.id}`;
    return;
  }

  // The order exists and stock is reserved; now take the payment.
  setCartCount(0);
  let outcome = "pending";
  try {
    outcome = (await payForOrder(order.id)) === "paid" ? "paid" : "pending";
  } catch {
    // Shown on the orders page, where the customer can retry with "Pay now".
    outcome = "failed";
  }
  location.href = `/orders.html?placed=${order.id}&payment=${outcome}`;
}

(async function init() {
  user = await requireUser();
  await mountHeader("cart");
  try {
    [cart, payments] = await Promise.all([api("/cart"), paymentConfig()]);
    render();
  } catch (err) {
    $("#cart-sub").textContent = "";
    root.innerHTML = html`
      <div class="state">
        <h2>We couldn't load your cart</h2>
        <p>${err.message}</p>
        <button class="btn btn--secondary" type="button" onclick="location.reload()">Try again</button>
      </div>`;
  }
})();
