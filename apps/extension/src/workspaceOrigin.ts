/**
 * Where this build of the extension talks to.
 *
 * Substituted at bundle time by `vite.config.ts` from the same value it writes into the manifest, so
 * the host the code calls and the host the manifest permits cannot drift apart.
 */
export const workspaceOrigin = __WORKSPACE_ORIGIN__;
