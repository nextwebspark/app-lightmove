import type { LightMoveApiClient } from "./lightMoveApiClient";
import type { ProjectSummary } from "./types";

/**
 * The mandates the signed-in consultant can work on — the popup's project dropdown.
 *
 * The web app's own endpoint, unchanged: `GET /projects` already scopes the list to the caller's
 * workspace and their seats. The extension adds nothing to it and must not.
 */
export function listProjects(api: LightMoveApiClient): Promise<ProjectSummary[]> {
  return api.request<ProjectSummary[]>("/projects");
}
