import type { WorkspaceRole } from "../../auth/api/types";
import type { Project } from "../api/types";

/**
 * Whether this user may act on a mandate's content — the client mirror of `WORK_EXECUTE`: a workspace admin, or a staff
 * seat (never CLIENT). UX only; every endpoint re-checks the seat.
 */
export function canExecuteProjectWork(
  project: Project,
  userId: string | undefined,
  workspaceRoles: WorkspaceRole[] | undefined,
): boolean {
  if (workspaceRoles?.includes("ADMIN")) return true;
  const seat = project.team.find((member) => member.userId === userId);
  return seat?.projectRoles.some((role) => role !== "CLIENT") ?? false;
}

/**
 * Whether this user may seat staff and grant hiring managers sight of a mandate — the client mirror
 * of `TEAM_MANAGE` and `CLIENT_ACCESS_MANAGE`, both the lead's (a workspace admin is implicitly one).
 * A researcher holds `WORK_EXECUTE` but neither of these. UX only, as above.
 */
export function canManageProjectAccess(
  project: Project,
  userId: string | undefined,
  workspaceRoles: WorkspaceRole[] | undefined,
): boolean {
  if (workspaceRoles?.includes("ADMIN")) return true;
  const seat = project.team.find((member) => member.userId === userId);
  return seat?.projectRoles.includes("LEAD") ?? false;
}

/**
 * Whether this user may archive or restore the position — the client mirror of `PROJECT_ARCHIVE`, its lead's
 * alone. Unlike every other project action, a workspace admin without a lead's seat does not hold it.
 */
export function canArchivePosition(project: Project, userId: string | undefined): boolean {
  const seat = project.team.find((member) => member.userId === userId);
  return seat?.projectRoles.includes("LEAD") ?? false;
}
