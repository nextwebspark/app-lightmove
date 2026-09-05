import { ApiRequestError } from "./apiClient";

/**
 * The backend's ErrorCode enum, mirrored — the machine-readable identity every failure carries.
 * UI switches on these; `messageFor` is the one place user-facing wording lives, so the same failure
 * never reads differently on two screens.
 */
export type ApiErrorCode =
  | "VALIDATION_FAILED"
  | "INVALID_CREDENTIALS"
  | "ACCOUNT_LOCKED"
  | "ACCOUNT_SUSPENDED"
  | "EMAIL_NOT_VERIFIED"
  | "EMAIL_ALREADY_REGISTERED"
  | "EMAIL_UNDELIVERABLE"
  | "EMAIL_DISPOSABLE"
  | "EMAIL_NOT_WORK_ADDRESS"
  | "ALREADY_IN_WORKSPACE"
  | "TOKEN_INVALID"
  | "TOKEN_EXPIRED"
  | "REFRESH_TOKEN_INVALID"
  | "REFRESH_TOKEN_REUSED"
  | "CURRENT_PASSWORD_INVALID"
  | "PASSWORD_NOT_SET"
  | "SESSION_NOT_FOUND"
  | "CURRENT_SESSION_NOT_REVOCABLE"
  | "WORKSPACE_ALREADY_EXISTS"
  | "WORKSPACE_NOT_FOUND"
  | "NOT_A_MEMBER"
  | "FORBIDDEN"
  | "INVITATION_INVALID"
  | "INVITATION_EXPIRED"
  | "LAST_ADMIN"
  | "MEMBER_LEADS_PROJECTS"
  | "CLIENT_ALREADY_EXISTS"
  | "PROJECT_LAST_LEAD"
  | "BULK_ADD_SCOPE_TOO_LARGE"
  | "TRIAGE_COMPANY_ALREADY_HELD"
  | "TRIAGE_COMPANY_NOT_EDITABLE"
  | "CANDIDATE_ALREADY_MAPPED"
  | "STRATEGY_SEARCH_NAME_TAKEN"
  | "CUSTOM_COLUMN_NAME_TAKEN"
  | "CUSTOM_COLUMN_LIMIT_REACHED"
  | "WORKSPACE_NAME_MISMATCH"
  | "CONFLICT"
  | "RATE_LIMITED"
  | "CSRF_TOKEN_INVALID"
  | "NOT_FOUND"
  | "METHOD_NOT_ALLOWED"
  | "UNSUPPORTED_MEDIA_TYPE"
  | "NOT_ACCEPTABLE"
  | "INTERNAL_ERROR";

// BULK_ADD_SCOPE_TOO_LARGE is deliberately absent: its server detail names how many companies matched
// and how many may be added, which no fixed sentence here could. Adding it would lose both numbers.
// CUSTOM_COLUMN_LIMIT_REACHED is absent for the same reason — its detail names the configured
// ceiling, and the ceiling is the part the reader needs.
const MESSAGES: Partial<Record<ApiErrorCode, string>> = {
  LAST_ADMIN: "A workspace must keep at least one admin.",
  MEMBER_LEADS_PROJECTS: "They are the only lead on active projects — hand those over first.",
  CLIENT_ALREADY_EXISTS: "A client with this name already exists.",
  PROJECT_LAST_LEAD: "A project must keep at least one lead.",
  WORKSPACE_NAME_MISMATCH: "Type the workspace name exactly to confirm.",
  FORBIDDEN: "You don't have permission to do this.",
  RATE_LIMITED: "Too many requests — slow down a little.",
  EMAIL_NOT_VERIFIED: "Verify your email address to continue.",
  ACCOUNT_SUSPENDED: "This account has been suspended.",
  EMAIL_NOT_WORK_ADDRESS: "Use your work email — the domain identifies your organization.",
  CURRENT_PASSWORD_INVALID: "That is not your current password.",
  PASSWORD_NOT_SET: "This account signs in with a provider — set a password from the reset link.",
  SESSION_NOT_FOUND: "That session has already ended.",
  CURRENT_SESSION_NOT_REVOCABLE: "Use sign out to end the session you are using.",
  STRATEGY_SEARCH_NAME_TAKEN: "A search with that name is already saved here.",
  CANDIDATE_ALREADY_MAPPED: "Someone with that name is already mapped here.",
  CUSTOM_COLUMN_NAME_TAKEN: "This mandate already has a column with that name — map onto it instead.",
  TRIAGE_COMPANY_NOT_EDITABLE:
    "This company came from the market export, so its details are not yours to edit.",
};

/**
 * Wording for a failure, in preference order: our copy for the code, the server's own detail, then a
 * generic line for anything unrecognisable (network failures, HTML error pages…).
 */
export function messageFor(error: unknown): string {
  if (error instanceof ApiRequestError) {
    const known = MESSAGES[error.code as ApiErrorCode];
    if (known) return known;
    if (error.problem.detail) return error.problem.detail;
  }
  return "Something went wrong. Try again.";
}

/** The code of a failed request, if it was one — for switching on special-cased failures. */
export function codeOf(error: unknown): ApiErrorCode | null {
  return error instanceof ApiRequestError ? (error.code as ApiErrorCode) : null;
}
