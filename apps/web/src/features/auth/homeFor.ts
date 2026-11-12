/**
 * Where a signed-in user belongs right now (its own module: routes.tsx imports the wizard pages that import it). An
 * unverified user outranks a pending invitation: /onboarding/** refuses them, so accept-invite first would 403.
 */
export const ACCEPT_INVITE_PATH = "/auth/accept-invite";

export function homeFor(user: {
  emailVerified: boolean;
  workspace: { roles: string[] } | null;
  pendingInvitations: readonly unknown[];
} | null): string {
  if (!user) return "/login";
  // Everyone in a workspace lands on the projects list; the server scopes a pure client's to their seats.
  if (user.workspace) return "/";
  if (!user.emailVerified) return "/signup/verify-email";
  if (user.pendingInvitations.length > 0) return ACCEPT_INVITE_PATH;
  return "/signup/workspace";
}
