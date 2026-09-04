import { ApiRequestError, createLightMoveApiClient } from "../api/lightMoveApiClient";
import { captureCandidate, removeCandidate } from "../api/captureCandidateApi";
import { captureCompany, removeTriageCompany } from "../api/captureCompanyApi";
import { listProjects } from "../api/projectsApi";
import type { ExtensionSession } from "../api/types";
import { extensionConnectUrl, workspaceOrigin } from "../workspaceOrigin";
import type { PageSubject } from "../content/pageReader/readPageSubject";
import {
  activePageKey,
  readActivePage,
  LinkedInOnlyError,
  PageNotReadableError,
  type ActivePageDeps,
  type StoredLastRead,
} from "./activePage";
import {
  applyPanelAvailability,
  applyPanelAvailabilityToAllTabs,
  disableGlobalPanel,
  type PanelAvailabilityDeps,
} from "./panelAvailability";
import { DEFAULT_CAPTURE_SETTINGS, type CaptureSettings } from "../domain/captureSettings";
import type { ExtensionFailure, ExtensionRequest } from "./extensionMessages";
import { CONNECT_RETURN_TAB_KEY, LAST_PROJECT_KEY, LAST_READ_KEY, SETTINGS_KEY } from "./storageKeys";
import {
  currentAccessToken,
  pairedUser,
  renewAccessToken,
  signOut,
  storePairedSession,
} from "./extensionSessionStore";

/**
 * The extension's only long-lived context, and the only one that holds the session.
 *
 * <b>Every listener is registered at the top level, and that is a trap rather than a style.</b> Chrome
 * runs this file to completion and *then* dispatches the message that woke the worker, so a listener
 * registered inside a callback does not exist yet, the message is dropped, and the popup hangs.
 */

const api = createLightMoveApiClient({
  baseOrigin: workspaceOrigin,
  currentAccessToken,
  renewAccessToken,
});


/** What the workspace's connect page sends over externally_connectable. */
type WorkspaceMessage =
  | { kind: "ping" }
  | { kind: "storePairedSession"; session: ExtensionSession };

// The toolbar icon opens the side panel. Top level like every other registration here, and tolerant
// of a Chrome too old to have the API rather than failing the whole worker on startup.
chrome.sidePanel?.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => undefined);

/** Where the availability rule meets the browser. */
const panelAvailabilityDeps: PanelAvailabilityDeps = {
  queryTabs: (query) => chrome.tabs.query(query),
  setPanelOptions: (options) => chrome.sidePanel.setOptions(options),
  enableAction: (tabId) => chrome.action.enable(tabId),
  disableAction: (tabId) => chrome.action.disable(tabId),
  setActionTitle: (details) => chrome.action.setTitle(details),
};

// Judged on every update rather than only when the address changed: the call is cheap and idempotent,
// and a tab's first update often carries its URL on the tab and not on the change.
chrome.tabs.onUpdated.addListener((_tabId, _change, tab) => {
  void applyPanelAvailability(panelAvailabilityDeps, tab);
});

// Tabs that were already open have never fired an update, so without these two they keep the
// manifest's enabled-everywhere default and the panel shows on a page it has no business on.
chrome.runtime.onInstalled.addListener(() => {
  void applyPanelAvailabilityToAllTabs(panelAvailabilityDeps);
});

// A tab the worker has never judged has no panel path and no global default to fall back on, so its
// toolbar click would open nothing. Activation is the last moment to catch one — a tab that existed
// before this extension did, or one a sweep raced.
chrome.tabs.onActivated.addListener(({ tabId }) => {
  void chrome.tabs
    .get(tabId)
    .then((tab) => applyPanelAvailability(panelAvailabilityDeps, tab))
    .catch(() => undefined);
});

void disableGlobalPanel(panelAvailabilityDeps).then(() =>
  applyPanelAvailabilityToAllTabs(panelAvailabilityDeps),
);

