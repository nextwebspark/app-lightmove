import type { DrawerTab } from "../../candidates/lib/drawerTabs";
import type { TaskResponse } from "../../tasks/api/types";

/**
 * An executive's record from Home: the position's In universe table with their panel open. Someone since unmapped
 * from the position is still the workspace's person, so their record opens on Candidates.
 */
export function recordPath(projectId: string, candidateId: string | null, personId: string, tab?: DrawerTab): string {
  if (!candidateId) return `/candidates?person=${encodeURIComponent(personId)}`;
  const query = new URLSearchParams({ view: "table", candidate: candidateId });
  if (tab) query.set("tab", tab);
  return `/projects/${projectId}/companies/universe?${query}`;
}

export function taskPath(task: Pick<TaskResponse, "projectId" | "candidateId" | "personId">): string {
  return recordPath(task.projectId, task.candidateId, task.personId);
}
