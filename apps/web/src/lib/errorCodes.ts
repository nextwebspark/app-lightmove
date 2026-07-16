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
  | "WORKSPACE_ALREADY_EXISTS"
  | "WORKSPACE_NOT_FOUND"
  | "NOT_A_MEMBER"
  | "FORBIDDEN"
  | "INVITATION_INVALID"
  | "INVITATION_EXPIRED"
  | "LAST_ADMIN"
  | "MEMBER_LEADS_PROJECTS"
  | "CLIENT_ALREADY_EXISTS"
  | "PROJECT_LAST_ADMIN"
  | "WORKSPACE_NAME_MISMATCH"
  | "CONFLICT"
  | "RATE_LIMITED"
  | "CSRF_TOKEN_INVALID"
  | "NOT_FOUND"
  | "METHOD_NOT_ALLOWED"
  | "INTERNAL_ERROR";

const MESSAGES: Partial<Record<ApiErrorCode, string>> = {
  LAST_ADMIN: "A workspace must keep at least one admin.",
  MEMBER_LEADS_PROJECTS: "They are the only admin on active projects — hand those over first.",
  CLIENT_ALREADY_EXISTS: "A client with this name already exists.",
  PROJECT_LAST_ADMIN: "A project must keep at least one admin.",
  WORKSPACE_NAME_MISMATCH: "Type the workspace name exactly to confirm.",
  FORBIDDEN: "You don't have permission to do this.",
  RATE_LIMITED: "Too many requests — slow down a little.",
  EMAIL_NOT_VERIFIED: "Verify your email address to continue.",
  ACCOUNT_SUSPENDED: "This account has been suspended.",
  EMAIL_NOT_WORK_ADDRESS: "Use your work email — the domain identifies your organization.",
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
