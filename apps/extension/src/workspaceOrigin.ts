/**
 * Where this build of the extension talks to.
 *
 * Substituted at bundle time by `vite.config.ts` from the same value it writes into the manifest, so
 * the host the code calls and the host the manifest permits cannot drift apart.
 */
export const workspaceOrigin = __WORKSPACE_ORIGIN__;

/**
 * The pairing page. Named once because two contexts reach for it: the worker opens it, and the
 * signed-out panel prints it so a consultant can see where the button is about to send them.
 */
export const extensionConnectUrl = `${workspaceOrigin}/extension/connect`;
