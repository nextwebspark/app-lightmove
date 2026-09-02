/**
 * LinkedIn URLs, parsed and built in one place. The slug out of the address bar is the one thing a
 * LinkedIn page cannot lie about, so the captured `linkedinUrl` is always built from it — never read
 * off the page, and never dependent on the DOM read succeeding.
 */

/** Whether a URL is an http(s) linkedin.com page at all — the only site this plugin reads. */
export function isLinkedInPageUrl(url: string | null | undefined): boolean {
  if (!url) {
    return false;
  }
  try {
    const parsed = new URL(url);
    return /^https?:$/.test(parsed.protocol) && /(^|\.)linkedin\.com$/i.test(parsed.hostname);
  } catch {
    return false;
  }
}

/** The `/in/<slug>` a LinkedIn profile URL names, or null when the URL is not one. */
export function profileSlugOf(url: string | undefined): string | null {
  return slugOf(url, /^\/in\/([^/]+)\/?/);
}

/** The `/company/<slug>` a LinkedIn company URL names, or null when the URL is not one. */
export function companySlugOf(url: string | undefined): string | null {
  return slugOf(url, /^\/company\/([^/]+)\/?/);
}

/** The canonical profile URL for a slug — query strings, locale prefixes and tracking dropped. */
export function linkedInProfileUrlOf(slug: string): string {
  return `https://www.linkedin.com/in/${encodeURIComponent(slug)}/`;
}

/** The canonical company-page URL for a slug. */
export function linkedInCompanyUrlOf(slug: string): string {
  return `https://www.linkedin.com/company/${encodeURIComponent(slug)}/`;
}

function slugOf(url: string | undefined, pattern: RegExp): string | null {
  if (!url) {
    return null;
  }
  try {
    const parsed = new URL(url);
    if (!/(^|\.)linkedin\.com$/i.test(parsed.hostname)) {
      return null;
    }
    const match = parsed.pathname.match(pattern);
    return match ? decodeURIComponent(match[1]) : null;
  } catch {
    return null;
  }
}
