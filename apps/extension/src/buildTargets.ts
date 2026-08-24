/**
 * Where a build of the extension points, and how that is decided.
 *
 * The origin is **not** a runtime setting and cannot be: the manifest's `host_permissions` and
 * `externally_connectable` both name it, and a permission cannot be computed from something a user
 * types later. So it is fixed at build time, by `vite.config.ts`, from `LM_WORKSPACE_ORIGIN`.
 *
 * The values are resolved in exactly one place so that the manifest's permissions and the code's fetch
 * target cannot name different hosts — a mismatch surfaces as an opaque network failure with an empty
 * console, which is a miserable thing to debug.
 */

/**
 * The Vite dev server, not the API on :8080 — it proxies `/api` to the API, so a development build
 * talks to exactly the origin the web app does.
 */
export const DEVELOPMENT_WORKSPACE_ORIGIN = "http://localhost:5173";

/**
 * What a production build uses when nobody said where to point it.
 *
 * A placeholder, and deliberately a recognisable one: there is no deployed LightMove domain yet. A
 * build that falls back to this is **not shippable** — the extension would ask permission for a host
 * nobody owns, and every request would fail. `vite.config.ts` prints a loud warning rather than
 * failing, so CI can still typecheck and bundle; shipping it is the thing to prevent, not building it.
 *
 * Set `LM_WORKSPACE_ORIGIN` to the real origin — a custom domain, or the Cloud Run URL the deploy
 * prints, which is a perfectly good origin to ship against in the meantime.
 */
export const PLACEHOLDER_WORKSPACE_ORIGIN = "https://app.lightmove.io.example";

/**
 * Resolves the origin this build points at. Called from `vite.config.ts`, which then both writes it
 * into the manifest and defines it for the bundle — one decision, two consumers.
 */
export function resolveWorkspaceOrigin(mode: string, env: Record<string, string>): string {
  const configured = env.LM_WORKSPACE_ORIGIN?.trim();
  if (configured) {
    return configured.replace(/\/+$/, "");
  }
  return mode === "production" ? PLACEHOLDER_WORKSPACE_ORIGIN : DEVELOPMENT_WORKSPACE_ORIGIN;
}
