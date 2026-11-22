import type { StartPage } from "../startpage/api/types";
import { startPagePath } from "../startpage/lib/startPage";

/**
 * Where a signed-in user belongs right now (its own module: routes.tsx imports the wizard pages that import it). An
 * unverified user outranks a pending invitation: /onboarding/** refuses them, so accept-invite first would 403.
 */
export const ACCEPT_INVITE_PATH = "/auth/accept-invite";

export function homeFor(user: {
  id: string;
  emailVerified: boolean;
  workspace: { id: string; roles: string[] } | null;
  startPage?: StartPage | null;
  pendingInvitations: readonly unknown[];
} | null): string {
  if (!user) return "/login";
  if (user.workspace) return startPagePath(user.startPage);
  if (!user.emailVerified) return "/signup/verify-email";
  if (user.pendingInvitations.length > 0) return ACCEPT_INVITE_PATH;
  return "/signup/workspace";
}
