package app.lightmove.api.core.error.constant;

import org.springframework.http.HttpStatus;

/**
 * Every failure the API can report, as a stable machine-readable code.
 *
 * <p>The frontend switches on these, not on the human-readable message — so wording can change
 * without breaking a client.
 */
public enum ErrorCode {

    VALIDATION_FAILED(HttpStatus.BAD_REQUEST, "One or more fields are invalid"),

    /**
     * Deliberately the single answer to "wrong password", "no such account", and "that address is a
     * Google-only account". Distinguishing them hands an attacker a free account-enumeration oracle:
     * they could harvest which of a leaked email list are real customers without ever guessing a
     * password. The audit log records precisely which case it was; the client is told only that the
     * pair did not match.
     */
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),

    ACCOUNT_LOCKED(HttpStatus.LOCKED, "Too many failed attempts. Try again later"),
    ACCOUNT_SUSPENDED(HttpStatus.FORBIDDEN, "This account has been suspended"),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "Please verify your email address to continue"),

    /**
     * The OAuth exchange itself failed — the provider refused, or its answer did not verify. It never
     * reaches an API response body: the flow is a browser redirect, so this travels as {@code ?error=}
     * on the way back to the login screen. The provider's own message (a {@code redirect_uri} that
     * does not match, an {@code invalid_client}) is logged instead, being configuration detail rather
     * than anything the person signing in can act on.
     */
    OAUTH_FAILED(HttpStatus.UNAUTHORIZED, "Sign-in did not complete. Please try again"),

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this email already exists"),
    EMAIL_UNDELIVERABLE(HttpStatus.BAD_REQUEST, "This email address does not appear to exist"),
    EMAIL_DISPOSABLE(HttpStatus.BAD_REQUEST, "Please use your work email address"),

    /** A consumer provider (gmail, outlook…). The domain must name a company — it is the organisation. */
    EMAIL_NOT_WORK_ADDRESS(HttpStatus.BAD_REQUEST,
            "Please sign up with your work email. LightMove is for search firms, and your email domain identifies your organization"),

    /** The user already has an active workspace. One at a time. */
    ALREADY_IN_WORKSPACE(HttpStatus.CONFLICT, "You already belong to a workspace"),

    TOKEN_INVALID(HttpStatus.BAD_REQUEST, "This link is not valid"),
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "This link has expired"),

    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Your session has ended. Please sign in again"),
    /** Reuse of a rotated token. The family is already dead by the time this reaches the client. */
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Your session was ended for security reasons. Please sign in again"),

    /**
     * Named plainly where {@link #INVALID_CREDENTIALS} is deliberately vague: this endpoint is already
     * authenticated as the account's owner, so there is no enumeration oracle left to protect.
     */
    CURRENT_PASSWORD_INVALID(HttpStatus.BAD_REQUEST, "That is not your current password"),

    /** Provider-only account. Attaching a local password is the reset flow's job — it proves the mailbox first. */
    PASSWORD_NOT_SET(HttpStatus.CONFLICT, "This account signs in with a provider and has no password to change"),

    /** Also served for a session belonging to somebody else — a 403 would confirm the id names a real one. */
    SESSION_NOT_FOUND(HttpStatus.NOT_FOUND, "That session is no longer active"),

    CURRENT_SESSION_NOT_REVOCABLE(HttpStatus.CONFLICT, "Use sign out to end the session you are using"),

    WORKSPACE_ALREADY_EXISTS(HttpStatus.CONFLICT, "You have already created a workspace"),
    WORKSPACE_NOT_FOUND(HttpStatus.NOT_FOUND, "Workspace not found"),

    /**
     * Served for "you are not a member of that workspace" as well as "that workspace does not exist".
     * Same reasoning as INVALID_CREDENTIALS: a 403 would confirm the workspace is real.
     */
    NOT_A_MEMBER(HttpStatus.NOT_FOUND, "Workspace not found"),
    FORBIDDEN(HttpStatus.FORBIDDEN, "You do not have permission to do this"),

    INVITATION_INVALID(HttpStatus.BAD_REQUEST, "This invitation is not valid"),
    INVITATION_EXPIRED(HttpStatus.BAD_REQUEST, "This invitation has expired"),

    /** A workspace must always keep someone who can run it. */
    LAST_ADMIN(HttpStatus.CONFLICT, "A workspace must keep at least one admin"),

    MEMBER_LEADS_PROJECTS(HttpStatus.CONFLICT,
            "This member is the only lead on active projects. Hand those over first"),

    CLIENT_ALREADY_EXISTS(HttpStatus.CONFLICT, "A client with this name already exists"),

    /** A project must always keep someone who can run it — the mirror of {@link #LAST_ADMIN}. */
    PROJECT_LAST_LEAD(HttpStatus.CONFLICT, "A project must keep at least one lead"),

    /**
     * "Add all to Universe" against a filter matching more companies than one bulk add may take.
     * Nothing is written: the caller narrows the filter and tries again.
     */
    BULK_ADD_SCOPE_TOO_LARGE(HttpStatus.CONFLICT,
            "This filter matches more companies than one bulk add may take"),

    /**
     * A capture naming a company this mandate already holds. Distinct from CONFLICT so the Companies
     * screen can say which company, and point at the stage it is already sitting in, rather than
     * offering "try again" for something retrying will never fix.
     */
    TRIAGE_COMPANY_ALREADY_HELD(HttpStatus.CONFLICT,
            "This mandate already holds a company with that name"),

    /**
     * An edit aimed at a company taken from the market. Its fields are the export's snapshot, not the
     * mandate's, so rewriting them would make the Source badge a lie about where the figures came
     * from. Distinct from FORBIDDEN because nothing about the caller is wrong — the company is.
     */
    TRIAGE_COMPANY_NOT_EDITABLE(HttpStatus.CONFLICT,
            "A company taken from the market cannot be edited"),

    /**
     * An executive already mapped under that name — at the same company, or, for someone whose
     * employer is not in the universe, anywhere in the mandate. Distinct from CONFLICT so the drawer
     * can mark the name field rather than offering "try again" for something retrying will never fix.
     */
    CANDIDATE_ALREADY_MAPPED(HttpStatus.CONFLICT,
            "This mandate already maps someone with that name"),

    /**
     * A saved search reusing a name already taken in the same list. Distinct from CONFLICT so the
     * Strategy dropdown can say what is wrong with the name rather than offering "try again" for
     * something retrying will never fix.
     */
    STRATEGY_SEARCH_NAME_TAKEN(HttpStatus.CONFLICT,
            "A search with that name is already saved here"),

    /** The typed confirmation on workspace deletion did not match. */
    WORKSPACE_NAME_MISMATCH(HttpStatus.BAD_REQUEST,
            "Type the workspace name exactly to confirm deletion"),

    /** A database constraint fired ahead of its service-level pre-check — two requests raced. */
    CONFLICT(HttpStatus.CONFLICT, "That conflicts with something that already exists. Try again"),

    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS, "Too many requests. Please slow down"),

    /**
     * The CSRF token was missing or did not match. Distinct from FORBIDDEN on purpose: the SPA recovers
     * from this by re-fetching {@code /auth/csrf} and retrying, and it cannot recover from "you lack
     * permission". Reporting them as the same thing turns a self-healing case into a dead end.
     */
    CSRF_TOKEN_INVALID(HttpStatus.FORBIDDEN, "Your session needs refreshing. Please try again"),

    /** No route and no file at that path. Says nothing about what does exist. */
    NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),

    /** The route exists; it does not answer to that verb. A GET of a POST-only endpoint lands here. */
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "That method is not supported on this endpoint"),

    /** The body arrived in a format the endpoint does not read. Every endpoint here wants JSON. */
    UNSUPPORTED_MEDIA_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "That content type is not supported"),

    /** The caller's Accept header asks for a format we do not produce. */
    NOT_ACCEPTABLE(HttpStatus.NOT_ACCEPTABLE, "No representation matches what you asked to accept"),

    /** An upload exceeded the ceiling — the caller is told the limit, so the message names it. */
    FILE_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "That file is too large"),

    /** An upload arrived as a document type this endpoint does not accept. */
    UNSUPPORTED_FILE_TYPE(HttpStatus.BAD_REQUEST, "That file type is not supported"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our end");

    private final HttpStatus status;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String defaultMessage) {
        this.status = status;
        this.defaultMessage = defaultMessage;
    }

    public HttpStatus status() {
        return status;
    }

    public String defaultMessage() {
        return defaultMessage;
    }
}
