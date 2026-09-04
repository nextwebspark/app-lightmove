/**
 * Everything the extension keeps in browser storage, and which of it belongs to a workspace.
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

/**
 * `chrome.storage.session`, not local: the last name read from a tab, kept only so the next read can
 * recognise it still being on screen after a pushState navigation. Nothing about a workspace, and
 * nothing worth persisting past the browser closing.
 */
export const LAST_READ_KEY = "lightmove.lastRead";

/**
 * `chrome.storage.session` again: the tab the consultant left to go and pair, so closing the connect
 * page can put them back on it rather than on whatever tab happened to sit next to it.
 */
export const CONNECT_RETURN_TAB_KEY = "lightmove.connectReturnTab";
