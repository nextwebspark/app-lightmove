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

/**
 * What page this is, canonically: `person:amira-haddad`, `company:acme` — subpaths, locale prefixes and
 * tracking parameters gone. This is the *identity* of a page, and the reason a capture is not reseeded
 * when `?trk=` appears or the consultant opens `/details/experience` on the person already on screen.
 */
export function pageKeyOf(url: string | null | undefined): string | null {
  const profile = profileSlugOf(url ?? undefined);
  if (profile) {
    return `person:${profile}`;
  }
  const company = companySlugOf(url ?? undefined);
  return company ? `company:${company}` : null;
}

/**
 * The identity of whatever tab the panel is looking at, readable or not.
 *
 * Total where `pageKeyOf` is nullable, because the panel keys its read on this and a tab it cannot
 * capture from still has to be *a* page: the two refusals differ (off LinkedIn entirely, versus a
 * LinkedIn page that names nobody), so they cannot collapse into one key or the panel would keep
 * showing the first refusal after moving between them.
 */
export function tabPageKeyOf(url: string | null | undefined): string {
  return pageKeyOf(url) ?? (isLinkedInPageUrl(url) ? "linkedin:unreadable" : "offsite");
}
