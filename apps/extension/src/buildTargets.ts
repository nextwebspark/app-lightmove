/**
 * The two hosts the extension can be built against.
 *
 * Constants only, and that is structural: `manifest.config.ts` imports this while running in **Node**
 * at build time, where `import.meta.env` does not exist. Putting the runtime choice here breaks the
 * config load with "Cannot read properties of undefined (reading 'PROD')" — `workspaceOrigin.ts` makes
 * that choice instead, and is imported only by code that runs in the browser.
 *
 * The values live here once so that the manifest's `host_permissions` and the code's fetch target
 * cannot name different hosts — a mismatch surfaces as an opaque network failure with an empty console.
 */
export const PRODUCTION_WORKSPACE_ORIGIN = "https://app.lightmove.io";

/**
 * The Vite dev server, not the API on :8080 — it proxies `/api` to the API, so a development build
 * talks to exactly the origin the web app does.
 */
export const DEVELOPMENT_WORKSPACE_ORIGIN = "http://localhost:5173";
