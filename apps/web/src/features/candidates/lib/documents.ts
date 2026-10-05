import type { PersonDocument, PersonDocumentCategory } from "../api/types";

export const MAX_DOCUMENT_BYTES = 20 * 1024 * 1024;

/** The picker's filter only; the server reads the bytes and decides. */
export const DOCUMENT_ACCEPT = ".pdf,.doc,.docx,.odt,.rtf,.txt,.png,.jpg,.jpeg";

export const DOCUMENT_CATEGORIES: {
  value: PersonDocumentCategory;
  label: string;
  className: string;
}[] = [
  { value: "cv", label: "CV", className: "bg-u-accent-tint text-u-accent" },
  { value: "cover_letter", label: "Cover letter", className: "bg-u-adjacent-tint text-u-adjacent" },
  { value: "reference", label: "Reference", className: "bg-u-direct-tint text-u-direct" },
  { value: "certificate", label: "Certificate", className: "bg-u-chart-5/15 text-u-chart-5" },
  { value: "assessment", label: "Assessment", className: "bg-u-inferred-tint text-u-inferred" },
  { value: "other", label: "Other", className: "bg-u-raised text-u-text2" },
];

export function categoryOf(value: PersonDocumentCategory) {
  return DOCUMENT_CATEGORIES.find((category) => category.value === value) ?? DOCUMENT_CATEGORIES[5];
}

/** `PersonDocumentCategory.guessFrom`, so the tray proposes what the server would have chosen. */
export function guessCategory(fileName: string): PersonDocumentCategory {
  const name = fileName.toLowerCase();
  if (/cover|motivation/.test(name)) return "cover_letter";
  if (/referen|recommendation/.test(name)) return "reference";
  if (/certificat|diploma|degree|transcript/.test(name)) return "certificate";
  if (/assessment|psychometric|hogan/.test(name)) return "assessment";
  if (/(^|[^a-z])(cv|resume|résumé|curriculum)([^a-z]|$)/.test(name)) return "cv";
  return "other";
}

export function extensionOf(fileName: string): string {
  const dot = fileName.lastIndexOf(".");
  return dot < 0 ? "" : fileName.slice(dot + 1).toLowerCase();
}

export function formatBytes(bytes: number): string {
  if (bytes >= 1024 * 1024) return `${Number((bytes / (1024 * 1024)).toFixed(1))} MB`;
  return `${Math.max(1, Math.round(bytes / 1024))} KB`;
}

export type UploadPlan =
  | { kind: "version"; document: PersonDocument; next: number }
  | { kind: "new"; sameNameAs: PersonDocument | null };

/**
 * What the server will make of a file: the next version of the document whose latest file has the same
 * name, ignoring case, unless the upload asks to be kept apart.
 */
export function planUpload(
  fileName: string,
  documents: readonly PersonDocument[],
  asNewDocument: boolean,
): UploadPlan {
  const name = fileName.trim().toLowerCase();
  const match =
    documents.find((document) => document.versions[0]?.fileName.trim().toLowerCase() === name) ?? null;
  if (match && !asNewDocument) {
    return { kind: "version", document: match, next: match.versions[0].versionNo + 1 };
  }
  return { kind: "new", sameNameAs: match };
}

/** The cards grouped by category in the picker's order, CV first; the primary CV, then the latest upload. */
export function groupDocuments(
  documents: readonly PersonDocument[],
): { category: (typeof DOCUMENT_CATEGORIES)[number]; documents: PersonDocument[] }[] {
  const latest = (document: PersonDocument) => document.versions[0]?.uploadedAt ?? document.updatedAt;
  return DOCUMENT_CATEGORIES.map((category) => ({
    category,
    documents: documents
      .filter((document) => document.category === category.value)
      .sort((a, b) => Number(b.primaryCv) - Number(a.primaryCv) || latest(b).localeCompare(latest(a))),
  })).filter((group) => group.documents.length > 0);
}

export function primaryCvOf(documents: readonly PersonDocument[]): PersonDocument | null {
  return documents.find((document) => document.primaryCv && document.versions.length > 0) ?? null;
}
