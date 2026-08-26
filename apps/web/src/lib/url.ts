/**
 * What counts as an address a browser will follow, and how a typed one becomes it.
 *
 * <p>The rule has to be the same on both sides of the wire. The server normalises a captured URL in
 * `CapturedCompanyDetails`, and this is its mirror: the form validates against it before posting, so a
 * typo is caught while the field is still on screen rather than silently dropped server-side, and the
 * grid renders through it, so a value stored before that normalisation existed still can't reach an
 * `href` as something a browser shouldn't follow.
 */

/**
 * An absolute http(s) URL, or null.
 *
 * <p>A bare host is promoted rather than refused: `acme.com` is what people type, and as an `href` it
 * is a *relative* link that would navigate inside the SPA instead of to the company. Anything whose
 * scheme is not http(s) after that — `javascript:` being the one that matters — is null, because a
 * link the grid refuses to render beats one every call site has to remember to sanitise.
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
