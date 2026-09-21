import { request, requestBlob } from "../../../lib/apiClient";
import { saveBlob } from "../../../lib/saveBlob";
import type {
  Compensation,
  Competency,
  Criterion,
  MandateContext,
  Position,
  PositionDetails,
  PositionExtraction,
  PositionTemplate,
  ReportingStructure,
} from "./types";

/**
 * Every call the Position screen makes. One read, one snapshot PUT per section of the brief, the
 * operations that are not a section — publish, withdraw, the template redraft, the attached position
 * description — and the four "Read from document" calls the fill engine (lib/documentFill.ts) folds
 * into the brief.
 *
 * Every write answers with the whole brief, so a caller only ever replaces its cached copy.
 */

export const POSITION_KEY = (projectId: string) => ["position", projectId] as const;

/** Not keyed by project: the catalog is the workspace's, and every mandate in it sees the same one. */
export const POSITION_TEMPLATES_KEY = ["position-templates"] as const;

const base = (projectId: string) => `/projects/${projectId}/position`;

export function getPosition(projectId: string, signal?: AbortSignal): Promise<Position> {
  return request<Position>(base(projectId), { signal });
}

/** Not keyed by project's brief: the same mandate, one figure off it. See {@link getBriefCompensation}. */
export const POSITION_COMPENSATION_KEY = (projectId: string) => ["position", projectId, "compensation"] as const;

/**
 * What the brief pays, without drafting one. {@link getPosition} writes: it drafts and saves a brief
 * for a mandate that has none, so a screen that only wants the mandate's currency — the Companies
 * grid, offering it to a new executive — asks for this instead and leaves the brief undrafted.
 */
export function getBriefCompensation(projectId: string, signal?: AbortSignal): Promise<Compensation> {
  return request<Compensation>(`${base(projectId)}/compensation`, { signal });
}

export function listTemplates(signal?: AbortSignal): Promise<PositionTemplate[]> {
  return request<PositionTemplate[]>("/position-templates", { signal });
}

/**
 * Redrafts the brief from a template, answering with the whole document like any step write.
 *
 * It does not touch the role title — a template drafts a brief, it does not rename a search. Picking
 * a title from the type-ahead writes that title through {@link putDetails}, the same path typing it
 * takes.
 */
export function applyTemplate(projectId: string, templateId: string): Promise<Position> {
  return request<Position>(`${base(projectId)}/template`, {
    method: "POST",
    body: { templateId },
  });
}

export function putDetails(projectId: string, details: PositionDetails): Promise<Position> {
  return request<Position>(`${base(projectId)}/details`, { method: "PUT", body: details });
}

export function putContext(projectId: string, context: MandateContext): Promise<Position> {
  return request<Position>(`${base(projectId)}/context`, { method: "PUT", body: context });
}

export function putReporting(projectId: string, reporting: ReportingStructure): Promise<Position> {
  // The target date is the project's and this step only displays it, so it is dropped rather than
  // echoed back: the server has no field for it, and sending one implies an owner this screen is not.
  const { targetStart: _targetStart, ...editable } = reporting;
  return request<Position>(`${base(projectId)}/reporting`, { method: "PUT", body: editable });
}

export function putCompensation(projectId: string, compensation: Compensation): Promise<Position> {
  return request<Position>(`${base(projectId)}/compensation`, {
    method: "PUT",
    body: compensation,
  });
}

export function putCriteria(projectId: string, criteria: Criterion[]): Promise<Position> {
  return request<Position>(`${base(projectId)}/criteria`, { method: "PUT", body: { criteria } });
}

export function putCompetencies(
  projectId: string,
  technical: Competency[],
  behavioural: Competency[],
  technicalShare: number,
): Promise<Position> {
  return request<Position>(`${base(projectId)}/competencies`, {
    method: "PUT",
    body: { technical, behavioural, technicalShare },
  });
}

export function publish(projectId: string): Promise<Position> {
  return request<Position>(`${base(projectId)}/publish`, { method: "POST" });
}

export function withdrawPublication(projectId: string): Promise<Position> {
  return request<Position>(`${base(projectId)}/publish`, { method: "DELETE" });
}

export function removeDocument(projectId: string): Promise<Position> {
  return request<Position>(`${base(projectId)}/document`, { method: "DELETE" });
}

/** The one call that is not JSON: {@link request} passes a FormData through as multipart. */
export function attachDocument(projectId: string, file: File): Promise<Position> {
  const form = new FormData();
  form.append("file", file);
  return request<Position>(`${base(projectId)}/document`, { method: "POST", body: form });
}

/** Fetches the stored position description and hands it to the browser to save. */
export async function saveDocument(projectId: string, fileName: string): Promise<void> {
  saveBlob(await requestBlob(`${base(projectId)}/document`), fileName);
}

const extract = (projectId: string, section: string): Promise<PositionExtraction> =>
  request<PositionExtraction>(`${base(projectId)}/document/extract/${section}`, { method: "POST" });

/**
 * The four sections a document reading is read for — no compensation route, #395's retirement of that
 * read. Each spends a billed model call, so nothing calls these but an explicit "Read from document" —
 * never upload, never the ordinary GET of the brief.
 */
export const extractDetails = (projectId: string): Promise<PositionExtraction> => extract(projectId, "details");
export const extractContext = (projectId: string): Promise<PositionExtraction> => extract(projectId, "context");
export const extractReporting = (projectId: string): Promise<PositionExtraction> => extract(projectId, "reporting");
export const extractAssessment = (projectId: string): Promise<PositionExtraction> => extract(projectId, "assessment");
