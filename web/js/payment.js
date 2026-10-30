import { api } from "./api.js";
import { $, $$, html, icons, money, orderNo, showErrors, clearErrors } from "./ui.js";

let configPromise = null;
/** { onlineEnabled, gateway: "DEMO" | "RAZORPAY" | null, testMode } from the server. */
export function paymentConfig() {
  configPromise ??= api("/payments/config").catch(() => ({ onlineEnabled: false, gateway: null, testMode: false }));
  return configPromise;
}

/**
 * Takes payment for an already-placed order through whichever gateway the store uses.
 * Resolves "paid" once the server has verified the payment, or "dismissed" if the
 * customer closed the window. Rejects if the gateway or verification fails.
 */
export async function payForOrder(orderId) {
  const checkout = await api(`/orders/${orderId}/payment`, { method: "POST" });
  const result = checkout.gateway === "RAZORPAY"
    ? await razorpayCheckout(checkout)
    : await demoCheckout(checkout);
  if (!result) return "dismissed";
  await api(`/orders/${orderId}/payment/verify`, { method: "POST", body: result });
  return "paid";
}

/* ---------- Razorpay Checkout ---------- */

let razorpayLoader = null;
function loadRazorpay() {
  razorpayLoader ??= new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = "https://checkout.razorpay.com/v1/checkout.js";
    script.onload = () => resolve(window.Razorpay);
    script.onerror = () => {
      razorpayLoader = null; // allow a retry
      reject(new Error("The payment window didn't load. Check your internet connection and try again."));
    };
    document.head.append(script);
  });
  return razorpayLoader;
}

async function razorpayCheckout(checkout) {
  const Razorpay = await loadRazorpay();
  return new Promise((resolve) => {
    new Razorpay({
      key: checkout.keyId,
      order_id: checkout.gatewayOrderId,
      amount: checkout.amount,
      currency: checkout.currency,
      name: "Online Book Store",
      description: `Order ${orderNo(checkout.orderId)}`,
      prefill: checkout.prefill,
      theme: { color: "#1f3a5f" },
      handler: (r) => resolve({
        gatewayOrderId: r.razorpay_order_id,
        paymentId: r.razorpay_payment_id,
        signature: r.razorpay_signature,
      }),
      modal: { ondismiss: () => resolve(null), confirm_close: true },
    }).open();
  });
}

/* ---------- Built-in demo gateway ---------- */

const BANKS = [
  ["SBI", "State Bank of India"],
  ["HDFC", "HDFC Bank"],
  ["ICICI", "ICICI Bank"],
  ["AXIS", "Axis Bank"],
  ["KOTAK", "Kotak Mahindra Bank"],
  ["DECLINE", "Declining Demo Bank"],
];
const MIN_PROCESSING_MS = 1100; // long enough to read "Processing", short enough not to annoy

const wait = (ms) => new Promise((r) => setTimeout(r, ms));

function demoMarkup(checkout) {
  const amount = money(checkout.amount / 100);
  const field = (name, label, attrs, hint = "") => html`
    <div class="field">
      <label for="pw-${name}">${label}</label>
      <input class="input" id="pw-${name}" name="${name}" ${attrs}>
      ${hint ? html`<span class="hint">${hint}</span>` : ""}
      <span class="error" data-error-for="${name}"></span>
    </div>`;

  return html`
    <div class="paywin__head">
      <div>
        <p class="paywin__brand">DemoPay <span class="paywin__tag">Test mode</span></p>
        <p class="small muted">Online Book Store · ${orderNo(checkout.orderId)}</p>
      </div>
      <p class="paywin__amount num">${amount}</p>
      <button class="dialog__close" type="button" data-close aria-label="Cancel payment">${icons.close}</button>
    </div>

    <div class="paywin__tabs" role="tablist">
      <button type="button" role="tab" data-method="UPI" aria-selected="true">UPI</button>
      <button type="button" role="tab" data-method="CARD" aria-selected="false">Card</button>
      <button type="button" role="tab" data-method="NETBANKING" aria-selected="false">Netbanking</button>
    </div>

    <form class="paywin__body" novalidate>
      <div data-panel="UPI">
        ${field("upiId", "UPI ID", html`autocomplete="off" placeholder="name@okbank" inputmode="email"`,
          html`Any ID like <code>name@okbank</code> works. <code>failure@demo</code> is declined.`)}
      </div>
      <div data-panel="CARD" hidden>
        ${field("cardNumber", "Card number", html`autocomplete="off" inputmode="numeric" maxlength="23" placeholder="4111 1111 1111 1111"`,
          html`Use <code>4111 1111 1111 1111</code>. <code>4000 0000 0000 0002</code> is declined.`)}
        <div class="paywin__row">
          ${field("cardExpiry", "Expiry", html`autocomplete="off" inputmode="numeric" maxlength="5" placeholder="MM/YY"`)}
          ${field("cardCvv", "CVV", html`autocomplete="off" inputmode="numeric" maxlength="4" placeholder="123" type="password"`)}
        </div>
        ${field("cardName", "Name on card", html`autocomplete="off" value="${checkout.prefill?.name ?? ""}"`)}
      </div>
      <div data-panel="NETBANKING" hidden>
        <fieldset class="paywin__banks">
          <legend class="field-label">Choose your bank</legend>
          ${BANKS.map(([code, name], i) => html`
            <label class="pay-option"><input type="radio" name="bank" value="${code}" ${i === 0 ? "checked" : ""}>
              <span><strong>${name}</strong></span></label>`)}
        </fieldset>
        <span class="error" data-error-for="bank"></span>
      </div>

      <div class="form-alert" role="alert" tabindex="-1"></div>
      <button class="btn btn--primary btn--block" type="submit">Pay ${amount}</button>
      <p class="paywin__note">Demo gateway: no real money moves and no card details are stored.</p>
    </form>

    <div class="paywin__state" hidden aria-live="polite">
      <div class="paywin__spinner" aria-hidden="true"></div>
      <p class="h3" data-state-text>Processing payment…</p>
      <p class="small muted">Don't close this window.</p>
    </div>`;
}

