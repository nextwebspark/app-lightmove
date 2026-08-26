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
  return /^https?:\/\//i.test(url) && !UNINJECTABLE_HOSTS.test(url);
}

/**
 * Both Web Store hosts. The store moved to `chromewebstore.google.com` in 2023 and the old address
 * now redirects there, so matching only the legacy one meant the page a consultant actually visits
 * fell through to `executeScript`, which rejects — surfacing the raw rejection instead of the sentence
 * this function exists to produce.
 */
const UNINJECTABLE_HOSTS = /^https?:\/\/(chrome\.google\.com\/webstore|chromewebstore\.google\.com)/i;
