import type { ReactNode } from "react";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import { PendingContacts } from "./PendingContacts";
import { PendingContactTray } from "./PendingContactTray";

/**
 * Above the routes, so a card outlives a move between a position and a workspace screen. One per member and
 * workspace: a sign-in as someone else, or a switch to another workspace, starts with nothing to log. A pure client
 * never presses a number, so draws no tray.
 */
export function PendingContactsBoundary({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const workspaceId = user?.workspace?.id ?? "";
  const isStaff = !!user?.workspace && !isPureClient(user.workspace.roles);
  return (
    <PendingContacts key={`${user?.id ?? "signed-out"}:${workspaceId}`} workspaceId={workspaceId}>
      {children}
      {isStaff && <PendingContactTray />}
    </PendingContacts>
  );
}
