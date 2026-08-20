/**
 * Turn a stored company link into an href, or `null` if it cannot safely be one.
 *
 * `app_lm_companies` is ETL-owned: the pipeline writes it and nothing guarantees the shape of a
 * `website` or `linkedin_url` value. They arrive both bare (`meridian.com`) and fully qualified, so a
 * scheme has to be added when it is missing — and precisely because a scheme can be *anything*, only
 * http and https are allowed through. A stored `javascript:…` rendered straight into an `href` would
 * be a click away from executing, which makes this a security boundary rather than formatting.
 */
export function externalUrl(value: string | null | undefined): string | null {
  if (!value) {
    return null;
  }
  const trimmed = value.trim();
  if (trimmed === "") {
    return null;
  }
  // A bare host has no scheme to judge, so give it the safe one rather than letting URL reject it.
  const candidate = /^[a-z][a-z0-9+.-]*:/i.test(trimmed) ? trimmed : `https://${trimmed}`;
  try {
    const url = new URL(candidate);
    return url.protocol === "http:" || url.protocol === "https:" ? url.href : null;
  } catch {
    return null;
  }
}

/** How a link reads when it has to be shown as text: no scheme, no `www.`, no trailing slash. */
export function linkLabel(value: string): string {
  return value
    .replace(/^[a-z][a-z0-9+.-]*:\/\//i, "")
    .replace(/^www\./i, "")
    .replace(/\/$/, "");
}
