import packageJson from "./package.json" with { type: "json" };

/**
 * The single source of the extension's manifest, emitted by the build so the entry points it names and
 * the files the bundler produces cannot drift — a manifest pointing at a moved script loads and
 * silently does nothing. Never edit `dist/manifest.json`.
 */

/**
 * The public half of a pinned keypair, which fixes the id **for unpacked loading only**:
 * `kllpamcdcnecpdblgdkehgbhdjdlbofh`. The Web Store assigns its own, which is why the API's CORS entry
 * and the connect page take the id as configuration. See the README.
 */
const PINNED_PUBLIC_KEY =
  "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAzEytfwFPI7Za1EUBOOPBAEpoHrYmNyz8P/i97riqVWaylCeZ" +
  "+E3A0/NL3d0W8qVoohO9RCu+jxDsXiMwjil8VAbTet3RjiAOUlxI5JknXLStIFpC98zX8QsPFzyOkJ1znVSziRgvNbVkGyEJ" +
  "qINRgjYdadm/KQTYVXPEcHk7GYSLQo2oUWyVAZnlNfVwfZpleEVPowk80r0JgrnAqqKiBL8QK2AtqN0WeH83A5ONTBwQu8jP" +
  "ABzm6+dRORxt2peYqyEYFCcXzzM/Pa3UuwQmRs0WCNhNeS8fJTyTjID7GN3pnM9S2+gsADtjtB9GlGYENPFdKy28OHvKWJ3A" +
  "ba4djQIDAQAB";

/**
 * The manifest for one build. Takes the origin rather than deciding it, so the manifest's permissions
 * and the bundle's fetch target cannot name different hosts.
 */
/** The one version there is: the manifest and the package must not drift. */
const { version: packageVersion } = packageJson as { version: string };

export function buildManifest(workspaceOrigin: string) {
  return {
    manifest_version: 3,
    name: "LightMove Capture",
    version: packageVersion,
    description: "Capture a company or an executive from the page you are on into a LightMove mandate.",
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
    // This was a window.postMessage, which is delivered to every listener in the frame — including
    // any other installed extension's content script, which would have read the refresh token.
    externally_connectable: {
      matches: [`${workspaceOrigin}/*`],
    },

    commands: {
      _execute_action: {
        suggested_key: { default: "Alt+Shift+L" },
        description: "Open LightMove Capture",
      },
    },
  } as const;
}
