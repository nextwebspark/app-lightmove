/**
 * Whether a tab is one the page reader can be injected into.
 *
 * Chrome refuses to inject into its own pages and the Web Store, and does it with a console error the
 * consultant never sees. Checking first turns that into a sentence the popup can show.
 */
export function isReadablePageUrl(url: string | null | undefined): boolean {
  if (!url) {
    return false;
  }
  return /^https?:\/\//i.test(url) && !/^https?:\/\/chrome\.google\.com\/webstore/i.test(url);
}