function demoCheckout(checkout) {
  const dialog = document.createElement("dialog");
  dialog.className = "dialog--sm paywin";
  dialog.setAttribute("aria-label", "DemoPay payment");
  dialog.innerHTML = demoMarkup(checkout);
  document.body.append(dialog);

  const form = $("form", dialog);
  const state = $(".paywin__state", dialog);
  let method = "UPI";
  let busy = false;

  const selectMethod = (m) => {
    method = m;
    $$("[data-method]", dialog).forEach((t) => t.setAttribute("aria-selected", String(t.dataset.method === m)));
    $$("[data-panel]", dialog).forEach((p) => (p.hidden = p.dataset.panel !== m));
    clearErrors(form);
    $(`[data-panel="${m}"] input`, dialog)?.focus();
  };
  $$("[data-method]", dialog).forEach((t) => t.addEventListener("click", () => selectMethod(t.dataset.method)));

  // Format card fields as the customer types.
  form.addEventListener("input", (e) => {
    const t = e.target;
    if (t.name === "cardNumber") t.value = t.value.replace(/\D/g, "").slice(0, 19).replace(/(\d{4})(?=\d)/g, "$1 ");
    if (t.name === "cardExpiry") {
      const d = t.value.replace(/\D/g, "").slice(0, 4);
      t.value = d.length > 2 ? `${d.slice(0, 2)}/${d.slice(2)}` : d;
    }
    if (t.name === "cardCvv") t.value = t.value.replace(/\D/g, "");
    t.removeAttribute("aria-invalid");
    const slot = $(`[data-error-for="${t.name}"]`, form);
    if (slot) slot.textContent = "";
  });

  const showState = (on, text = "Processing payment…", done = false) => {
    form.hidden = on;
    $(".paywin__tabs", dialog).hidden = on;
    state.hidden = !on;
    state.classList.toggle("paywin__state--done", done);
    $("[data-state-text]", state).textContent = text;
  };

  return new Promise((resolve) => {
    const finish = (value) => {
      dialog.close();
      dialog.remove();
      resolve(value);
    };

    $("[data-close]", dialog).addEventListener("click", () => {
      if (!busy) finish(null);
    });
    dialog.addEventListener("cancel", (e) => {
      e.preventDefault(); // Esc
      if (!busy) finish(null);
    });

    form.addEventListener("submit", async (e) => {
      e.preventDefault();
      clearErrors(form);
      const panel = $(`[data-panel="${method}"]`, dialog);
      const body = { gatewayOrderId: checkout.gatewayOrderId, method };
      $$("input", panel).forEach((i) => {
        if (i.type !== "radio" || i.checked) body[i.name] = i.value;
      });

      busy = true;
      showState(true);
      try {
        const [result] = await Promise.all([
          api("/demo-gateway/charge", { method: "POST", body }),
          wait(MIN_PROCESSING_MS),
        ]);
        showState(true, "Payment successful", true);
        await wait(700);
        busy = false;
        finish(result);
      } catch (err) {
        busy = false;
        showState(false);
        showErrors(form, err);
      }
    });

    dialog.showModal();
    $("#pw-upiId", dialog).focus();
  });
}
