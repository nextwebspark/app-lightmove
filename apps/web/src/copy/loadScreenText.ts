import { applyBundle, rememberedBundle, type ScreenTextBundle } from "./store";

const LOCALE = "en";

/**
 * Started before the first render, beside the session restore rather than after it. Plain `fetch`, not `apiClient`:
 * the read is public, and the browser revalidates it by ETag on its own, so an unchanged bundle is a 304.
 */
export function loadScreenText(): Promise<void> {
  const remembered = rememberedBundle();
  if (remembered) applyBundle(remembered);
  return fetch(`/api/v1/screen-text?locale=${LOCALE}`, { headers: { Accept: "application/json" } })
    .then((response) => (response.ok ? (response.json() as Promise<ScreenTextBundle>) : null))
    .then((bundle) => {
      if (bundle && bundle.version !== remembered?.version) applyBundle(bundle);
    })
    .catch(() => undefined);
}
