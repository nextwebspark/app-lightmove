import { request } from "../../../lib/apiClient";
import type { Project, ProjectActivity, ProjectType, StaffRole } from "./types";

/**
 * Every call the projects feature makes, plus the query keys its screens share. Clients are their own
 * feature now — see {@code features/clients/api/clientsApi}; the New-project modal imports the client
 * calls from there.
 */

export const PROJECTS_KEY = ["projects"] as const;

export const projectActivityKey = (projectId: string) => ["project-activity", projectId] as const;

export function projects(): Promise<Project[]> {
  return request<Project[]>("/projects");
}

export function createProject(payload: {
  clientId: string;
  positionTitle: string;
  projectType: ProjectType;
  startDate?: string;
  mappingTargetDate?: string;
  shortlistTargetDate?: string;
}): Promise<Project> {
  // No lead to choose: the server seats the creator as the mandate's lead.
  return request<Project>("/projects", { method: "POST", body: payload });
}

/**
 * A patch of the mandate itself: the brief's target start, or the timeline health is measured
 * against. Every field is optional and what is left out stays as it is.
 */
export function updateProject(
  projectId: string,
  payload: {
    targetDate?: string;
    projectType?: ProjectType;
    startDate?: string;
    mappingTargetDate?: string;
    shortlistTargetDate?: string;
  },
): Promise<Project> {
  return request<Project>(`/projects/${projectId}`, { method: "PATCH", body: payload });
}

/** What has happened on a mandate lately. Staff only — a client contact is refused it. */
export function projectActivity(projectId: string, signal?: AbortSignal): Promise<ProjectActivity[]> {
  return request<ProjectActivity[]>(`/projects/${projectId}/activity`, { signal });
}

/**
 * Seats the member with this staff role, or moves an existing seat to it. One role per seat; a CLIENT
 * role the seat already carries survives, so staffing a client contact never revokes their read access.
 */
export function putProjectMember(
  projectId: string,
  memberId: string,
  role: StaffRole,
): Promise<Project> {
  return request<Project>(`/projects/${projectId}/members/${memberId}`, {
    method: "PUT",
    body: { role },
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

/**
 * Create a client contact and attach them here in one call. One decision, one transaction — issuing
 * the registry write and the attach separately stranded the contact on the client whenever the second
 * request failed, and mailed them twice when it didn't.
 */
export function inviteRepresentativeToProject(
  projectId: string,
  payload: { fullName: string; position?: string; email: string },
): Promise<Project> {
  return request<Project>(`/projects/${projectId}/representatives/invitations`, {
    method: "POST",
    body: payload,
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
