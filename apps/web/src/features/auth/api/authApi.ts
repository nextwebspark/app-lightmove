import { request, setAccessToken } from "../../../lib/apiClient";
import type {
  AuthProviders,
  AuthResponse,
  CreateWorkspaceRequest,
  InvitationPreview,
  InviteRequest,
  JoinableWorkspace,
  LoginRequest,
  PendingMember,
  SignupRequest,
  User,
  WorkspaceRole,
} from "./types";

/**
 * Every call the auth module makes. One place, so a URL or a method never has to be remembered twice.
 *
 * The functions that establish a session also install the returned access token, because forgetting to
 * do that is a bug with no symptom until the next request 401s.
 */

export async function signup(payload: SignupRequest): Promise<AuthResponse> {
  const session = await request<AuthResponse>("/auth/signup", {
    method: "POST",
    body: payload,
    anonymous: true,
  });
  setAccessToken(session.accessToken);
  return session;
}

export async function login(payload: LoginRequest): Promise<AuthResponse> {
  const session = await request<AuthResponse>("/auth/login", {
    method: "POST",
    body: payload,
    anonymous: true,
  });
  setAccessToken(session.accessToken);
  return session;
}

export async function logout(): Promise<void> {
  await request<void>("/auth/logout", { method: "POST", withCsrf: true });
  setAccessToken(null);
}

export function me(): Promise<User> {
  return request<User>("/auth/me");
}

export function providers(): Promise<AuthProviders> {
  return request<AuthProviders>("/auth/providers", { anonymous: true });
}

/** Redeems the emailed verification link. */
export function verifyEmail(token: string): Promise<User> {
  return request<User>(`/auth/verify?token=${encodeURIComponent(token)}`, {
    method: "POST",
    anonymous: true,
  });
}

export function resendVerification(email: string): Promise<void> {
  return request<void>("/auth/verify/resend", {
    method: "POST",
    body: { email },
    anonymous: true,
  });
}

// ── Onboarding ──────────────────────────────────────────────────────────────

/** The workspaces already on this user's email domain — "is my firm already here?" */
export function joinableWorkspaces(): Promise<JoinableWorkspace[]> {
  return request<JoinableWorkspace[]>("/onboarding/workspaces");
}

/** Editing the workspace you already run — which is what Back means once step 2 has committed. */
export function updateWorkspace(payload: CreateWorkspaceRequest): Promise<User> {
  return request<User>("/onboarding/workspace", {
    method: "PATCH",
    body: payload,
  });
}

export function createWorkspace(payload: CreateWorkspaceRequest): Promise<User> {
  return request<User>("/onboarding/workspace", { method: "POST", body: payload });
}

/** Asks to join. Grants nothing — an admin has to approve it. */
export function requestToJoin(workspaceId: string, requestedRole: WorkspaceRole): Promise<User> {
  return request<User>("/onboarding/join-requests", {
    method: "POST",
    body: { workspaceId, requestedRole },
  });
}

export function invite(invites: InviteRequest[]): Promise<{ sent: number }> {
  return request<{ sent: number }>("/onboarding/invitations", {
    method: "POST",
    body: invites,
  });
}

/** Anonymous: the invitee has no session yet, and needs to see what they were invited to. */
export function previewInvitation(token: string): Promise<InvitationPreview> {
  return request<InvitationPreview>(
    `/onboarding/invitations/preview?token=${encodeURIComponent(token)}`,
    { anonymous: true },
  );
}

export function acceptInvitation(token: string): Promise<User> {
  return request<User>("/onboarding/invitations/accept", {
    method: "POST",
    body: { token },
  });
}

// ── Membership decisions ────────────────────────────────────────────────────

export function pendingMembers(): Promise<PendingMember[]> {
  return request<PendingMember[]>("/members/pending");
}

export function approveMember(memberId: string, role: WorkspaceRole): Promise<PendingMember> {
  return request<PendingMember>(`/members/${memberId}/approve`, {
    method: "POST",
    body: { role },
  });
}

export function rejectMember(memberId: string): Promise<void> {
  return request<void>(`/members/${memberId}/reject`, { method: "POST" });
}
