import type { ReactNode } from "react";
import { useAuth } from "../../auth/AuthProvider";
import { isPureClient } from "../../auth/roles";
import { PendingContacts } from "./PendingContacts";
import { PendingContactTray } from "./PendingContactTray";

/**
 * Above the routes, so a card outlives a move between a position and a workspace screen. One per signed-in member:
 * a sign-in as someone else starts with nothing to log. A pure client never presses a number, so draws no tray.
 */
export function PendingContactsBoundary({ children }: { children: ReactNode }) {
  const { user } = useAuth();
  const isStaff = !!user?.workspace && !isPureClient(user.workspace.roles);
  return (
    <PendingContacts key={user?.id ?? "signed-out"}>
      {children}
      {isStaff && <PendingContactTray />}
    </PendingContacts>
  );
}
