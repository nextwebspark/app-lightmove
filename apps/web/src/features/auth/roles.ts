import type { WorkspaceRole } from "./api/types";

/**
 * A pure client (only the CLIENT role) is a portal guest: read-only, scoped to the mandates they're
 * attached to. The registry and roster are staff surfaces they can't read — don't query them, don't
 * show them. A member holding CLIENT alongside a staff role is staff.
 */
export function isPureClient(roles: WorkspaceRole[]): boolean {
  return roles.includes("CLIENT") && !roles.some((role) => role === "ADMIN" || role === "MEMBER");
}

/**
 * Whether the server answers this person every position of the workspace (`PROJECT_BROWSE_ALL`, held by ADMIN): for
 * choosing an empty state only, never for deciding what is shown. The one place the role stands for that action.
 */
export function seesEveryPosition(roles: WorkspaceRole[]): boolean {
  return roles.includes("ADMIN");
}
