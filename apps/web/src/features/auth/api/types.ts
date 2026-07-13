/**
 * The API's contract, mirrored.
 *
 * Kept by hand rather than generated, because there are a dozen types and a codegen step would be
 * more machinery than it saves. If this list grows past the auth module, generate it from the
 * OpenAPI document instead of maintaining it twice.
 */

export type WorkspaceRole = "ADMIN" | "CONSULTANT" | "RESEARCHER";

export interface WorkspaceSummary {
  id: string;
  name: string;
  slug: string;
  logoMark: string | null;
  emailDomain: string;
  role: WorkspaceRole;
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  title: string | null;
  avatarUrl: string | null;
  emailVerified: boolean;

  /**
   * Null until the user has joined or created a workspace. The router reads this to decide whether
   * someone belongs in the app or back in the onboarding wizard.
   */
  workspace: WorkspaceSummary | null;
}

export interface AuthResponse {
  /** Held in memory only. Never localStorage — see AuthProvider. */
  accessToken: string;
  /** Seconds. Lets the client refresh *before* a 401 rather than in response to one. */
  expiresIn: number;
  user: User;
}

/** A workspace on the user's email domain, offered at signup so they can find their firm. */
export interface JoinableWorkspace {
  id: string;
  name: string;
  logoMark: string | null;
  memberCount: number;
  adminName: string | null;
}

/** Someone waiting on an admin's decision. */
export interface PendingMember {
  memberId: string;
  userId: string;
  fullName: string;
  email: string;
  requestedRole: WorkspaceRole;
  requestedAt: string;
}

export interface SignupRequest {
  fullName: string;
  email: string;
  password: string;
  termsAccepted: boolean;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface CreateWorkspaceRequest {
  name: string;
  companySize: string;
  primaryRegion: string;
  /** The wizard's "Your role" — a job title, not an authority. The creator is always ADMIN. */
  jobTitle: string;
  teamFocus: string;
}

export interface InviteRequest {
  email: string;
  role: WorkspaceRole;
}

/** What an invitation link says, readable before the invitee has an account. */
export interface InvitationPreview {
  email: string;
  role: WorkspaceRole;
  workspaceName: string;
  inviterName: string | null;
}

/** Which sign-in methods this deployment actually offers. */
export interface AuthProviders {
  google: boolean;
}

/**
 * An RFC 9457 ProblemDetail, as GlobalExceptionHandler produces it.
 *
 * `code` is the stable machine-readable identity of the failure — switch on that, never on `detail`,
 * whose wording is free to change.
 */
export interface ApiError {
  code: string;
  detail: string;
  status: number;
  correlationId: string;
  /** Present on a validation failure: field name → the message to show beneath it. */
  fieldErrors?: Record<string, string>;
}
