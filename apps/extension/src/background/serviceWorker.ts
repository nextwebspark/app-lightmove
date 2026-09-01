import { ApiRequestError, createLightMoveApiClient } from "../api/lightMoveApiClient";
import { captureCandidate, removeCandidate } from "../api/captureCandidateApi";
import { captureCompany, removeTriageCompany } from "../api/captureCompanyApi";
import { listProjects } from "../api/projectsApi";
import type { ExtensionSession } from "../api/types";
import { workspaceOrigin } from "../workspaceOrigin";
import type { PageSubject } from "../content/pageReader/readPageSubject";
import { mergeExtractedPerson } from "../content/pageReader/extractedPerson";
import { mergeExtracted } from "../content/pageReader/extractedCompany";
import {
  companySlugOf,
  isLinkedInPageUrl,
  linkedInCompanyUrlOf,
  linkedInProfileUrlOf,
  profileSlugOf,
} from "../content/pageReader/linkedInUrls";
import { DEFAULT_CAPTURE_SETTINGS, type CaptureSettings } from "../domain/captureSettings";
import type { ExtensionFailure, ExtensionRequest, ReadPageResult } from "./extensionMessages";
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

// The toolbar icon opens the side panel. Top level like every other registration here, and tolerant
// of a Chrome too old to have the API rather than failing the whole worker on startup.
chrome.sidePanel?.setPanelBehavior({ openPanelOnActionClick: true }).catch(() => undefined);

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
        void chrome.tabs.remove(sender.tab.id).catch(() => undefined);
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
    case "readActivePage":
      return readActivePage();
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

/** The IIFE bundle of the extractors; see `vite.page-reader.config.ts` for how it answers. */
const PAGE_READER_BUNDLE = "page-reader.js";

/**
 * Reads the tab the consultant invoked the extension on: one injection, one pass, no scrolling.
 *
 * LinkedIn only. The URL decides the subject and supplies the one field that must never be missing —
 * the canonical profile or company URL, built from the address bar's slug — so even a page whose DOM
 * yields nothing still captures with its URL, and the consultant types the name. Anything richer
 * than name + URL is enrichment, done server-side later, not read off the page.
 */
async function readActivePage(): Promise<ReadPageResult> {
  const tab = await tabToRead();
  if (!tab?.id) {
    throw new LinkedInOnlyError();
  }
  const sourceUrl = tab.url ?? "";

  const profileSlug = profileSlugOf(tab.url);
  if (profileSlug) {
    const read = await readPage(tab.id);
    // The slug-built URL first, so it wins the merge: the address bar cannot lie, and it is present
    // even when the read came back empty.
    const person = mergeExtractedPerson([
      { linkedinUrl: linkedInProfileUrlOf(profileSlug) },
      read?.person ?? {},
    ]);
    return { subject: "person", person, company: mergeExtracted([]), sourceUrl };
  }

  const companySlug = companySlugOf(tab.url);
  if (companySlug) {
    const read = await readPage(tab.id);
    const company = mergeExtracted([
      { linkedinUrl: linkedInCompanyUrlOf(companySlug) },
      read?.company ?? {},
    ]);
    return { subject: "company", person: mergeExtractedPerson([]), company, sourceUrl };
  }

  // LinkedIn, but not a page that names a person or a company — the feed, search, jobs.
  throw new PageNotReadableError();
}

/**
 * The tab the panel is looking at.
 *
 * `currentWindow` is the last *focused* window, which is not always a browser window: with DevTools
 * detached and focused it resolves to something with no readable tab, and the read would be refused
 * for a page sitting right there. Falling back to the most recently touched LinkedIn tab answers
 * for the tab beside it — and null means the consultant has no LinkedIn page open anywhere, which
 * is the LinkedIn-only message's case, not an error.
 */
async function tabToRead(): Promise<chrome.tabs.Tab | null> {
  const [inCurrent] = await chrome.tabs.query({ active: true, currentWindow: true });
  if (inCurrent?.id && isCapturableTab(inCurrent)) {
    return inCurrent;
  }
  const all = await chrome.tabs.query({});
  return (
    all
      .filter(isCapturableTab)
      .sort((a, b) => (b.lastAccessed ?? 0) - (a.lastAccessed ?? 0))[0] ?? null
  );
}

/** A tab worth reading: LinkedIn, the one site this plugin captures from. */
function isCapturableTab(tab: chrome.tabs.Tab): boolean {
  return Boolean(tab.id) && isLinkedInPageUrl(tab.url);
}

/**
 * One injection of the page reader; the page's answer, or null when it had none. Only ever called
 * for a LinkedIn tab, which the manifest's standing host permission covers — so a failure here is
 * a real one (the tab closed mid-read), never a missing grant.
 */
async function readPage(tabId: number): Promise<PageSubject | null> {
  const [injected] = await chrome.scripting.executeScript({ target: { tabId }, files: [PAGE_READER_BUNDLE] });
  return (injected?.result as PageSubject | undefined) ?? null;
}

/** Not on LinkedIn at all — answered with the pointer to the app, not with an apology. */
class LinkedInOnlyError extends Error {
  constructor() {
    super(
      "LightMove Capture reads LinkedIn only, for now. To add a person or a company by hand, open LightMove.",
    );
    this.name = "LinkedInOnlyError";
  }
}

class PageNotReadableError extends Error {
  constructor() {
    super("Open a LinkedIn profile or company page to capture from it.");
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
