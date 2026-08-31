import packageJson from "./package.json" with { type: "json" };

/**
 * The single source of the extension's manifest.
 *
 * Written here and emitted by the build rather than hand-maintained as JSON, so the entry points it
 * names and the files the bundler actually produces cannot drift apart — a manifest pointing at a
 * script that moved is an extension that loads and silently does nothing. The paths below are the
 * bundle's own output names, pinned in `vite.config.ts`; never edit `dist/manifest.json` directly.
 */

/**
 * The public half of a pinned keypair, which is what makes the extension's id stable **when loaded
 * unpacked**: `kllpamcdcnecpdblgdkehgbhdjdlbofh`. Without it Chrome mints a fresh id on every load, so
 * no `chrome-extension://` origin could be allow-listed for CORS and nothing would work locally.
 *
 * <b>It does not decide the published id.</b> The Chrome Web Store assigns its own when the item is
 * first created, so a published build has a different id from this one — which is why the API's CORS
 * entry and the connect page both take it as configuration rather than a literal. See the README.
 *
 * Public by nature. The private half signs a self-hosted `.crx` and is not in this repo; neither
 * loading unpacked nor Web Store publishing needs it.
 */
const PINNED_PUBLIC_KEY =
  "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzEytfwFPI7Za1EUBOOPBAEpoHrYmNyz8P/i97riqVWaylCeZ" +
  "+E3A0/NL3d0W8qVoohO9RCu+jxDsXiMwjil8VAbTet3RjiAOUlxI5JknXLStIFpC98zX8QsPFzyOkJ1znVSziRgvNbVkGyEJ" +
  "qINRgjYdadm/KQTYVXPEcHk7GYSLQo2oUWyVAZnlNfVwfZpleEVPowk80r0JgrnAqqKiBL8QK2AtqN0WeH83A5ONTBwQu8jP" +
  "ABzm6+dRORxt2peYqyEYFCcXzzM/Pa3UuwQmRs0WCNhNeS8fJTyTjID7GN3pnM9S2+gsADtjtB9GlGYENPFdKy28OHvKWJ3A" +
  "ba4djQIDAQAB";

/**
 * The manifest for one build.
 *
 * Takes the origin rather than deciding it: `host_permissions` and `externally_connectable` both name
 * it, and `vite.config.ts` resolves it once and hands the same value to both the manifest and the
 * bundle. Guessing here — from `process.env.NODE_ENV`, say, which Vite sets differently depending on
 * how the build was invoked — is how the two end up naming different hosts.
 */
/** The one version there is: the manifest and the package must not drift. */
const { version: packageVersion } = packageJson as { version: string };

export function buildManifest(workspaceOrigin: string) {
  return {
    manifest_version: 3,
    name: "LightMove Capture",
    version: packageVersion,
    description: "Capture a company from the page you are on into a LightMove mandate.",
    key: PINNED_PUBLIC_KEY,

    // Every permission below is here for one named feature. An unexplained permission is a review
    // failure and a Web Store rejection.
    permissions: [
      // The paired session token and the last-used project. Nothing else is stored.
      "storage",
      // Read the page the consultant is looking at — but only the tab they invoked the extension on,
      // and only for as long as that grant lasts. See the note on content_scripts below.
      "activeTab",
      // Injects the page reader into that tab on demand. The counterpart to activeTab.
      "scripting",
    ],

    // The workspace only. The extension has no standing permission on any other site: it reads a page
    // through activeTab, which Chrome grants for the current tab when the user clicks the toolbar icon
    // or presses the shortcut, and revokes when they navigate away. A <all_urls> content script would
    // have been less code and a standing licence to read every page the consultant ever opens.
    host_permissions: [`${workspaceOrigin}/*`],

    action: {
      default_popup: "popup.html",
      default_title: "LightMove Capture",
      default_icon: {
        16: "icons/icon-16.png",
        32: "icons/icon-32.png",
        48: "icons/icon-48.png",
        128: "icons/icon-128.png",
      },
    },

    icons: {
      16: "icons/icon-16.png",
      32: "icons/icon-32.png",
      48: "icons/icon-48.png",
      128: "icons/icon-128.png",
    },

    background: {
      service_worker: "background.js",
      type: "module",
    },

    // How the workspace hands the paired session over, and the only channel that keeps it private.
    //
    // This was a content script and a window.postMessage, which was wrong: postMessage to the page's
    // own window is delivered to every listener in that frame, and a content script's isolated world
    // does not isolate it from those events. Any other extension the consultant had installed with a
    // broad content script — a grammar checker, a coupon finder — would have received the refresh
    // token. externally_connectable delivers to this extension and to nothing else.
    //
    // The id is not a secret and pinning it costs nothing: PINNED_PUBLIC_KEY above fixes it, and
    // application.yml already names the same id in the CORS allow-list.
    externally_connectable: {
      matches: [`${workspaceOrigin}/*`],
    },

    commands: {
      _execute_action: {
        suggested_key: { default: "Alt+Shift+L", mac: "Alt+Shift+L" },
        description: "Open LightMove Capture",
      },
    },
  } as const;
}
