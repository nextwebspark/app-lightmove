import type { WorkspaceRole } from "./api/types";

/**
 * A pure client (only the CLIENT role) is a portal guest: read-only, scoped to the mandates they're
 * attached to. The registry and roster are staff surfaces they can't read — don't query them, don't
 * show them. A member holding CLIENT alongside a staff role is staff.
 */
export function isPureClient(roles: WorkspaceRole[]): boolean {
  return roles.includes("CLIENT") && !roles.some((role) => role === "ADMIN" || role === "MEMBER");
}
