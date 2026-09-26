package app.lightmove.api.core.error.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;
import org.springframework.http.HttpStatus;

/** Every failure the API can report, as a stable code the frontend switches on — never on the message. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "One or more fields are invalid"),

    /**
     * The single answer to wrong password, no such account and provider-only account: distinguishing
     * them is an account-enumeration oracle. The audit log records which case it was.
     */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),

    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Too many failed attempts. Try again later"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "This account has been suspended"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Please verify your email address to continue"),

    /** Travels as {@code ?error=} on the redirect to the login screen, never in a response body. */
    OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "Sign-in did not complete. Please try again"),

    /** Declined at the consent screen; kept apart from {@link #OAUTH_FAILED} so the SPA shows nothing. */
    OAUTH_CANCELLED(HttpStatus.UNAUTHORIZED, "Sign-in was cancelled"),

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this email already exists"),
    EMAIL_UNDELIVERABLE(HttpStatus.BAD_REQUEST, "This email address does not appear to exist"),
    EMAIL_DISPOSABLE(HttpStatus.BAD_REQUEST, "Please use your work email address"),

    /** A consumer provider (gmail, outlook…). The domain must name a company — it is the organisation. */
    EMAIL_NOT_WORK_ADDRESS(HttpStatus.BAD_REQUEST,
            "Please sign up with your work email. Uncava is for search firms, and your email domain identifies your organization"),

    ALREADY_IN_WORKSPACE(HttpStatus.CONFLICT, "You already belong to a workspace"),

    TOKEN_INVALID(HttpStatus.BAD_REQUEST, "This link is not valid"),
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "This link has expired"),

    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Your session has ended. Please sign in again"),
    /** Reuse of a rotated token. The family is already dead by the time this reaches the client. */
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Your session was ended for security reasons. Please sign in again"),

    /** Plain where {@link #INVALID_CREDENTIALS} is vague: the caller is already the authenticated owner. */
    CURRENT_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "That is not your current password"),

    /** Provider-only account: attaching a password is the reset flow's job, which proves the mailbox first. */
    PASSWORD_NOT_SET(HttpStatus.CONFLICT, "This account signs in with a provider and has no password to change"),

    /** Also served for a session belonging to somebody else — a 403 would confirm the id names a real one. */
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "That session is no longer active"),

    CURRENT_SESSION_NOT_REVOCABLE(HttpStatus.CONFLICT, "Use sign out to end the session you are using"),

    WORKSPACE_ALREADY_EXISTS(HttpStatus.CONFLICT, "You have already created a workspace"),
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "Workspace not found"),

    /** Also served for a workspace that does not exist: a 403 would confirm it is real. */
    NOT_A_MEMBER(HttpStatus.NOT_FOUND, "Workspace not found"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to do this"),

    INVITATION_INVALID(HttpStatus.BAD_REQUEST, "This invitation is not valid"),
    INVITATION_EXPIRED(HttpStatus.BAD_REQUEST, "This invitation has expired"),

    LAST_ADMIN(HttpStatus.CONFLICT, "A workspace must keep at least one admin"),

    MEMBER_LEADS_PROJECTS(HttpStatus.CONFLICT,
            "This member is the only lead on active projects. Hand those over first"),

    CLIENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "A client with this name already exists"),

    PROJECT_LAST_LEAD(HttpStatus.CONFLICT, "A project must keep at least one lead"),

    /** Nothing is written: the caller narrows the filter and tries again. */
    BULK_ADD_SCOPE_TOO_LARGE(HttpStatus.CONFLICT,
            "This filter matches more companies than one bulk add may take"),

    /** Distinct from CONFLICT so the screen can name the company rather than offer a futile retry. */
    TRIAGE_COMPANY_ALREADY_HELD(HttpStatus.CONFLICT,
            "This mandate already holds a company with that name"),

    /** A market company's fields are the export's snapshot; editing them would make the Source badge lie. */
    TRIAGE_COMPANY_NOT_EDITABLE(HttpStatus.CONFLICT,
            "A company taken from the market cannot be edited"),

    /**
     * Same name at the same company (or anywhere, for an employer outside the universe), or a LinkedIn
     * profile already mapped. Distinct from CONFLICT so the drawer can mark the name field.
     */
    CANDIDATE_ALREADY_MAPPED(HttpStatus.CONFLICT,
            "This mandate already maps someone with that name"),

    STRATEGY_SEARCH_NAME_TAKEN(HttpStatus.CONFLICT,
            "A search with that name is already saved here"),

    WORKSPACE_NAME_MISMATCH(HttpStatus.BAD_REQUEST,
            "Type the workspace name exactly to confirm deletion"),

    /** A database constraint fired ahead of its service-level pre-check — two requests raced. */
    CONFLICT(HttpStatus.CONFLICT, "That conflicts with something that already exists. Try again"),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down"),

    /** Distinct from FORBIDDEN: the SPA recovers by re-fetching {@code /auth/csrf} and retrying. */
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "Your session needs refreshing. Please try again"),

    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),

    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "That method is not supported on this endpoint"),

    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "That content type is not supported"),

    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "No representation matches what you asked to accept"),

    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "That file is too large"),

    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "That file type is not supported"),

    /** The file type was right but the contents are not a table: no header row, corrupt, ragged rows. */
    IMPORT_FILE_UNREADABLE(HttpStatus.BAD_REQUEST,
            "That file could not be read as a table. Check it has a header row."),

    /** Refused whole rather than truncated, which would silently drop part of the list. */
    IMPORT_TOO_MANY_ROWS(HttpStatus.PAYLOAD_TOO_LARGE,
            "That file has more rows than one import can take"),

    CUSTOM_COLUMN_NAME_TAKEN(HttpStatus.CONFLICT,
            "This mandate already has a column with that name"),

    CUSTOM_COLUMN_LIMIT_REACHED(HttpStatus.CONFLICT,
            "This mandate has as many custom columns as it can hold"),

    /** A template save carrying an older version than the row's: someone else saved it first. */
    TEMPLATE_STALE(HttpStatus.CONFLICT,
            "Someone saved this template after you opened it. Reload to see their version"),

    /** The template an unrecognised role title is drafted from; without it a mandate starts blank. */
    TEMPLATE_FALLBACK_REQUIRED(HttpStatus.CONFLICT,
            "The fallback template cannot be archived or hidden"),

    TEMPLATE_FILE_UNREADABLE(HttpStatus.BAD_REQUEST,
            "That file is not a LightMove template file"),

    /** All or nothing: no template in the file is written. */
    TEMPLATE_IMPORT_INVALID(HttpStatus.BAD_REQUEST,
            "Some templates in that file are invalid, so none were imported"),

    /** Encrypted, no text layer, a legacy {@code .doc}, or an unknown format. */
    POSITION_DOCUMENT_UNREADABLE(HttpStatus.BAD_REQUEST,
            "That document could not be read. Save it as .docx or PDF, with a text layer, and try again."),

    /** No provider configured, or it refuses our key: both an operator's problem, so they read the same. */
    CONTACT_LOOKUP_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "Contact lookup is not available on this deployment"),

    /** Not SERVICE_UNAVAILABLE, which reads as "try again shortly": a monthly quota will not refill shortly. */
    CONTACT_LOOKUP_NO_CREDITS(HttpStatus.CONFLICT,
            "Contact lookup has no credits left this period"),

    CONTACT_LOOKUP_FAILED(HttpStatus.BAD_GATEWAY,
            "Contact lookup did not answer. Try again in a moment"),

    /** Contacts are keyed on a LinkedIn profile; refused before anything is spent. */
    CONTACT_LOOKUP_NO_PROFILE(HttpStatus.CONFLICT,
            "Add this person's LinkedIn profile URL first"),

    CONTACT_LIMIT_REACHED(HttpStatus.CONFLICT,
            "A profile holds ten email addresses and ten phone numbers at most"),

    /** A plugin capture's URL is the page it was read off, and research and contact lookup key on it. */
    CANDIDATE_PROFILE_URL_LOCKED(HttpStatus.CONFLICT,
            "This profile was captured from LinkedIn; its URL is not editable"),

    /** Nothing was saved. Never sent for a stream that ran out of time: see {@link #ASSISTANT_STILL_ANSWERING}. */
    ASSISTANT_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "The assistant could not answer just now. Try again in a moment"),

    /** Refused before anything is asked or billed. */
    ASSISTANT_BUSY(HttpStatus.SERVICE_UNAVAILABLE,
            "The assistant is busy answering other questions. Try again in a moment"),

    /** The stream closed first; the answer is saved to the chat when ready, so asking again pays twice. */
    ASSISTANT_STILL_ANSWERING(HttpStatus.ACCEPTED,
            "This is taking longer than usual. The answer will appear in this chat when it is ready"),

    /** A conflict rather than a quiet re-run, whose "added 0" would read as a failure. */
    ASSISTANT_PROPOSAL_ALREADY_ACCEPTED(HttpStatus.CONFLICT,
            "This proposal has already been filed"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our end");

    private final HttpStatus status;
    private final String defaultMessage;
}
