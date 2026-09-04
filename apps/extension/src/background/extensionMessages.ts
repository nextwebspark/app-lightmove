import type {
  CaptureCompanyRequest,
  CapturedCandidate,
  ProjectSummary,
  SaveCandidateRequest,
  TriagedCompany,
  WorkspaceUser,
} from "../api/types";
import type { ExtractedCompany } from "../content/pageReader/extractedCompany";
import type { ExtractedPerson } from "../content/pageReader/extractedPerson";
import type { PageSubjectKind } from "../content/pageReader/readPageSubject";
import type { CaptureSettings } from "../domain/captureSettings";

/**
 * Everything the popup can ask the service worker to do. It asks; the worker acts, because the worker
 * holds the session and outlives the popup.
 *
 * Every reply is a discriminated result rather than a thrown error: a rejection across the
 * `chrome.runtime` boundary arrives as an opaque "Could not establish connection", cause lost.
 */

export type ExtensionRequest =
  | { kind: "getPairedUser" }
  | { kind: "signOut" }
  | { kind: "openConnectPage" }
  | { kind: "readActivePage" }
  | { kind: "activePageKey" }
  | { kind: "listProjects" }
  | { kind: "captureCompany"; projectId: string; capture: CaptureCompanyRequest }
  | { kind: "captureCandidate"; projectId: string; candidate: SaveCandidateRequest }
  | { kind: "rememberProject"; projectId: string }
  | { kind: "lastUsedProject" }
  | { kind: "removeTriageCompany"; projectId: string; triageCompanyId: string }
  | { kind: "removeCandidate"; projectId: string; candidateId: string }
  | { kind: "readSettings" }
  | { kind: "writeSettings"; settings: Partial<CaptureSettings> };

/** What went wrong, in the popup's own vocabulary. `code` is the API's when the API is what failed. */
export interface ExtensionFailure {
  ok: false;
  code: string;
  message: string;
}

export type ExtensionResult<T> = { ok: true; value: T } | ExtensionFailure;

/** One read of the active tab: both sides of it, and which one the page is about. */
export interface ReadPageResult {
  subject: PageSubjectKind;
  person: ExtractedPerson;
  company: ExtractedCompany;
  /** The address actually read, tracking and all — provenance, sent with the capture. */
  sourceUrl: string;
  /** Which page this is, canonically. The panel's identity for a read; see `pageKeyOf`. */
  pageKey: string;
}

/**
 * Which page the panel is looking at, with nothing read from it yet.
 *
 * The panel asks for this rather than deriving it, so the tab the key names and the tab the read
 * comes from are resolved by the same rule — otherwise a panel whose guess disagreed with the
 * worker's would sit empty waiting for a read that answers under another key.
 */
export interface ActivePageKey {
  pageKey: string;
  sourceUrl: string;
}

/** The reply type for each request kind, so a call site cannot mismatch the two. */
export interface ExtensionReplies {
  getPairedUser: WorkspaceUser | null;
  signOut: null;
  openConnectPage: null;
  readActivePage: ReadPageResult;
  activePageKey: ActivePageKey;
  listProjects: ProjectSummary[];
  captureCompany: TriagedCompany;
  captureCandidate: CapturedCandidate;
  rememberProject: null;
  lastUsedProject: string | null;
  removeTriageCompany: null;
  removeCandidate: null;
  readSettings: CaptureSettings;
  writeSettings: CaptureSettings;
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
    const reply = (await chrome.runtime.sendMessage(request)) as ExtensionResult<ExtensionReplies[K]> | undefined;
    // A worker that dies mid-request closes the channel without answering, and Chrome *resolves* with
    // undefined rather than rejecting — so without this every caller reads `.ok` off nothing and throws
    // a TypeError out of its queryFn, which is the shape this module exists to prevent.
    return reply ?? {
      ok: false,
      code: "WORKER_UNREACHABLE",
      message: "The extension's background worker closed the connection without answering.",
    };
  } catch (error) {
    return {
      ok: false,
      code: "WORKER_UNREACHABLE",
      message: error instanceof Error ? error.message : "The extension's background worker did not answer.",
    };
  }
}
