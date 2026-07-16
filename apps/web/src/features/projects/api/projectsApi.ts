import { request } from "../../../lib/apiClient";
import type { Client, Project } from "./types";

/** Every call the projects feature makes, plus the query keys its screens share. */

export const PROJECTS_KEY = ["projects"] as const;
export const CLIENTS_KEY = ["clients"] as const;

export function projects(): Promise<Project[]> {
  return request<Project[]>("/projects");
}

export function createProject(payload: {
  clientId: string;
  positionTitle: string;
  leadMemberId: string;
  targetDate?: string;
}): Promise<Project> {
  return request<Project>("/projects", { method: "POST", body: payload });
}

export function updateProject(
  projectId: string,
  payload: { leadMemberId?: string; targetDate?: string },
): Promise<Project> {
  return request<Project>(`/projects/${projectId}`, { method: "PATCH", body: payload });
}

export function addProjectMember(projectId: string, memberId: string): Promise<Project> {
  return request<Project>(`/projects/${projectId}/members/${memberId}`, { method: "PUT" });
}

export function removeProjectMember(projectId: string, memberId: string): Promise<Project> {
  return request<Project>(`/projects/${projectId}/members/${memberId}`, { method: "DELETE" });
}

export function clients(): Promise<Client[]> {
  return request<Client[]>("/clients");
}

export function createClient(payload: { name: string; hqCountry?: string }): Promise<Client> {
  return request<Client>("/clients", { method: "POST", body: payload });
}
