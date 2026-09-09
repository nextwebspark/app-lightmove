/**
 * Reading and writing the cookies this app owns from script.
 *
 * Only ever non-secret, non-session values. The two cookies that matter for auth — the refresh token
 * and the session — are `httpOnly` and deliberately invisible here: that is what stops a compromised
 * dependency walking away with a credential. Nothing in this module can reach them, and nothing
 * should be added that tries.
 */

/** The value, or null when the cookie is absent. */
export function readCookie(name: string): string | null {
  const match = document.cookie.match(new RegExp(`(^|;\\s*)${escapeForRegExp(name)}=([^;]*)`));
  return match ? decodeURIComponent(match[2]) : null;
}

/**
 * Writes a first-party cookie scoped to the whole site.
 *
 * `SameSite=Lax` rather than `Strict` because the one caller — the OAuth popup handshake — needs the
 * cookie sent on the top-level navigation *back* from the identity provider, which is cross-site.
 * Never `Secure` unconditionally: local development is plain http, and a Secure cookie is dropped
 * there, so it follows the page's own protocol.
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
