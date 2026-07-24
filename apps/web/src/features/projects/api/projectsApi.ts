import { request } from "../../../lib/apiClient";
import type { Project, ProjectRole } from "./types";

/**
 * Every call the projects feature makes, plus the query keys its screens share. Clients are their own
 * feature now — see {@code features/clients/api/clientsApi}; the New-project modal imports the client
 * calls from there.
 */

export const PROJECTS_KEY = ["projects"] as const;

export function projects(): Promise<Project[]> {
  return request<Project[]>("/projects");
}

export function createProject(payload: {
  clientId: string;
  positionTitle: string;
  targetDate?: string;
}): Promise<Project> {
  // No lead to choose: the creator is seated as the project's admin (and lead) by the server.
  return request<Project>("/projects", { method: "POST", body: payload });
}

export function updateProject(
  projectId: string,
  payload: { targetDate?: string },
): Promise<Project> {
  return request<Project>(`/projects/${projectId}`, { method: "PATCH", body: payload });
}

/** Replace-set: seats the member with these roles, or replaces the roles an existing seat holds. */
export function putProjectMember(
  projectId: string,
  memberId: string,
  roles: ProjectRole[],
): Promise<Project> {
  return request<Project>(`/projects/${projectId}/members/${memberId}`, {
    method: "PUT",
    body: { roles },
  });
}

export function removeProjectMember(projectId: string, memberId: string): Promise<Project> {
  return request<Project>(`/projects/${projectId}/members/${memberId}`, { method: "DELETE" });
}

/**
 * Attach a client contact to this mandate. An ACTIVE representative is seated read-only at once; an
 * INVITED one is parked server-side and seated automatically when they accept.
 */
export function attachRepresentative(
  projectId: string,
  representativeId: string,
): Promise<Project> {
  return request<Project>(`/projects/${projectId}/representatives`, {
    method: "POST",
    body: { representativeId },
  });
}

export function detachRepresentative(
  projectId: string,
  representativeId: string,
): Promise<Project> {
  return request<Project>(`/projects/${projectId}/representatives/${representativeId}`, {
    method: "DELETE",
  });
}
