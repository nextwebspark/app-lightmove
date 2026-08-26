import type { CaptureCompanyRequest, ProjectSummary, TriagedCompany, WorkspaceUser } from "../api/types";
import type { ExtractedCompany } from "../content/pageReader/extractedCompany";

/**
 * Everything the popup can ask the service worker to do.
 *
 * The popup makes no network call of its own and reads no page of its own. It asks; the worker acts.
 * That is not ceremony — the session token lives in the worker's storage and must never be handed to a
 * document that a page could reach, and the worker is the only context that survives the popup closing
 * mid-request.
 *
 * Every reply is a discriminated result rather than a thrown error: a message that rejects across the
 * `chrome.runtime` boundary arrives as an opaque "Could not establish connection" with the real cause
 * lost, so the worker catches its own failures and reports them as data.
 */

export type ExtensionRequest =
  | { kind: "getPairedUser" }
  | { kind: "signOut" }
  | { kind: "readActiveTabCompany" }
  | { kind: "listProjects" }
  | { kind: "captureCompany"; projectId: string; capture: CaptureCompanyRequest }
  | { kind: "rememberProject"; projectId: string }
  | { kind: "lastUsedProject" };

/** What went wrong, in the popup's own vocabulary. `code` is the API's when the API is what failed. */
export interface ExtensionFailure {
  ok: false;
  code: string;
  message: string;
}

export type ExtensionResult<T> = { ok: true; value: T } | ExtensionFailure;

export interface ReadPageResult {
  company: ExtractedCompany;
  sourceUrl: string;
}

/** The reply type for each request kind, so a call site cannot mismatch the two. */
export interface ExtensionReplies {
  getPairedUser: WorkspaceUser | null;
  signOut: null;
  readActiveTabCompany: ReadPageResult;
  listProjects: ProjectSummary[];
  captureCompany: TriagedCompany;
  rememberProject: null;
  lastUsedProject: string | null;
}

/**
 * Sends one request to the service worker and returns its typed reply.
 *
 * Used by the popup only. A worker that was asleep is woken by this call, which is why the worker
 * registers its listener at the top level of its module — registering inside a callback would mean the
 * listener does not exist yet when the waking message is delivered, and the popup would hang.
 */
export async function askServiceWorker<K extends ExtensionRequest["kind"]>(
  request: Extract<ExtensionRequest, { kind: K }>,
): Promise<ExtensionResult<ExtensionReplies[K]>> {
  try {
    return (await chrome.runtime.sendMessage(request)) as ExtensionResult<ExtensionReplies[K]>;
  } catch (error) {
    return {
      ok: false,
      code: "WORKER_UNREACHABLE",
      message: error instanceof Error ? error.message : "The extension's background worker did not answer.",
    };
  }
}
