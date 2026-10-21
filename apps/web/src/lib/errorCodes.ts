import type { CopyScreen } from "../copy/defineScreen";
import { ERROR_SCREENS, errorsGeneral } from "../copy/catalog/errors";
import { screenText } from "../copy/store";
import { ApiRequestError } from "./apiClient";
import { AutosaveFailedError } from "./useAutosave";

/**
 * The backend's ErrorCode enum, mirrored — the machine-readable identity every failure carries.
 * UI switches on these; `messageFor` is the one place user-facing wording is chosen, so the same failure
 * never reads differently on two screens. The words themselves are `copy/catalog/errors.ts`'s, which a super admin
 * edits in Settings → Screen text.
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
  | "COMPANY_NOTE_NOT_YOURS"
  | "PERSON_DOCUMENT_DUPLICATE"
  | "PERSON_DOCUMENT_NOT_YOURS"
  | "PERSON_DOCUMENT_LIMIT"
  | "CANDIDATE_TAG_EXISTS"
  | "CANDIDATE_TAG_RETIRED"
  | "CANDIDATE_ASSESSMENT_STALE"
  | "CANDIDATE_ASSESSMENT_NO_CRITERIA"
  | "CANDIDATE_ASSESSMENT_NOTHING_OPEN"
  | "AI_UNAVAILABLE"
  | "PIPELINE_STAGE_NOT_EMPTY"
  | "PIPELINE_STAGE_REQUIRED"
  | "PIPELINE_STATUS_REQUIRED"
  | "PIPELINE_LABEL_TAKEN"
  | "PIPELINE_STATUS_RETIRED"
  | "PIPELINE_ORDER_INVALID"
  | "PERSON_OWNER_NOT_STAFF"
  | "PERSON_DO_NOT_CONTACT"
  | "TASK_ASSIGNEE_NOT_ON_TEAM"
  | "TASK_NOT_YOURS"
  | "STRATEGY_SEARCH_NAME_TAKEN"
  | "ASSISTANT_UNAVAILABLE"
  | "ASSISTANT_BUSY"
  | "ASSISTANT_STILL_ANSWERING"
  | "ASSISTANT_PROPOSAL_ALREADY_ACCEPTED"
  | "FILE_TOO_LARGE"
  | "REQUEST_TOO_LARGE"
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
  | "INSUFFICIENT_CREDITS"
  | "FAIR_USE_REACHED"
  | "TRIAL_ENDED"
  | "BILLING_UNAVAILABLE"
  | "BILLING_PLAN_UNKNOWN"
  | "BILLING_PACK_UNKNOWN"
  | "SUBSCRIPTION_BILLED_BY_STRIPE"
  | "SEAT_LIMIT_REACHED"
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
  | "ARCHIVE_NAME_MISMATCH"
  | "CLIENT_HAS_LIVE_POSITIONS"
  | "CLIENT_ARCHIVED"
  | "CLIENT_NAME_ARCHIVED"
  | "PROJECT_ARCHIVED"
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
  | "INTERNAL_ERROR"
  | "TASK_PRESET_EXISTS"
  | "SCREEN_TEXT_STALE"
  | "SCREEN_TEXT_KEY_UNKNOWN"
  | "SCREEN_TEXT_NOTHING_TO_PUBLISH";

/** Which group of the catalogue words each code, so `messageFor` reads a super admin's edit before the default. */
const ERROR_SCREEN_OF: ReadonlyMap<string, CopyScreen> = new Map(
  ERROR_SCREENS.flatMap((screen) =>
    Object.keys(screen.entries)
      .filter((key) => /^[A-Z][A-Z0-9_]+$/.test(key))
      .map((code) => [code, screen] as const),
  ),
);

function wordingOf(code: string): string | null {
  const screen = ERROR_SCREEN_OF.get(code);
  return screen ? screenText(screen, code) : null;
}

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
export function unsavedChangesMessage(): string {
  return screenText(errorsGeneral, "unsavedChanges");
}

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
  "REQUEST_TOO_LARGE",
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

function genericMessage(): string {
  return screenText(errorsGeneral, "generic");
}

/**
 * Wording for a failure: our copy for the code, the server's detail only for an allow-listed code, and
 * otherwise a line carrying the request's reference so support can find it in the logs.
 */
export function messageFor(error: unknown): string {
  if (error instanceof AutosaveFailedError) return unsavedChangesMessage();
  if (!(error instanceof ApiRequestError)) return genericMessage();
  const known = wordingOf(error.code);
  if (known) return known;
  const namedField = firstFieldError(error);
  if (namedField) return namedField;
  if (DETAIL_ALLOWED.has(error.code as ApiErrorCode) && error.problem.detail) return error.problem.detail;
  return unexpectedFailureMessage(error.problem.correlationId);
}

/**
 * A refusal that named its field, worded for a screen that has no input to put it under: "Responsibilities:
 * At most 20". Without this, "One or more fields are invalid" points at nothing.
 */
function firstFieldError(error: ApiRequestError): string | null {
  if (error.code !== "VALIDATION_FAILED") return null;
  const [key, message] = Object.entries(error.fieldErrors)[0] ?? [];
  if (!key || !message) return null;
  return `${fieldLabelOf(key)}: ${message}`;
}

/** `steps[1].subject` → "Subject"; `positionTitle` → "Position title". */
export function fieldLabelOf(serverKey: string): string {
  const leaf = serverKey.replace(/\[\d+\]/g, "").split(".").pop() ?? serverKey;
  const words = leaf.replace(/([a-z0-9])([A-Z])/g, "$1 $2").toLowerCase();
  return words.charAt(0).toUpperCase() + words.slice(1);
}

function unexpectedFailureMessage(correlationId: string | undefined): string {
  if (!correlationId || correlationId === "none") return genericMessage();
  return screenText(errorsGeneral, "unexpected", { reference: correlationId });
}

/** The sentence for a code that arrived without a response, such as a redirect's `?error=`. */
export function messageForCode(code: string): string {
  return wordingOf(code) ?? genericMessage();
}

/** The code of a failed request, if it was one — for switching on special-cased failures. */
export function codeOf(error: unknown): ApiErrorCode | null {
  return error instanceof ApiRequestError ? (error.code as ApiErrorCode) : null;
}
