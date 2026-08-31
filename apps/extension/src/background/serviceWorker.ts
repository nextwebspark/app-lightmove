import { ApiRequestError, createLightMoveApiClient } from "../api/lightMoveApiClient";
import { captureCandidate, removeCandidate } from "../api/captureCandidateApi";
import { captureCompany, removeTriageCompany } from "../api/captureCompanyApi";
import { listProjects } from "../api/projectsApi";
import { findTriageCompanyByName } from "../api/triageApi";
import type { ExtensionSession } from "../api/types";
import { workspaceOrigin } from "../workspaceOrigin";
import type { PageSubject } from "../content/pageReader/readPageSubject";
import { DEFAULT_CAPTURE_SETTINGS, type CaptureSettings } from "../domain/captureSettings";
import type { ExtensionFailure, ExtensionRequest, ReadPageResult } from "./extensionMessages";
import { isReadablePageUrl } from "./readablePageUrl";
import { LAST_PROJECT_KEY, SETTINGS_KEY } from "./storageKeys";
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
    .then(() => respond({ ok: true, value: null }))
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
    case "readActivePage":
      return readActivePage();
    case "listProjects":
      return listProjects(api);
    case "captureCompany":
      return captureCompany(api, message.projectId, message.capture);
    case "captureCandidate":
      return captureCandidate(api, message.projectId, message.candidate);
    case "findTriageCompany":
      return findTriageCompanyByName(api, message.projectId, message.companyName);
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

/** The IIFE bundle of the extractors; see `vite.page-reader.config.ts` for how it answers. */
const PAGE_READER_BUNDLE = "page-reader.js";

/**
 * Reads the tab the consultant invoked the extension on. Injected rather than declared, which is why
 * the extension holds no standing permission on any site, and injected as a `file` rather than a
 * `func`: a `func` is serialised with `toString` and closes over nothing.
 */
async function readActivePage(): Promise<ReadPageResult> {
  const [tab] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (!tab?.id || !isReadablePageUrl(tab.url)) {
    throw new PageNotReadableError();
  }

  const [injected] = await chrome.scripting.executeScript({
    target: { tabId: tab.id },
    files: [PAGE_READER_BUNDLE],
  });

  if (!injected?.result) {
    throw new PageNotReadableError();
  }
  return { ...(injected.result as PageSubject), sourceUrl: tab.url ?? "" };
}

class PageNotReadableError extends Error {
  constructor() {
    super("This page cannot be read. Open a company's website, or a LinkedIn company or profile page.");
    this.name = "PageNotReadableError";
  }
}

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
  if (error instanceof PageNotReadableError) {
    return { ok: false, code: "PAGE_NOT_READABLE", message: error.message };
  }
  return {
    ok: false,
    code: "UNEXPECTED_ERROR",
    message: error instanceof Error ? error.message : "Something went wrong.",
  };
}
