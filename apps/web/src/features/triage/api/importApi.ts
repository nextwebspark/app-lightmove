import { request } from "../../../lib/apiClient";
import type { ImportPreview, ImportSummary, ProposedColumnMapping } from "./importTypes";

/**
 * Importing a spreadsheet into a mandate's Companies grid.
 *
 * Two calls carrying the same file. Preview reads it and proposes a mapping; commit takes it back
 * with the mapping the user confirmed. Nothing is held open between them — this browser still holds
 * the `File`, so re-posting costs one parse and saves a staging table with an expiry to explain.
 */

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
