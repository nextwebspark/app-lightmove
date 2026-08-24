/**
 * Carries the paired session from the workspace's connect page into extension storage.
 *
 * Injected on exactly one URL — `<workspace>/extension/connect` — and it does one thing: listen for
 * the page to post a session, hand it to the service worker, and tell the page it landed.
 *
 * Two checks matter, and they are the whole security of this file. `event.source === window` rejects
 * anything posted from an iframe or another window, and `event.origin === location.origin` rejects
 * anything posted from a different origin. Without them any page that could get itself framed here
 * could hand the extension a session pointing at a server of its choosing.
 *
 * Deliberately dependency-free: a Manifest V3 content script is a classic script, so this is bundled
 * as an IIFE (see `vite.content.config.ts`) and the message names below are inlined rather than
 * imported. They are duplicated in `ExtensionConnectPage.tsx` on the web side, which is the one place
 * they have to agree.
 */

const SESSION_OFFERED = "lightmove.extension.session";
const SESSION_STORED = "lightmove.extension.paired";

window.addEventListener("message", (event: MessageEvent) => {
  if (event.source !== window || event.origin !== window.location.origin) {
    return;
  }
  const offered = event.data as { type?: unknown; session?: unknown } | null;
  if (!offered || offered.type !== SESSION_OFFERED || typeof offered.session !== "object") {
    return;
  }

  chrome.runtime.sendMessage({ kind: "storePairedSession", session: offered.session }, () => {
    // The page is waiting to say "connected" or "we could not reach the extension", so it is told
    // either way. chrome.runtime.lastError has to be read or Chrome logs an unchecked-error warning.
    const failed = chrome.runtime.lastError;
    window.postMessage({ type: SESSION_STORED, ok: !failed }, window.location.origin);
  });
});

// The page may have finished rendering before this script was injected, in which case its offer has
// already been posted and missed. Announcing presence lets it post again.
window.postMessage({ type: "lightmove.extension.ready" }, window.location.origin);
