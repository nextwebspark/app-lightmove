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

export function lockPosition(projectId: string): Promise<Position> {
  return request<Position>(`/projects/${projectId}/position/lock`, { method: "POST" });
}

export function unlockPosition(projectId: string): Promise<Position> {
  return request<Position>(`/projects/${projectId}/position/unlock`, { method: "POST" });
}
