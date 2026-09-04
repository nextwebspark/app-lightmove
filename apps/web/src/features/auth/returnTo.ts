/**
 * Where sign-in should land, when the user was already headed somewhere.
 *
 * The extension is what made this load-bearing: its "Open LightMove" button opens `/extension/connect`,
 * and a consultant who is signed out lands on the login page, signs in, arrives at the projects list —
 * and the extension is still not paired, with the tab that would have paired it gone.
 */
const RETURN_TO_KEY = "lightmove.returnTo";

/**
 * The path, or nothing.
 *
 * Only ever an absolute in-app path. `//evil.example` and `/\evil.example` are both read by browsers as
 * protocol-relative URLs, so a value arriving from a route's state or from storage is a redirect
 * off-site unless this refuses it.
 */
export function safeReturnTo(value: unknown): string | null {
  if (typeof value !== "string" || !value.startsWith("/")) {
    return null;
  }
  return value[1] === "/" || value[1] === "\\" ? null : value;
}

/**
 * Holds the destination across an OAuth round trip, which leaves the SPA entirely and so cannot carry
 * router state. Written on every visit to the login page — clearing it when there is nowhere to return
 * to, so a destination from an abandoned attempt cannot redirect a later sign-in.
 */
export function rememberReturnTo(path: string | null): void {
  try {
    if (path) {
      sessionStorage.setItem(RETURN_TO_KEY, path);
    } else {
      sessionStorage.removeItem(RETURN_TO_KEY);
    }
  } catch {
    // Storage refused (private mode, blocked site data). Sign-in still works; it just lands home.
  }
}

/** The remembered destination, consumed — it is for one sign-in and must not outlive it. */
export function takeReturnTo(): string | null {
  try {
    const stored = sessionStorage.getItem(RETURN_TO_KEY);
    sessionStorage.removeItem(RETURN_TO_KEY);
    return safeReturnTo(stored);
  } catch {
    return null;
  }
}
