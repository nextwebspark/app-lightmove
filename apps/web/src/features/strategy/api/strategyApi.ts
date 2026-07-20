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