chrome.runtime.onMessage.addListener((message: ExtensionRequest, _sender, respond) => {
  // Answering asynchronously requires returning true synchronously, so the work is started here and
  // the channel held open. Returning the promise itself does not work in Chrome.
  handle(message)
    .then((value) => respond({ ok: true, value }))
    .catch((error) => respond(toFailure(error)));
  return true;
});

/**
 * What arrives from a web page rather than from inside the extension. A separate listener because the
 * trust is different: the sender's origin is checked before a credential is stored, which is the lock
 * that still holds if the manifest's match pattern is ever widened.
 */
chrome.runtime.onMessageExternal.addListener((message: WorkspaceMessage, sender, respond) => {
  if (!isWorkspaceSender(sender)) {
    respond({ ok: false, code: "SENDER_REFUSED", message: "Not the LightMove workspace." });
    return false;
  }
  // Answered before a token exists, so the page can find out whether this extension is installed
  // rather than minting a credential and hoping something collects it. It carries nothing.
  if (message?.kind === "ping") {
    respond({ ok: true, value: null });
    return false;
  }
  if (message?.kind !== "storePairedSession") {
    respond({ ok: false, code: "SENDER_REFUSED", message: "Not a message this extension answers." });
    return false;
  }
  storePairedSession(message.session)
    .then(() => {
      respond({ ok: true, value: null });
      // The connect page has one job and has done it. Closing it puts the consultant back where they
      // were, which is what makes pairing feel like a step rather than a detour.
      if (sender.tab?.id) {
        void closeConnectPage(sender.tab.id);
      }
    })
    .catch((error) => respond(toFailure(error)));
  return true;
});

/** The sender's own origin, which Chrome sets and a page cannot forge. */
function isWorkspaceSender(sender: chrome.runtime.MessageSender): boolean {
  return sender.origin === workspaceOrigin;
}

async function handle(message: ExtensionRequest): Promise<unknown> {
  switch (message.kind) {
    case "getPairedUser":
      return pairedUser();
    case "signOut":
      await signOut();
      return null;
    case "openConnectPage":
      return openConnectPage();
    case "readActivePage":
      return readActivePage(activePageDeps);
    case "activePageKey":
      return activePageKey(activePageDeps);
    case "listProjects":
      return listProjects(api);
    case "captureCompany":
      return captureCompany(api, message.projectId, message.capture);
    case "captureCandidate":
      return captureCandidate(api, message.projectId, message.candidate);
    case "rememberProject":
      await chrome.storage.local.set({ [LAST_PROJECT_KEY]: message.projectId });
      return null;
    case "lastUsedProject":
      return (await chrome.storage.local.get(LAST_PROJECT_KEY))[LAST_PROJECT_KEY] ?? null;
    case "removeTriageCompany":
      await removeTriageCompany(api, message.projectId, message.triageCompanyId);
      return null;
    case "removeCandidate":
      await removeCandidate(api, message.projectId, message.candidateId);
      return null;
    case "readSettings":
      return storedSettings();
    case "writeSettings":
      return writeSettings(message.settings);
    default:
      // Reachable in one real state: an extension update restarts the worker while an open popup keeps
      // running against the older contract. Without this the switch falls through to `undefined` and
      // the caller reads `{ ok: true, value: undefined }` — a successful empty reply, rendering a blank
      // project list rather than reporting anything.
      throw new Error(`This version of LightMove Capture cannot handle "${(message as { kind: string }).kind}".`);
  }
}

/**
 * The popup's own preferences, defaulted on read rather than on install — an extension updated from a
 * version that did not have a setting must not be left with it undefined.
 */
async function storedSettings(): Promise<CaptureSettings> {
  const stored = (await chrome.storage.local.get(SETTINGS_KEY))[SETTINGS_KEY] as Partial<CaptureSettings> | undefined;
  return { ...DEFAULT_CAPTURE_SETTINGS, ...stored };
}

