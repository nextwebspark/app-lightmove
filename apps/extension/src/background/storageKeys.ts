/**
 * Everything the extension keeps in `chrome.storage.local`, and which of it belongs to a workspace.
 *
 * Signing out clears the workspace-scoped keys as well as the credential. They are mandate ids, not
 * preferences: left behind, the next consultant to pair on a shared laptop opens the popup holding the
 * previous one's mandate — and `useProjectSelection` would file their first capture into it.
 */
export const SESSION_KEY = "lightmove.session";
export const LAST_PROJECT_KEY = "lightmove.lastProjectId";
export const SETTINGS_KEY = "lightmove.settings";

/** Cleared on sign-out, because none of it means anything to a different account. */
export const WORKSPACE_SCOPED_KEYS = [SESSION_KEY, LAST_PROJECT_KEY, SETTINGS_KEY];
