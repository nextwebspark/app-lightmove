import { request } from "../../../lib/apiClient";
import type {
  Compensation,
  Competency,
  Criterion,
  MandateContext,
  Position,
  PositionDetails,
  ReportingStructure,
} from "./types";

/**
 * Every call the Position screen makes. One read, one snapshot PUT per wizard step, and the three
 * operations that are not a step: publish, withdraw, and the attached position description.
 *
 * Every write answers with the whole brief, so a caller only ever replaces its cached copy.
 */

export const POSITION_KEY = (projectId: string) => ["position", projectId] as const;

const base = (projectId: string) => `/projects/${projectId}/position`;

export function getPosition(projectId: string, signal?: AbortSignal): Promise<Position> {
  return request<Position>(base(projectId), { signal });
}

export function putDetails(projectId: string, details: PositionDetails): Promise<Position> {
  return request<Position>(`${base(projectId)}/details`, { method: "PUT", body: details });
}

export function putContext(projectId: string, context: MandateContext): Promise<Position> {
  return request<Position>(`${base(projectId)}/context`, { method: "PUT", body: context });
}

export function putReporting(projectId: string, reporting: ReportingStructure): Promise<Position> {
  return request<Position>(`${base(projectId)}/reporting`, { method: "PUT", body: reporting });
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
): Promise<Position> {
  return request<Position>(`${base(projectId)}/competencies`, {
    method: "PUT",
    body: { technical, behavioural },
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

export function documentUrl(projectId: string): string {
  return `/api/v1${base(projectId)}/document`;
}
