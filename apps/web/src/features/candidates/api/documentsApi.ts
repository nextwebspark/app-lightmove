import { request, requestBlob } from "../../../lib/apiClient";
import { CANDIDATES_KEY_PREFIX } from "./candidatesApi";
import { POOL_KEY } from "./poolApi";
import type {
  PersonDocument,
  PersonDocumentCategory,
  PersonDocumentUpload,
  UpdatePersonDocumentPayload,
} from "./types";

/**
 * A person's documents through either door the drawers have: a position's executive drawer
 * (WORK_EXECUTE) or the Candidates page (CANDIDATE_POOL_MANAGE). Staff-only either way.
 *
 * <p>Each door keys under its own tree, so whatever refreshes that drawer refreshes these too.
 */
export type DocumentScope =
  | { kind: "position"; projectId: string; candidateId: string }
  | { kind: "person"; personId: string };

export const DOCUMENTS_KEY = (scope: DocumentScope) =>
  scope.kind === "position"
    ? ([...CANDIDATES_KEY_PREFIX(scope.projectId), "documents", scope.candidateId] as const)
    : ([...POOL_KEY, "documents", scope.personId] as const);

/** The timeline a document change adds a line to, under the same door. */
export const DOCUMENT_TIMELINE_KEY = (scope: DocumentScope) =>
  scope.kind === "position"
    ? ([...CANDIDATES_KEY_PREFIX(scope.projectId), "timeline", scope.candidateId] as const)
    : ([...POOL_KEY, "timeline", scope.personId] as const);

const baseOf = (scope: DocumentScope) =>
  scope.kind === "position"
    ? `/projects/${scope.projectId}/candidates/${scope.candidateId}/documents`
    : `/candidates/${scope.personId}/documents`;

export function listDocuments(scope: DocumentScope, signal?: AbortSignal): Promise<PersonDocument[]> {
  return request<PersonDocument[]>(baseOf(scope), { signal });
}

/** A file named like the latest file of a document here becomes its next version unless `asNewDocument`. */
export function uploadDocument(
  scope: DocumentScope,
  file: File,
  options: { category: PersonDocumentCategory; asNewDocument: boolean },
): Promise<PersonDocumentUpload> {
  const body = new FormData();
  body.append("file", file);
  body.append("category", options.category);
  body.append("asNewDocument", String(options.asNewDocument));
  return request<PersonDocumentUpload>(baseOf(scope), { method: "POST", body });
}

/** "Upload new version" on a card: the next version whatever the file is called. */
export function uploadVersion(scope: DocumentScope, documentId: string, file: File): Promise<PersonDocumentUpload> {
  const body = new FormData();
  body.append("file", file);
  return request<PersonDocumentUpload>(`${baseOf(scope)}/${documentId}/versions`, { method: "POST", body });
}

export function updateDocument(
  scope: DocumentScope,
  documentId: string,
  patch: UpdatePersonDocumentPayload,
): Promise<PersonDocument> {
  return request<PersonDocument>(`${baseOf(scope)}/${documentId}`, { method: "PATCH", body: patch });
}

export function removeDocument(scope: DocumentScope, documentId: string): Promise<void> {
  return request<void>(`${baseOf(scope)}/${documentId}`, { method: "DELETE" });
}

/** Removing a document's last version removes the document. */
export function removeVersion(scope: DocumentScope, documentId: string, versionId: string): Promise<void> {
  return request<void>(`${baseOf(scope)}/${documentId}/versions/${versionId}`, { method: "DELETE" });
}

/** The bytes, audited server-side either way; `preview` asks for a PDF or image inline as its own type. */
export function documentContent(
  scope: DocumentScope,
  documentId: string,
  versionId: string,
  preview: boolean,
  signal?: AbortSignal,
): Promise<Blob> {
  const query = preview ? "?preview=true" : "";
  return requestBlob(`${baseOf(scope)}/${documentId}/versions/${versionId}/content${query}`, { signal });
}
