export class ApiError extends Error {
  constructor(status, message, fields = {}) {
    super(message);
    this.status = status;
    this.fields = fields;
  }
}

/** Encodes an object as form data. Arrays become repeated keys; null/undefined are skipped. */
function encode(data) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(data)) {
    if (value == null) continue;
    if (Array.isArray(value)) value.forEach((v) => params.append(key, v));
    else params.append(key, value);
  }
  return params;
}

export async function api(path, { method = "GET", body, query } = {}) {
  let url = "/api" + path;
  if (query) {
    const qs = encode(query).toString();
    if (qs) url += "?" + qs;
  }

  let response;
  try {
    response = await fetch(url, {
      method,
      credentials: "same-origin",
      headers: body ? { "Content-Type": "application/x-www-form-urlencoded;charset=UTF-8" } : undefined,
      body: body ? encode(body) : undefined,
    });
  } catch {
    throw new ApiError(0, "Can't reach the store. Check your connection and try again.");
  }

  if (response.status === 204) return null;

  let payload = null;
  try {
    payload = await response.json();
  } catch {
    // Non-JSON response (e.g. a proxy error page); handled below.
  }

  if (!response.ok) {
    throw new ApiError(
      response.status,
      payload?.error ?? `Request failed (${response.status}).`,
      payload?.fields ?? {}
    );
  }
  return payload;
}
