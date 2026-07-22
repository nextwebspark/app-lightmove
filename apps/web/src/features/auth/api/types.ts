/**
 * The API's contract, mirrored.
 *
 * Kept by hand rather than generated, because there are a dozen types and a codegen step would be
 * more machinery than it saves. If this list grows past the auth module, generate it from the
 * OpenAPI document instead of maintaining it twice.
 */

export type WorkspaceRole = "ADMIN" | "MEMBER" | "CLIENT";

export interface WorkspaceSummary {
  id: string;
  name: string;
  slug: string;
  logoMark: string | null;
  emailDomain: string;
  /** The caller's workspace roles — a set. Admin checks read `roles.includes("ADMIN")`. */
  roles: WorkspaceRole[];
}

export interface User {
  id: string;
  email: string;
  fullName: string;
  title: string | null;
  avatarUrl: string | null;
  emailVerified: boolean;

  /**
   * Null until the user has created a workspace or accepted an invitation. The router reads this to
   * decide whether someone belongs in the app or back in the onboarding wizard.
   */
  workspace: WorkspaceSummary | null;

  /**
   * They finished the wizard but have not verified their email, so what they asked for is held: no
   * workspace exists on their firm's domain. Verifying is what makes it real.
   *
   * The router needs this to tell "has not started onboarding" from "has finished it and is waiting on
   * their inbox" — both of which have `workspace: null`. Without it, a user who closes the tab comes
   * back to an empty form they have already filled in.
   */
  onboardingHeld: boolean;

  /**
   * The redeemable invitation addressed to this user, when they are not yet placed. Server-derived so
   * an invitee is routed to "join {workspace}" from any tab — the emailed token lives in one tab's
   * sessionStorage, but this survives everywhere the session does. Null once placed.
   */
  pendingInvitation: PendingInvitation | null;
}

/** What the invitee is told about their outstanding invitation. Deliberately token-free. */
export interface PendingInvitation {
  workspaceName: string;
  role: WorkspaceRole;
}

export interface AuthResponse {
  /** Held in memory only. Never localStorage — see AuthProvider. */
  accessToken: string;
  /** Seconds. Lets the client refresh *before* a 401 rather than in response to one. */
  expiresIn: number;
  user: User;
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
