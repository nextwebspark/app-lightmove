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
  | "ASSISTANT_UNAVAILABLE"
  | "ASSISTANT_PROPOSAL_ALREADY_ACCEPTED"
  | "FILE_TOO_LARGE"
  | "UNSUPPORTED_FILE_TYPE"
  | "IMPORT_FILE_UNREADABLE"
  | "IMPORT_TOO_MANY_ROWS"
  | "CUSTOM_COLUMN_NAME_TAKEN"
  | "CUSTOM_COLUMN_LIMIT_REACHED"
  | "POSITION_DOCUMENT_UNREADABLE"
  | "CONTACT_LOOKUP_UNAVAILABLE"
  | "CONTACT_LOOKUP_NO_CREDITS"
  | "CONTACT_LOOKUP_FAILED"
  | "CONTACT_LOOKUP_NO_PROFILE"
  | "CONTACT_LIMIT_REACHED"
  | "CANDIDATE_PROFILE_URL_LOCKED"
  | "WORKSPACE_NAME_MISMATCH"
  | "TEMPLATE_STALE"
  | "TEMPLATE_FALLBACK_REQUIRED"
  | "TEMPLATE_FILE_UNREADABLE"
  | "TEMPLATE_IMPORT_INVALID"
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
// FILE_TOO_LARGE, IMPORT_TOO_MANY_ROWS and CUSTOM_COLUMN_LIMIT_REACHED are absent for the same reason
// — each names its configured ceiling, and the ceiling is the part the reader needs.
// TEMPLATE_FILE_UNREADABLE likewise: its detail may name the template limit or the format version.
const MESSAGES: Partial<Record<ApiErrorCode, string>> = {
  ASSISTANT_UNAVAILABLE: "The assistant could not answer just now. Try again in a moment.",
  ASSISTANT_PROPOSAL_ALREADY_ACCEPTED: "These companies have already been filed.",
  TEMPLATE_STALE: "Someone saved this template after you opened it. Reload to see their version.",
  TEMPLATE_FALLBACK_REQUIRED:
    "The fallback template can't be archived or hidden — a title nothing else matches is drafted from it.",
  TEMPLATE_IMPORT_INVALID: "Some templates in the file are invalid, so none were imported.",
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
  // Two uploads raise this — the spreadsheet import and the position description — so the wording
  // stays neutral. Naming one screen's file types here misdescribes the other's refusal, and each
  // dropzone already states what it takes.
  UNSUPPORTED_FILE_TYPE: "That file type is not supported.",
  CUSTOM_COLUMN_NAME_TAKEN: "This mandate already has a column with that name — map onto it instead.",
  TRIAGE_COMPANY_NOT_EDITABLE:
    "This company came from the market export, so its details are not yours to edit.",
  CONTACT_LOOKUP_UNAVAILABLE: "Contact lookup is not set up on this deployment.",
  // Distinct from a failure on purpose: nothing was written, so the same button works once the
  // account is topped up.
  CONTACT_LOOKUP_NO_CREDITS: "No contact lookup credits left this period.",
  CONTACT_LOOKUP_FAILED: "Contact lookup didn't answer. Try again in a moment.",
  CONTACT_LOOKUP_NO_PROFILE: "Add this person's LinkedIn profile URL first.",
  CONTACT_LIMIT_REACHED: "A profile holds ten email addresses and ten phone numbers at most.",
  CANDIDATE_PROFILE_URL_LOCKED:
    "This profile was captured from LinkedIn, so its URL is not editable.",
};

/**
 * Everything the server can reject about an email address, all of which belong under the email field
 * rather than in a banner above the form — a consumer address, a disposable one, a domain with no
 * mailbox behind it, one already registered.
 */
export const EMAIL_FIELD_ERROR_CODES: readonly ApiErrorCode[] = [
  "EMAIL_NOT_WORK_ADDRESS",
  "EMAIL_DISPOSABLE",
  "EMAIL_UNDELIVERABLE",
  "EMAIL_ALREADY_REGISTERED",
];

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
