import { request, requestBlob } from "../../../lib/apiClient";
import { saveBlob } from "../../../lib/saveBlob";
import type {
  TemplateDetail,
  TemplateImportResult,
  TemplateOverview,
  TemplateScope,
  TemplateWriteRequest,
} from "./types";

/**
 * Settings → Templates (the caller's workspace) and Settings → Template library (the one every
 * workspace shares). One module for both: the calls have the same shape, and only the root differs.
 */

export const TEMPLATE_ADMIN_KEY = (scope: TemplateScope) => ["template-admin", scope] as const;

export const TEMPLATE_DETAIL_KEY = (scope: TemplateScope, code: string) =>
  ["template-admin", scope, code] as const;

const ROOTS: Record<TemplateScope, string> = {
  library: "/platform/position-templates",
  workspace: "/workspace/position-templates",
};

const EXPORT_FILE_NAMES: Record<TemplateScope, string> = {
  library: "lightmove-template-library.json",
  workspace: "lightmove-position-templates.json",
};

const templateUrl = (scope: TemplateScope, code: string) => `${ROOTS[scope]}/${encodeURIComponent(code)}`;

export function listTemplates(scope: TemplateScope, signal?: AbortSignal): Promise<TemplateOverview[]> {
  return request<TemplateOverview[]>(ROOTS[scope], { signal });
}

export function getTemplate(scope: TemplateScope, code: string, signal?: AbortSignal): Promise<TemplateDetail> {
  return request<TemplateDetail>(templateUrl(scope, code), { signal });
}

export function createTemplate(scope: TemplateScope, template: TemplateWriteRequest): Promise<TemplateDetail> {
  return request<TemplateDetail>(ROOTS[scope], { method: "POST", body: template });
}

/** In the workspace scope, the first save of a library template takes the firm's own copy of it. */
export function saveTemplate(
  scope: TemplateScope,
  code: string,
  template: TemplateWriteRequest,
): Promise<TemplateDetail> {
  return request<TemplateDetail>(templateUrl(scope, code), { method: "PUT", body: template });
}

/** Library only: archive (`false`) or restore (`true`). */
export function setTemplateActive(code: string, active: boolean): Promise<TemplateDetail> {
  return request<TemplateDetail>(`${templateUrl("library", code)}/active`, {
    method: "PATCH",
    body: { active },
  });
}

/** Workspace only: take a library template out of the firm's picker and title matching, or put it back. */
export function setTemplateHidden(code: string, hidden: boolean): Promise<TemplateDetail> {
  return request<TemplateDetail>(`${templateUrl("workspace", code)}/hidden`, {
    method: "PATCH",
    body: { hidden },
  });
}

/**
 * Workspace only: resets the firm's copy to the library's template, or deletes one the firm wrote.
 * Carries the version the editor opened, so neither discards an edit it did not see.
 */
export function removeTemplate(code: string, version: number): Promise<void> {
  return request<void>(`${templateUrl("workspace", code)}?version=${version}`, { method: "DELETE" });
}

export async function exportTemplates(scope: TemplateScope): Promise<void> {
  saveBlob(await requestBlob(`${ROOTS[scope]}/export`), EXPORT_FILE_NAMES[scope]);
}

export async function downloadSchema(scope: TemplateScope): Promise<void> {
  saveBlob(await requestBlob(`${ROOTS[scope]}/schema`), "position-templates.schema.json");
}

/** Reads the file and answers with what an import would do. Writes nothing. */
export function previewImport(scope: TemplateScope, file: File): Promise<TemplateImportResult> {
  return request<TemplateImportResult>(`${ROOTS[scope]}/import/preview`, { method: "POST", body: formOf(file) });
}

/** Sends the same file again and writes it — all of it, or none if any template is invalid. */
export function commitImport(scope: TemplateScope, file: File): Promise<TemplateImportResult> {
  return request<TemplateImportResult>(`${ROOTS[scope]}/import/commit`, { method: "POST", body: formOf(file) });
}

function formOf(file: File): FormData {
  const form = new FormData();
  form.append("file", file);
  return form;
}
