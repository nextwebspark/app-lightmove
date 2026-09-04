/**
 * Where a build of the extension points. Fixed at build time from `LM_WORKSPACE_ORIGIN`, because the
 * manifest's `host_permissions` and `externally_connectable` name it and a permission cannot be
 * computed at runtime — and resolved here alone, so the manifest and the bundle cannot disagree.
 */
/**
 * The side panel's document, named by the manifest's `side_panel.default_path` *and* by every per-tab
 * `sidePanel.setOptions({ enabled: true })` the worker makes.
 *
 * Both, and that is the trap: enabling a tab without naming a path leaves it enabled and pointing at
 * nothing, so the toolbar icon lights up and clicking it opens no panel at all.
 */
export const SIDE_PANEL_PATH = "popup.html";

/**
 * The Vite dev server, not the API on :8080 — it proxies `/api` to the API, so a development build
 * talks to exactly the origin the web app does.
 */
export const DEVELOPMENT_WORKSPACE_ORIGIN = "http://localhost:5173";

/**
 * What a production build uses when nobody said where to point it — a recognisable placeholder, since
 * there is no deployed domain yet. A build carrying it is not shippable; CI warns rather than failing,
 * and `build:release` refuses outright.
 */
export const PLACEHOLDER_WORKSPACE_ORIGIN = "https://app.lightmove.io.example";

/**
 * Resolves the origin this build points at. Called from `vite.config.ts`, which then both writes it
 * into the manifest and defines it for the bundle — one decision, two consumers.
 */
export function resolveWorkspaceOrigin(mode: string, env: Record<string, string>): string {
  const configured = env.LM_WORKSPACE_ORIGIN?.trim();
  if (configured) {
    return requireOrigin(configured);
  }
  return mode === "production" ? PLACEHOLDER_WORKSPACE_ORIGIN : DEVELOPMENT_WORKSPACE_ORIGIN;
}

/**
 * An origin and nothing else. Thrown rather than trimmed: this goes into `host_permissions` and
 * `externally_connectable`, so a path makes a pattern Chrome rejects at load and a wildcard widens the
 * door the pairing handover comes through — both silently.
 */
function requireOrigin(value: string): string {
  const withoutTrailingSlash = value.replace(/\/+$/, "");
  let parsed: URL;
  try {
    parsed = new URL(withoutTrailingSlash);
  } catch {
    throw new Error(`LM_WORKSPACE_ORIGIN must be a URL, not "${value}".`);
  }
  if (parsed.protocol !== "https:" && parsed.protocol !== "http:") {
    throw new Error(`LM_WORKSPACE_ORIGIN must be http(s), not "${parsed.protocol}".`);
  }
  if (parsed.hostname.includes("*")) {
    throw new Error(`LM_WORKSPACE_ORIGIN must name one host; "${parsed.hostname}" is a wildcard.`);
  }
  if (withoutTrailingSlash !== parsed.origin) {
    throw new Error(
      `LM_WORKSPACE_ORIGIN must be an origin with no path, query or fragment. Use "${parsed.origin}".`,
    );
  }
  return parsed.origin;
}
