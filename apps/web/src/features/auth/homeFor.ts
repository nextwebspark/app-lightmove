/**
 * Where a signed-in user actually belongs, given what is true of them right now.
 *
 * Its own module rather than living in routes.tsx, because the wizard pages import it and routes.tsx
 * imports them.
 *
 * Order matters: an unverified user outranks a pending invitation. Verification is step 2 and the API
 * refuses /onboarding/** without it, so routing them to accept-invite first would dead-end on a 403.
 */
export function homeFor(user: {
  emailVerified: boolean;
  workspace: { roles: string[] } | null;
  pendingInvitation: unknown;
} | null): string {
  if (!user) return "/login";
  // Everyone in a workspace lands on the projects list; the server scopes a pure client's to their seats.
  if (user.workspace) return "/";
  if (!user.emailVerified) return "/signup/verify-email";
  if (user.pendingInvitation) return "/auth/accept-invite";
  return "/signup/workspace";
}
