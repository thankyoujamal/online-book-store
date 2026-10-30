import { api } from "./api.js";
import { $, $$, mountHeader, showErrors, clearErrors, setLoading, formData } from "./ui.js";

const EMAIL = /^[^@\s]+@[^@\s]+\.[A-Za-z]{2,}$/;

/** Only allow same-site relative redirects, so ?next= can't send users elsewhere. */
function nextUrl() {
  const next = new URLSearchParams(location.search).get("next");
  return next && next.startsWith("/") && !next.startsWith("//") ? next : "/";
}

function show(panel) {
  $("#panel-login").hidden = panel !== "login";
  $("#panel-register").hidden = panel !== "register";
  document.title = (panel === "login" ? "Sign in" : "Create an account") + " · Online Book Store";
  $(`#panel-${panel} input`).focus();
}

/* Client-side checks mirror the server rules; the server remains the authority. */
function validateLogin(data) {
  const fields = {};
  if (!EMAIL.test(data.email.trim())) fields.email = "Enter a valid email address, like name@example.com.";
  if (!data.password) fields.password = "This field is required.";
  return fields;
}

function validateRegister(data) {
  const fields = {};
  if (data.fullName.trim().length < 2) fields.fullName = "Enter your full name.";
  if (!EMAIL.test(data.email.trim())) fields.email = "Enter a valid email address, like name@example.com.";
  if (data.password.length < 8) fields.password = "Use at least 8 characters.";
  else if (!/[A-Za-z]/.test(data.password) || !/\d/.test(data.password))
    fields.password = "Include at least one letter and one number.";
  if (data.confirmPassword !== data.password) fields.confirmPassword = "Passwords do not match.";
  return fields;
}

function bind(form, path, validate) {
  form.addEventListener("submit", async (e) => {
    e.preventDefault();
    const data = formData(form);
    const fields = validate(data);
    if (Object.keys(fields).length) {
      showErrors(form, { message: "", fields });
      return;
    }
    clearErrors(form);
    const button = $('button[type="submit"]', form);
    setLoading(button, true);
    try {
      await api(path, { method: "POST", body: data });
      location.replace(nextUrl());
    } catch (err) {
      showErrors(form, err);
      setLoading(button, false);
    }
  });
  // Clear a field's error as soon as the user edits it.
  form.addEventListener("input", (e) => {
    e.target.removeAttribute("aria-invalid");
    const slot = $(`[data-error-for="${e.target.name}"]`, form);
    if (slot) slot.textContent = "";
  });
}

bind($("#login-form"), "/auth/login", validateLogin);
bind($("#register-form"), "/auth/register", validateRegister);

$$("[data-switch]").forEach((a) =>
  a.addEventListener("click", (e) => {
    e.preventDefault();
    history.replaceState(null, "", location.search + "#" + a.dataset.switch);
    show(a.dataset.switch);
  }));

(async function init() {
  const user = await mountHeader(null);
  if (user) {
    location.replace(nextUrl());
    return;
  }
  show(location.hash === "#register" ? "register" : "login");
})();