async function writeSettings(changed: Partial<CaptureSettings>): Promise<CaptureSettings> {
  const merged = { ...(await storedSettings()), ...changed };
  await chrome.storage.local.set({ [SETTINGS_KEY]: merged });
  return merged;
}

/**
 * Opens the workspace's pairing page, remembering the tab it was opened from.
 *
 * Opened here rather than from the panel so there is something to come back to: a tab created with no
 * opener leaves Chrome to pick whoever sits next to it when the connect page closes, which is how
 * pairing from LinkedIn ended up on an unrelated tab.
 */
async function openConnectPage(): Promise<null> {
  const [from] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (from?.id) {
    await chrome.storage.session.set({ [CONNECT_RETURN_TAB_KEY]: from.id });
  }
  await chrome.tabs.create({ url: extensionConnectUrl, openerTabId: from?.id });
  return null;
}

/** Closes the pairing page and puts the consultant back on the page they were reading. */
async function closeConnectPage(connectTabId: number): Promise<void> {
  const returnTo = (await chrome.storage.session.get(CONNECT_RETURN_TAB_KEY))[CONNECT_RETURN_TAB_KEY] as
    | number
    | undefined;
  await chrome.storage.session.remove(CONNECT_RETURN_TAB_KEY);
  await chrome.tabs.remove(connectTabId).catch(() => undefined);
  // `openerTabId` alone is not enough — Chrome only honours it in some close paths — so the tab is
  // named outright. It may have been closed while the consultant was pairing.
  if (returnTo !== undefined) {
    await chrome.tabs.update(returnTo, { active: true }).catch(() => undefined);
  }
}

/** The IIFE bundle of the extractors; see `vite.page-reader.config.ts` for how it answers. */
const PAGE_READER_BUNDLE = "page-reader.js";

/**
 * Where the reading of a page meets the browser. Everything above the `chrome.*` calls lives in
 * `activePage.ts`, which is testable because it does not.
 */
const activePageDeps: ActivePageDeps = {
  queryTabs: (query) => chrome.tabs.query(query),
  injectReader: async (tabId) => {
    // Only ever called for a LinkedIn tab, which the manifest's standing host permission covers — so a
    // failure here is a real one (the tab closed mid-read), never a missing grant.
    const [injected] = await chrome.scripting.executeScript({
      target: { tabId },
      files: [PAGE_READER_BUNDLE],
    });
    return (injected?.result as PageSubject | undefined) ?? null;
  },
  // `session`, not `local`: this is a name already on screen, worth nothing once the browser closes,
  // and it must survive the worker being recycled mid-navigation — which `local` would over-persist
  // and a module variable would not survive at all.
  lastRead: {
    get: async () =>
      ((await chrome.storage.session.get(LAST_READ_KEY))[LAST_READ_KEY] as StoredLastRead | undefined) ?? null,
    set: async (value) => chrome.storage.session.set({ [LAST_READ_KEY]: value }),
  },
  delay: (ms) => new Promise((resolve) => setTimeout(resolve, ms)),
};

/**
 * Failures cross the message boundary as data, never as a rejection.
 *
 * A rejected `sendMessage` reaches the popup as "Could not establish connection", with the real cause
 * discarded — so the popup would have nothing to show but a generic apology for a perfectly
 * explicable refusal like an off-limits company.
 */
function toFailure(error: unknown): ExtensionFailure {
  if (error instanceof ApiRequestError) {
    return { ok: false, code: error.code, message: error.problem.detail };
  }
  if (error instanceof LinkedInOnlyError) {
    return { ok: false, code: "LINKEDIN_ONLY", message: error.message };
  }
  if (error instanceof PageNotReadableError) {
    return { ok: false, code: "PAGE_NOT_READABLE", message: error.message };
  }
  return {
    ok: false,
    code: "UNEXPECTED_ERROR",
    message: error instanceof Error ? error.message : "Something went wrong.",
  };
}
