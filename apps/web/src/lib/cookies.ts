/**
 * Script-owned cookies: non-secret values only. The auth cookies are `httpOnly` and must stay out of reach
 * of this module.
 */

/** The value, or null when the cookie is absent. */
export function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(^|;\\s*)${escapeForRegExp(name)}=([^;]*)`));
  return match ? decodeURIComponent(match[2]) : null;
}

/**
 * A site-wide first-party cookie. `SameSite=Lax` so the OAuth popup's cross-site return carries it;
 * `Secure` follows the page's protocol, since local dev is plain http.
 */
export function writeCookie(name: string, value: string, maxAgeSeconds: number): void {
  const attributes = [
    `${name}=${encodeURIComponent(value)}`,
    "Path=/",
    `Max-Age=${maxAgeSeconds}`,
    "SameSite=Lax",
  ];

  if (window.location.protocol === "https:") {
    attributes.push("Secure");
  }

  document.cookie = attributes.join("; ");
}

/** Expires a cookie written by {@link writeCookie}. Attributes must match for the browser to match it. */
export function deleteCookie(name: string): void {
  writeCookie(name, "", 0);
}

function escapeForRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}
