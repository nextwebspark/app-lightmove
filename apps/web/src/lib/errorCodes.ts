import { ApiRequestError } from "./apiClient";
import { AutosaveFailedError } from "./useAutosave";

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
  | "TOKEN_INVALID"
  | "TOKEN_EXPIRED"
  | "REFRESH_TOKEN_INVALID"
  | "REFRESH_TOKEN_REUSED"
  | "CURRENT_PASSWORD_INVALID"
  | "PASSWORD_NOT_SET"
  | "SESSION_NOT_FOUND"
  | "CURRENT_SESSION_NOT_REVOCABLE"
  | "WORKSPACE_NOT_FOUND"
  | "ALREADY_IN_WORKSPACE"
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
  | "CANDIDATE_POSSIBLE_DUPLICATE"
  | "CANDIDATE_KEYS_NAME_ANOTHER"
  | "PERSON_PROFILE_HELD"
  | "PERSON_NOTE_NOT_YOURS"
  | "PERSON_DOCUMENT_DUPLICATE"
  | "PERSON_DOCUMENT_NOT_YOURS"
  | "PERSON_DOCUMENT_LIMIT"
  | "CANDIDATE_TAG_EXISTS"
  | "CANDIDATE_TAG_RETIRED"
  | "PERSON_OWNER_NOT_STAFF"
  | "PERSON_DO_NOT_CONTACT"
  | "STRATEGY_SEARCH_NAME_TAKEN"
  | "ASSISTANT_UNAVAILABLE"
  | "ASSISTANT_BUSY"
  | "ASSISTANT_STILL_ANSWERING"
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
  | "EXECUTIVE_SOURCING_UNAVAILABLE"
  | "EXECUTIVE_SOURCING_TOO_MANY_COMPANIES"
  | "EXECUTIVE_SOURCING_IN_PROGRESS"
  | "PEOPLE_SEARCH_UNAVAILABLE"
  | "PEOPLE_SEARCH_NO_CREDITS"
  | "PEOPLE_SEARCH_FAILED"
  | "PEOPLE_SEARCH_REJECTED"
  | "PEOPLE_SEARCH_EMPTY_FILTER"
  | "PEOPLE_SEARCH_PERSON_UNKNOWN"
  | "MAILBOX_UNAVAILABLE"
  | "ZOOM_UNAVAILABLE"
  | "ZOOM_NOT_CONNECTED"
  | "ZOOM_RECONNECT_NEEDED"
  | "ZOOM_CONNECT_CANCELLED"
  | "ZOOM_CONNECT_FAILED"
  | "INTEGRATION_ENCRYPTION_UNAVAILABLE"
  | "INTEGRATION_SHARED_APP_UNAVAILABLE"
  | "MAILBOX_PROVIDER_UNSUPPORTED"
  | "MAILBOX_NOT_CONNECTED"
  | "MAILBOX_RECONNECT_NEEDED"
  | "MAILBOX_CONNECT_EXPIRED"
  | "MAILBOX_CONNECT_CANCELLED"
  | "MAILBOX_CONNECT_FAILED"
  | "MAILBOX_SEND_FAILED"
  | "OUTREACH_SEQUENCE_IN_USE"
  | "OUTREACH_PERSON_SKIPPED"
  | "OUTREACH_ALREADY_ENROLLED"
  | "OUTREACH_ADDRESS_NOT_ON_FILE"
  | "OUTREACH_NOT_RUNNING"
  | "OUTREACH_BOOKING_LINK_UNAVAILABLE"
  | "OUTREACH_BOOKING_LINK_FAILED"
  | "MEETING_DO_NOT_CONTACT"
  | "MEETING_SLOT_TAKEN"
  | "MEETING_SLOT_INVALID"
  | "MEETING_BOOK_FAILED"
  | "MEETING_CALENDAR_UNAVAILABLE"
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
  | "API_KEY_NOT_FOUND"
  | "API_KEY_LIMIT_REACHED"
  | "API_KEY_READS_NOTHING"
  | "OAUTH_GRANT_NOT_FOUND"
  | "INTERNAL_ERROR";

