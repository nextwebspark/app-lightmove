import { request, requestBlob } from "../../../lib/apiClient";
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

/**
 * Fetches the stored position description and hands it to the browser to save.
 *
 * Not an `<a href>`: the access token lives in a module variable inside `apiClient` and rides on the
 * `Authorization` header, which a browser navigation does not send — and the refresh cookie is
 * path-scoped to the auth routes, so a plain link to this endpoint 401s for every user, every time.
 */
export async function saveDocument(projectId: string, fileName: string): Promise<void> {
  const blob = await requestBlob(`${base(projectId)}/document`);
  const url = URL.createObjectURL(blob);
  try {
    const link = window.document.createElement("a");
    link.href = url;
    link.download = fileName;
    window.document.body.append(link);
    link.click();
    link.remove();
  } finally {
    // Revoked once the click has been handed off; leaving it would pin the blob in memory for the
    // life of the document.
    URL.revokeObjectURL(url);
  }
}
