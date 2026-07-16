package app.lightmove.api.core.error.constant;
import app.lightmove.api.workspace.model.Workspace;

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

    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT, "An account with this email already exists"),
    EMAIL_UNDELIVERABLE(HttpStatus.BAD_REQUEST, "This email address does not appear to exist"),
    EMAIL_DISPOSABLE(HttpStatus.BAD_REQUEST, "Please use your work email address"),

    /** A consumer provider (gmail, outlook…). The domain must name a company — it is the organisation. */
    EMAIL_NOT_WORK_ADDRESS(HttpStatus.BAD_REQUEST,
            "Please sign up with your work email. LightMove is for search firms, and your email domain identifies your organization"),

    /** The user already has an active workspace. One at a time. */
    ALREADY_IN_WORKSPACE(HttpStatus.CONFLICT, "You already belong to a workspace"),

    /** They have asked to join a workspace and an admin has not answered yet. */
    JOIN_REQUEST_PENDING(HttpStatus.CONFLICT,
            "Your request to join is waiting for an administrator to approve it"),

    JOIN_REQUEST_REJECTED(HttpStatus.FORBIDDEN,
            "Your request to join this workspace was declined. Ask an administrator to invite you"),

    /** Asking to join a workspace whose domain is not the requester's. */
    JOIN_DOMAIN_MISMATCH(HttpStatus.FORBIDDEN,
            "You can only ask to join a workspace on your own email domain"),

    TOKEN_INVALID(HttpStatus.BAD_REQUEST, "This link is not valid"),
    TOKEN_EXPIRED(HttpStatus.BAD_REQUEST, "This link has expired"),

    REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "Your session has ended. Please sign in again"),
    /** Reuse of a rotated token. The family is already dead by the time this reaches the client. */
    REFRESH_TOKEN_REUSED(HttpStatus.UNAUTHORIZED, "Your session was ended for security reasons. Please sign in again"),

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