const MESSAGES: Partial<Record<ApiErrorCode, string>> = {
  ASSISTANT_UNAVAILABLE: "The assistant could not answer just now. Try again in a moment.",
  ASSISTANT_BUSY: "The assistant is busy answering other questions. Try again in a moment.",
  ASSISTANT_STILL_ANSWERING:
    "This is taking longer than usual. The answer will appear in this chat's history when it is ready — no need to ask again.",
  ASSISTANT_PROPOSAL_ALREADY_ACCEPTED: "These companies have already been filed.",
  TEMPLATE_STALE: "Someone saved this template after you opened it. Reload to see their version.",
  TEMPLATE_FALLBACK_REQUIRED:
    "The fallback template can't be archived or hidden — a title nothing else matches is drafted from it.",
  TEMPLATE_IMPORT_INVALID: "Some templates in the file are invalid, so none were imported.",
  LAST_ADMIN: "A workspace must keep at least one admin.",
  MEMBER_LEADS_PROJECTS: "They are the only lead on active positions — hand those over first.",
  CLIENT_ALREADY_EXISTS: "That name is already taken.",
  PROJECT_LAST_LEAD: "A position must keep at least one lead.",
  WORKSPACE_NAME_MISMATCH: "Type the workspace name exactly to confirm.",
  FORBIDDEN: "You don't have permission to do this.",
  API_KEY_NOT_FOUND: "That key no longer exists. Reload to see the current list.",
  API_KEY_LIMIT_REACHED: "You have the most API keys allowed. Revoke one you no longer use.",
  API_KEY_READS_NOTHING: "Choose what the key may read as well as MCP access.",
  RATE_LIMITED: "Too many requests in a short time. Wait a minute and try again.",
  ALREADY_IN_WORKSPACE: "You already belong to a workspace. Found another from Settings → Workspaces.",
  EMAIL_NOT_VERIFIED: "Verify your email address to continue.",
  ACCOUNT_SUSPENDED: "This account has been suspended.",
  EMAIL_NOT_WORK_ADDRESS: "Use your work email — the domain identifies your organization.",
  CURRENT_PASSWORD_INVALID: "That is not your current password.",
  PASSWORD_NOT_SET: "This account signs in with a provider — set a password from the reset link.",
  SESSION_NOT_FOUND: "That session has already ended.",
  CURRENT_SESSION_NOT_REVOCABLE: "Use sign out to end the session you are using.",
  STRATEGY_SEARCH_NAME_TAKEN: "A search with that name is already saved here.",
  CANDIDATE_ALREADY_MAPPED: "Someone with that name is already mapped here.",
  CANDIDATE_POSSIBLE_DUPLICATE: "Your team already has someone with that name at that employer.",
  CANDIDATE_KEYS_NAME_ANOTHER: "The LinkedIn profile or email typed belongs to someone else in your candidates.",
  PERSON_PROFILE_HELD: "Another candidate in this workspace already has that LinkedIn profile.",
  PERSON_NOTE_NOT_YOURS: "Only the person who wrote this note, or an admin, can change it.",
  PERSON_DOCUMENT_DUPLICATE: "That file is already on this candidate.",
  PERSON_DOCUMENT_NOT_YOURS: "Only the person who uploaded this, or an admin, can remove it.",
  PERSON_DOCUMENT_LIMIT: "This candidate has reached the limit for documents.",
  CANDIDATE_TAG_EXISTS: "Your team already has that tag.",
  CANDIDATE_TAG_RETIRED: "That tag is retired. Restore it in Settings to use it again.",
  PERSON_OWNER_NOT_STAFF: "The owner must be someone on your team.",
  PERSON_DO_NOT_CONTACT: "This person is marked do not contact, so contact lookups are off.",
  // Two uploads raise this — the spreadsheet import and the position description — so the wording
  // stays neutral. Naming one screen's file types here misdescribes the other's refusal, and each
  // dropzone already states what it takes.
  UNSUPPORTED_FILE_TYPE: "That file type is not supported.",
  CUSTOM_COLUMN_NAME_TAKEN: "This position already has a column with that name. Map onto that column instead.",
  TRIAGE_COMPANY_NOT_EDITABLE:
    "This company came from the market export, so its details are not yours to edit.",
  CONTACT_LOOKUP_UNAVAILABLE: "Contact lookup isn't switched on for your workspace. Contact Uncava support to turn it on.",
  // Distinct from a failure on purpose: nothing was written, so the same button works once the
  // account is topped up.
  CONTACT_LOOKUP_NO_CREDITS: "Your workspace has used this period's contact lookup credits. Ask an admin to top them up.",
  CONTACT_LOOKUP_FAILED: "Contact lookup didn't respond — the provider may be busy. Try again in a minute.",
  CONTACT_LOOKUP_NO_PROFILE: "Add this person's LinkedIn profile URL first.",
  CONTACT_LIMIT_REACHED: "A profile holds ten email addresses and ten phone numbers at most.",
  CANDIDATE_PROFILE_URL_LOCKED:
    "This profile was captured from LinkedIn, so its URL is not editable.",
  EXECUTIVE_SOURCING_UNAVAILABLE: "Find executives isn't switched on for your workspace. Contact Uncava support to turn it on.",
  EXECUTIVE_SOURCING_TOO_MANY_COMPANIES: "Too many companies ticked. Find executives takes a limited batch — untick some and try again.",
  EXECUTIVE_SOURCING_IN_PROGRESS: "A Find executives run is already in progress for this position.",
  PEOPLE_SEARCH_UNAVAILABLE: "People search isn't switched on for your workspace. Contact Uncava support to turn it on.",
  // Nothing was bought, so the same button works once the account is topped up.
  PEOPLE_SEARCH_NO_CREDITS: "Your workspace has used this period's people search credits. Ask an admin to top them up.",
  PEOPLE_SEARCH_FAILED: "People search didn't respond — the provider may be busy. Try again in a minute.",
  PEOPLE_SEARCH_REJECTED: "People search couldn't run that filter. Loosen or change it and try again.",
  PEOPLE_SEARCH_EMPTY_FILTER: "Add at least one filter before searching.",
  PEOPLE_SEARCH_PERSON_UNKNOWN: "That person is no longer in the results. Search again and add them from there.",
  MAILBOX_UNAVAILABLE: "Outreach email isn't switched on for your workspace. Contact Uncava support to turn it on.",
  ZOOM_UNAVAILABLE: "Zoom isn't set up for your workspace. Ask an admin to set it up in Settings → Integrations.",
  ZOOM_NOT_CONNECTED: "Connect Zoom first.",
  ZOOM_RECONNECT_NEEDED: "Your Zoom account needs reconnecting.",
  ZOOM_CONNECT_CANCELLED: "Zoom was not connected.",
  ZOOM_CONNECT_FAILED: "Zoom could not be connected. Try again.",
  INTEGRATION_ENCRYPTION_UNAVAILABLE: "Your own app's keys can't be stored for your workspace yet. Use the shared app for now, or contact Uncava support.",
  INTEGRATION_SHARED_APP_UNAVAILABLE: "Uncava's shared app isn't available for this provider yet. Use your own app instead.",
  MAILBOX_PROVIDER_UNSUPPORTED: "That kind of mailbox can't be connected.",
  MAILBOX_NOT_CONNECTED: "Connect your mailbox first.",
  MAILBOX_RECONNECT_NEEDED: "Your mailbox needs reconnecting before it can send.",
  MAILBOX_CONNECT_EXPIRED: "That connection attempt expired. Start again.",
  MAILBOX_CONNECT_FAILED: "Your mailbox couldn't be connected. Try again.",
  MAILBOX_SEND_FAILED: "The email couldn't be sent. Try again in a moment.",
  OUTREACH_SEQUENCE_IN_USE: "People are on this sequence, so it can't be deleted.",
  OUTREACH_PERSON_SKIPPED: "Someone you chose can no longer be added. Go back and review the list.",
  OUTREACH_ALREADY_ENROLLED: "Someone you chose is already in a sequence on this position.",
  OUTREACH_ADDRESS_NOT_ON_FILE: "That address isn't on file for this person.",
  OUTREACH_NOT_RUNNING: "This sequence has already ended.",
  MEETING_DO_NOT_CONTACT: "This person is marked do not contact.",
  MEETING_SLOT_TAKEN: "That time is no longer free. Pick another.",
  MEETING_SLOT_INVALID: "That time can't be booked. Pick one from the grid.",
  MEETING_BOOK_FAILED: "The invite couldn't be sent. Check your calendar before trying again.",
  MEETING_CALENDAR_UNAVAILABLE: "Your calendar couldn't be read. Try again in a moment, or reconnect your mailbox if it keeps failing.",
  WORKSPACE_NOT_FOUND: "That workspace isn't available to you any more.",
  NOT_A_MEMBER: "That workspace isn't available to you any more.",
  NOT_FOUND: "That item no longer exists. Reload to see the latest.",
  TRIAGE_COMPANY_ALREADY_HELD: "This position already has a company with that name.",
  IMPORT_FILE_UNREADABLE: "That file couldn't be read as a table. Check it has a header row, then try again.",
  MAILBOX_CONNECT_CANCELLED: "Your mailbox was not connected.",
  OUTREACH_BOOKING_LINK_UNAVAILABLE:
    "Booking links aren't available for your mailbox. Remove the booking link from the emails to start this sequence.",
  OUTREACH_BOOKING_LINK_FAILED: "Your booking page couldn't be set up, so nothing was sent. Try again in a moment.",
  OAUTH_GRANT_NOT_FOUND: "That app is already disconnected. Reload to see the current list.",
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

/** What an act that first had to save the screen's edits says when that save was refused. */
export const UNSAVED_CHANGES_MESSAGE = "Couldn't save your last change. Check your connection and try again.";

/**
 * Codes whose server detail is itself the message: a fixed sentence the server opts in
 * (`ApiException.userFacing`) because it names a number or a reason no fixed line here could — a limit,
 * a count, which part of a file failed — or an auth sentence kept deliberately vague in one place.
 */
export const DETAIL_ALLOWED: ReadonlySet<ApiErrorCode> = new Set<ApiErrorCode>([
  "VALIDATION_FAILED",
  "CONFLICT",
  "BULK_ADD_SCOPE_TOO_LARGE",
  "FILE_TOO_LARGE",
  "IMPORT_TOO_MANY_ROWS",
  "CUSTOM_COLUMN_LIMIT_REACHED",
  "TEMPLATE_FILE_UNREADABLE",
  "POSITION_DOCUMENT_UNREADABLE",
  "INVALID_CREDENTIALS",
  "ACCOUNT_LOCKED",
  "EMAIL_ALREADY_REGISTERED",
  "EMAIL_UNDELIVERABLE",
  "EMAIL_DISPOSABLE",
  "TOKEN_INVALID",
  "TOKEN_EXPIRED",
  "INVITATION_INVALID",
  "INVITATION_EXPIRED",
  "REFRESH_TOKEN_INVALID",
  "REFRESH_TOKEN_REUSED",
  "CSRF_TOKEN_INVALID",
]);

const GENERIC_MESSAGE = "Something went wrong. Try again.";

/**
 * Wording for a failure: our copy for the code, the server's detail only for an allow-listed code, and
 * otherwise a line carrying the request's reference so support can find it in the logs.
 */
export function messageFor(error: unknown): string {
  if (error instanceof AutosaveFailedError) return UNSAVED_CHANGES_MESSAGE;
  if (!(error instanceof ApiRequestError)) return GENERIC_MESSAGE;
  const known = MESSAGES[error.code as ApiErrorCode];
  if (known) return known;
  if (DETAIL_ALLOWED.has(error.code as ApiErrorCode) && error.problem.detail) return error.problem.detail;
  return unexpectedFailureMessage(error.problem.correlationId);
}

function unexpectedFailureMessage(correlationId: string | undefined): string {
  if (!correlationId || correlationId === "none") return GENERIC_MESSAGE;
  return `Something went wrong on our side. Try again, or contact support and quote reference ${correlationId}.`;
}

/** The sentence for a code that arrived without a response, such as a redirect's `?error=`. */
export function messageForCode(code: string): string {
  return MESSAGES[code as ApiErrorCode] ?? GENERIC_MESSAGE;
}

/** The code of a failed request, if it was one — for switching on special-cased failures. */
export function codeOf(error: unknown): ApiErrorCode | null {
  return error instanceof ApiRequestError ? (error.code as ApiErrorCode) : null;
}
