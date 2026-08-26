import { ApiRequestError, createLightMoveApiClient } from "../api/lightMoveApiClient";
import { captureCompany } from "../api/captureCompanyApi";
import { listProjects } from "../api/projectsApi";
import type { ExtensionSession } from "../api/types";
import { workspaceOrigin } from "../workspaceOrigin";
import type { ExtensionFailure, ExtensionRequest, ReadPageResult } from "./extensionMessages";
import { isReadablePageUrl } from "./readablePageUrl";
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
 * Everything the popup needs happens here: the network calls, reading the active tab, and the small
 * amount of remembered state. The popup asks over `chrome.runtime.sendMessage` and renders the answer.
 *
 * <b>Every listener is registered at the top level of this module, and that is not style.</b> An MV3
 * service worker is killed between events and restarted by the next one — Chrome runs this file to
 * completion and *then* dispatches the message that woke it. A listener registered inside a promise
 * callback does not exist yet at that moment, the message is dropped, and the popup hangs with no
 * error anywhere. Nothing below may move inside an async boundary.
 */

const api = createLightMoveApiClient({
  baseOrigin: workspaceOrigin,
  currentAccessToken,
  renewAccessToken,
});

const LAST_PROJECT_KEY = "lightmove.lastProjectId";

/** What the workspace's connect page sends over externally_connectable. */
interface StorePairedSessionMessage {
  kind: "storePairedSession";
  session: ExtensionSession;
}

chrome.runtime.onMessage.addListener((message: ExtensionRequest, _sender, respond) => {
  // Answering asynchronously requires returning true synchronously, so the work is started here and
  // the channel held open. Returning the promise itself does not work in Chrome.
  handle(message)
    .then((value) => respond({ ok: true, value }))
    .catch((error) => respond(toFailure(error)));
  return true;
});

/**
 * Pairing, and only pairing, arrives here — from a web page rather than from inside the extension.
 *
 * A separate listener from the one above, because the trust is different. `onMessage` can only be
 * reached by this extension's own popup; `onMessageExternal` is reachable by any page the manifest's
 * `externally_connectable` names, so the sender is checked before a credential is stored. The manifest
 * already narrows it to the workspace origin — this is the second lock on the same door, and it is the
 * one that still holds if that match pattern is ever widened.
 */
chrome.runtime.onMessageExternal.addListener((message: StorePairedSessionMessage, sender, respond) => {
  if (message?.kind !== "storePairedSession" || !isWorkspaceSender(sender)) {
    respond({ ok: false, code: "SENDER_REFUSED", message: "Not the LightMove workspace." });
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
    case "readActiveTabCompany":
      return readActiveTabCompany();
    case "listProjects":
      return listProjects(api);
    case "captureCompany":
      return captureCompany(api, message.projectId, message.capture);
    case "rememberProject":
      await chrome.storage.local.set({ [LAST_PROJECT_KEY]: message.projectId });
      return null;
    case "lastUsedProject":
      return (await chrome.storage.local.get(LAST_PROJECT_KEY))[LAST_PROJECT_KEY] ?? null;
    default:
      // Reachable in one real state: an extension update restarts the worker while an open popup keeps
      // running against the older contract. Without this the switch falls through to `undefined` and
      // the caller reads `{ ok: true, value: undefined }` — a successful empty reply, rendering a blank
      // project list rather than reporting anything.
      throw new Error(`This version of LightMove Capture cannot handle "${(message as { kind: string }).kind}".`);
  }
}

/** The IIFE bundle of the extractors; see `vite.page-reader.config.ts` for how it answers. */
const PAGE_READER_BUNDLE = "page-reader.js";

/**
 * Reads the tab the consultant invoked the extension on.
 *
 * Injected here rather than declared as a content script in the manifest, and that is the whole reason
 * the extension needs no standing permission on any site: `activeTab` grants access to this one tab
 * because the user clicked the toolbar icon, and it lapses when they navigate away.
 *
 * Injected as a `file` and not as a `func`, which matters: a `func` is serialised with `toString` and
 * re-parsed in the page, so it closes over nothing — every extractor would have to be inlined into one
 * unmaintainable function body. A file is a real bundle, and its completion value comes back as
 * `result`.
 */
async function readActiveTabCompany(): Promise<ReadPageResult> {
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
  return { company: injected.result as ReadPageResult["company"], sourceUrl: tab.url ?? "" };
}

class PageNotReadableError extends Error {
  constructor() {
    super("This page cannot be read. Open a company's website or LinkedIn page and try again.");
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
