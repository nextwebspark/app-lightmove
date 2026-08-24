/**
 * The registrable domain inside a URL — `https://www.alrawabidairy.ae/about` is `alrawabidairy.ae`.
 *
 * The same normalisation the API applies server-side (`WebsiteDomain`), and it has to agree with it:
 * this is the key a captured company is stored under, so a page that normalises one way here and
 * another way there becomes two rows for one company. Parsed with `URL` rather than pattern-matched,
 * for the reason the server's version gives — stripping the scheme by hand keeps the port and any
 * userinfo along with it.
 */
export function companyDomainName(url: string | null | undefined): string | null {
  if (!url || !url.trim()) {
    return null;
  }
  const trimmed = url.trim();
  const absolute = trimmed.includes("://") ? trimmed : `https://${trimmed}`;
  try {
    const host = new URL(absolute).hostname.toLowerCase().replace(/^www\./, "");
    return host.includes(".") ? host : null;
  } catch {
    return null;
  }
}

/**
 * True for the pages there is no point reading — a new tab, the browser's own settings, the Chrome
 * Web Store. Chrome refuses to inject into most of them anyway; checking first turns a console error
 * into a sentence the consultant can act on.
 */
export function isReadablePageUrl(url: string | null | undefined): boolean {
  if (!url) {
    return false;
  }
  return /^https?:\/\//i.test(url) && !/^https?:\/\/chrome\.google\.com\/webstore/i.test(url);
}
