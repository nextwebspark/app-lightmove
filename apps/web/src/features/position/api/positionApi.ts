import { request } from "../../../lib/apiClient";
import type { Competency, Criterion, Position, PositionDetails } from "./types";

/** Every call the position screen makes. Writes are snapshot PUTs, matching the autosave model. */

export const POSITION_KEY = (projectId: string) => ["position", projectId] as const;

export function getPosition(projectId: string): Promise<Position> {
  return request<Position>(`/projects/${projectId}/position`);
}

export function putPosition(projectId: string, details: PositionDetails): Promise<Position> {
  return request<Position>(`/projects/${projectId}/position`, { method: "PUT", body: details });
}

export function putCriteria(projectId: string, criteria: Criterion[]): Promise<Position> {
  return request<Position>(`/projects/${projectId}/position/criteria`, {
    method: "PUT",
    body: { criteria },
  });
}

export function putCompetencies(
  projectId: string,
  technical: Competency[],
  behavioural: Competency[],
): Promise<Position> {
  return request<Position>(`/projects/${projectId}/position/competencies`, {
    method: "PUT",
    body: { technical, behavioural },
  });
}

/** Uploads a position-description document; the server extracts and applies what it finds. */
export function uploadBriefDocument(projectId: string, file: File): Promise<Position> {
  const form = new FormData();
  form.append("file", file);
  return request<Position>(`/projects/${projectId}/position/brief-document`, {
    method: "POST",
    body: form,
  });
}

export function removeBriefDocument(projectId: string): Promise<Position> {
  return request<Position>(`/projects/${projectId}/position/brief-document`, { method: "DELETE" });
}
