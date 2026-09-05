import { request, requestBlob } from "../../../lib/apiClient";
import type { ImportPreview, ImportSummary, ProposedColumnMapping } from "./importTypes";

/**
 * Importing a spreadsheet into a mandate's Companies grid.
 *
 * Two calls carrying the same file. Preview reads it and proposes a mapping; commit takes it back
 * with the mapping the user confirmed. Nothing is held open between them — this browser still holds
 * the `File`, so re-posting costs one parse and saves a staging table with an expiry to explain.
 */

/**
 * Downloads the blank template and saves it. Optional — the import maps whatever headers arrive — but
 * a file built from it needs no assistant call, because every header in it is one we already know.
 *
 * A fetch and an object URL rather than an href: the bytes need the bearer token, the same reason
 * `positionApi.saveDocument` does it this way.
 */
export async function saveTemplate(projectId: string): Promise<void> {
  const blob = await requestBlob(`/projects/${projectId}/import/template`);
  const url = URL.createObjectURL(blob);
  try {
    const link = window.document.createElement("a");
    link.href = url;
    link.download = "lightmove-import-template.csv";
    link.click();
  } finally {
    URL.revokeObjectURL(url);
  }
}

/** Reads the file and answers with a mapping to confirm. Writes nothing. */
export function previewImport(projectId: string, file: File): Promise<ImportPreview> {
  const form = new FormData();
  form.append("file", file);
  return request<ImportPreview>(`/projects/${projectId}/import/preview`, {
    method: "POST",
    body: form,
  });
}

/**
 * Applies the confirmed mapping.
 *
 * The mapping goes as a `Blob` typed `application/json` rather than a plain form field, because the
 * server binds and validates it as a `@RequestPart` — a field of JSON text could not be validated.
 */
export function commitImport(
  projectId: string,
  file: File,
  columns: ProposedColumnMapping[],
): Promise<ImportSummary> {
  const form = new FormData();
  form.append("file", file);
  form.append(
    "mapping",
    new Blob([JSON.stringify({ columns })], { type: "application/json" }),
    "mapping.json",
  );
  return request<ImportSummary>(`/projects/${projectId}/import/commit`, {
    method: "POST",
    body: form,
  });
}
