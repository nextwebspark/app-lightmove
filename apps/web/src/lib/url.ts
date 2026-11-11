/**
 * What counts as a browsable address, mirroring the server's `CapturedCompanyDetails` normalisation, so forms
 * catch typos on screen and a value stored before normalisation still never reaches an `href` unsafely.
 */

/**
 * An absolute http(s) URL, or null. A bare host is promoted (else it is a relative SPA link); any other
 * scheme — `javascript:` above all — is null.
 */
export function toBrowsableUrl(value: string | null | undefined): string | null {
  if (!value) return null;
  const trimmed = value.trim();
  if (!trimmed) return null;

  // A scheme-relative "//host" is absolute to the browser but carries no scheme of its own, so it
  // would inherit the page's — harmless here, but it is not what the server stores, and the two must
  // agree. Treated as a bare host.
  const candidate = trimmed.includes("://") ? trimmed : `https://${trimmed.replace(/^\/+/, "")}`;
  try {
    const parsed = new URL(candidate);
    if (parsed.protocol !== "http:" && parsed.protocol !== "https:") return null;
    // `https://javascript:alert(1)` parses, but with no host — the promotion must not launder a
    // scheme the guard above would have rejected.
    return parsed.hostname ? candidate : null;
  } catch {
    return null;
  }
}

/**
 * A URL as a person reads it: no scheme, no `www.`, no trailing slash — `linkedin.com/in/hakanalac`
 * rather than the address bar's spelling. The href stays the full URL; this is only what is printed.
 */
export function toReadableUrl(url: string): string {
  return url
    .replace(/^https?:\/\//i, "")
    .replace(/^www\./i, "")
    .replace(/\/+$/, "");
}
