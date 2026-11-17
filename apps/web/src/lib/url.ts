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
  // The browser forgives what the server's java.net.URI does not. "linkedin.com/in/john doe" or "my_company.com"
  // passed the form, the server dropped it as unparseable, and the save wiped the URL that was stored.
  if (!parsesAsServerUri(candidate)) return null;
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

/** Characters `java.net.URI` refuses anywhere, and a `%` that does not start an escape. */
const URI_ILLEGAL = /[\s<>"{}|\\^`]|%(?![0-9A-Fa-f]{2})/;
const HOST_LABEL = /^[A-Za-z0-9](?:[A-Za-z0-9-]*[A-Za-z0-9])?$/;
const IPV4 = /^\d{1,3}(?:\.\d{1,3}){3}$/;

/**
 * Whether the server's `SuppliedText.browsableUrlOrNull` keeps this address: `URI.create` parses it and finds a host,
 * which Java does only for a registry name of letters, digits and inner hyphens whose last label starts with a letter,
 * an IPv4 address, or a bracketed IPv6 literal.
 */
function parsesAsServerUri(candidate: string): boolean {
  if (URI_ILLEGAL.test(candidate) || (candidate.match(/#/g) ?? []).length > 1) return false;
  const afterScheme = candidate.slice(candidate.indexOf("://") + 3);
  const authority = afterScheme.split(/[/?#]/, 1)[0];
  // Brackets are a query's or a fragment's to carry, and an IPv6 host's; in a path or a login they refuse the URI.
  const path = afterScheme.slice(authority.length).split(/[?#]/, 1)[0];
  const login = authority.split("@");
  if (/[[\]]/.test(path) || login.length > 2 || (login.length === 2 && /[[\]]/.test(login[0]))) return false;
  const hostAndPort = login[login.length - 1];
  if (hostAndPort.startsWith("[")) return /^\[[0-9A-Fa-f:.]+\](?::\d*)?$/.test(hostAndPort);
  const host = hostAndPort.replace(/:\d*$/, "").replace(/\.$/, "");
  if (IPV4.test(host)) return host.split(".").every((octet) => Number(octet) <= 255);
  const labels = host.split(".");
  return labels.every((label) => HOST_LABEL.test(label)) && /^[A-Za-z]/.test(labels[labels.length - 1]);
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
