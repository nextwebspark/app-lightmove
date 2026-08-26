import type { WorkspaceRole } from "../../auth/api/types";
import type { Project } from "../api/types";

/**
 * Whether this user may act on a mandate's content — add a company, move it, delete it, edit the
 * position. The client mirror of the server's `WORK_EXECUTE`.
 *
 * <p>Two ways to hold it, matching `ProjectAccess`: a workspace admin is implicitly a lead on every
 * project, and anyone else needs a *staff* seat on this one. The CLIENT seat is deliberately not
 * enough — a hiring-company representative reads the mandate and writes nothing, which is the whole
 * shape of the portal.
 *
 * <p>UX only. Every endpoint re-reads the seat from the database on every call, and refused each of
 * these writes even while the buttons were on screen; this exists so a reader is not offered actions
 * that can only end in a 403.
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
