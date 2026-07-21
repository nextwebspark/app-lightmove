import { request } from "../../../lib/apiClient";
import type { Strategy } from "./types";

/** Every call the strategy screen makes to its own project-scoped endpoints. Writes are snapshot PUTs. */

export const STRATEGY_KEY = (projectId: string) => ["strategy", projectId] as const;

export function getStrategy(projectId: string): Promise<Strategy> {
  return request<Strategy>(`/projects/${projectId}/strategy`);
}

export function putSectors(projectId: string, strategy: Strategy): Promise<Strategy> {
  return request<Strategy>(`/projects/${projectId}/strategy/sectors`, {
    method: "PUT",
    body: strategy,
  });
}

export function putCompanySize(
  projectId: string,
  employee: string[],
  revenue: string[],
): Promise<Strategy> {
  return request<Strategy>(`/projects/${projectId}/strategy/company-size`, {
    method: "PUT",
    body: { employee, revenue },
  });
}

export function putGeography(projectId: string, markets: string[]): Promise<Strategy> {
  return request<Strategy>(`/projects/${projectId}/strategy/geography`, {
    method: "PUT",
    body: { markets },
  });
}

export function putOwnership(projectId: string, structures: string[]): Promise<Strategy> {
  return request<Strategy>(`/projects/${projectId}/strategy/ownership`, {
    method: "PUT",
    body: { structures },
  });
}

/** The company lists write bare keys — the server resolves every snapshot field itself. */
export interface CompanyKey {
  source: string;
  sourceId: string;
}

export function putTargets(projectId: string, companies: CompanyKey[]): Promise<Strategy> {
  return request<Strategy>(`/projects/${projectId}/strategy/targets`, {
    method: "PUT",
    body: { companies },
  });
}

export function putOffLimits(projectId: string, companies: CompanyKey[]): Promise<Strategy> {
  return request<Strategy>(`/projects/${projectId}/strategy/off-limits`, {
    method: "PUT",
    body: { companies },
  });
}
