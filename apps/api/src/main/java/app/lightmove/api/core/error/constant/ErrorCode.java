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

    /**
     * A hand-typed add names someone the workspace already holds at that employer, by name alone. The
     * body's {@code personIds} are who; the drawer asks, then resends naming one or adding a new person.
     */
    CANDIDATE_POSSIBLE_DUPLICATE(HttpStatus.CONFLICT,
            "Your team already has someone with that name at that employer"),

    /** The dialog named one person, but the LinkedIn profile or email typed is another's. */
    CANDIDATE_KEYS_NAME_ANOTHER(HttpStatus.CONFLICT,
            "The LinkedIn profile or email typed belongs to someone else in your candidates"),

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

    MCP_REQUEST_TOO_LARGE(HttpStatus.PAYLOAD_TOO_LARGE, "That request is too large"),

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
            "This position has as many custom columns as it can hold"),

    /** A template save carrying an older version than the row's: someone else saved it first. */
    TEMPLATE_STALE(HttpStatus.CONFLICT,
            "Someone saved this template after you opened it. Reload to see their version"),

    /** The template an unrecognised role title is drafted from; without it a mandate starts blank. */
    TEMPLATE_FALLBACK_REQUIRED(HttpStatus.CONFLICT,
            "The fallback template cannot be archived or hidden"),

    TEMPLATE_FILE_UNREADABLE(HttpStatus.BAD_REQUEST,
            "That file isn't an Uncava template file. Export templates from Uncava and import that file"),

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

    /** Another person in the workspace is that LinkedIn profile; the mandate may map them instead. */
    PERSON_PROFILE_HELD(HttpStatus.CONFLICT,
            "Another candidate in this workspace already has that LinkedIn profile"),

    /** A note may be changed or removed by its author, or by a workspace admin. */
    PERSON_NOTE_NOT_YOURS(HttpStatus.FORBIDDEN, "Only the person who wrote this note, or an admin, can change it"),

    /** The same file is already on this person; the body names the document and version holding it. */
    PERSON_DOCUMENT_DUPLICATE(HttpStatus.CONFLICT, "That file is already on this candidate"),

    /** A document or version may be removed by whoever uploaded it, or by a workspace admin. */
    PERSON_DOCUMENT_NOT_YOURS(HttpStatus.FORBIDDEN, "Only the person who uploaded this, or an admin, can remove it"),

    /** Past lightmove.person-documents' ceilings on documents per person or versions per document. */
    PERSON_DOCUMENT_LIMIT(HttpStatus.CONFLICT, "This candidate has reached the limit for documents"),

    /** Tags are unique per workspace whatever their case. */
    CANDIDATE_TAG_EXISTS(HttpStatus.CONFLICT, "Your team already has that tag"),

    /** A retired tag stays on the people who hold it, but can no longer be put on anyone. */
    CANDIDATE_TAG_RETIRED(HttpStatus.CONFLICT, "That tag is retired. Restore it in Settings to use it again"),

    /** A person's owner is a colleague: an active member who is not a client representative. */
    PERSON_OWNER_NOT_STAFF(HttpStatus.BAD_REQUEST, "The owner must be someone on your team"),

    /** Contact lookups are off for a person marked do not contact; nothing was spent. */
    PERSON_DO_NOT_CONTACT(HttpStatus.CONFLICT, "This person is marked do not contact, so contact lookups are off"),

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

    /** Nothing is spent: the deployment has no people-search provider configured. */
    EXECUTIVE_SOURCING_UNAVAILABLE(HttpStatus.CONFLICT,
            "Find executives is not set up on this deployment"),

    /** Nothing is written: the caller ticks fewer companies; the cap is on the sourcing config read. */
    EXECUTIVE_SOURCING_TOO_MANY_COMPANIES(HttpStatus.BAD_REQUEST,
            "That is more companies than one run may take"),

    /** One run at a time per mandate, so a second tab cannot double-spend. */
    EXECUTIVE_SOURCING_IN_PROGRESS(HttpStatus.CONFLICT,
            "A Find executives run is already in progress for this mandate"),

    /** No ContactOut key, or it refuses ours: an operator's problem either way, so they read the same. */
    PEOPLE_SEARCH_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE,
            "People search is not available on this deployment"),

    /** CONTACT_LOOKUP_NO_CREDITS' reason: a spent search quota will not refill shortly. */
    PEOPLE_SEARCH_NO_CREDITS(HttpStatus.CONFLICT,
            "People search has no credits left this period"),

    PEOPLE_SEARCH_FAILED(HttpStatus.BAD_GATEWAY,
            "People search did not answer. Try again in a moment"),

    /** ContactOut refused the question itself; the filter's own checks exist so this is never reached. */
    PEOPLE_SEARCH_REJECTED(HttpStatus.BAD_REQUEST,
            "People search could not run that filter. Loosen or change it and try again"),

    /** A search would answer ContactOut's whole index; nothing is spent on it. */
    PEOPLE_SEARCH_EMPTY_FILTER(HttpStatus.BAD_REQUEST,
            "Add at least one filter before searching"),

    /** Only a person a search on this mandate returned can be added from it; nothing is bought to add one. */
    PEOPLE_SEARCH_PERSON_UNKNOWN(HttpStatus.CONFLICT,
            "That person is no longer in the search results. Search again and add them from there"),

    /** No mail service is configured, so no mailbox can be connected or send. */
    MAILBOX_UNAVAILABLE(HttpStatus.CONFLICT, "Outreach email is not set up on this deployment"),

    /** Only the mailbox hosts the deployment lists may be connected. */
    MAILBOX_PROVIDER_UNSUPPORTED(HttpStatus.BAD_REQUEST, "That kind of mailbox cannot be connected"),

    MAILBOX_NOT_CONNECTED(HttpStatus.CONFLICT, "Connect your mailbox first"),

    /** The provider withdrew access (a password change, an admin removing the app); nothing was sent. */
    MAILBOX_RECONNECT_NEEDED(HttpStatus.CONFLICT, "Your mailbox needs reconnecting before it can send"),

    /**
     * The consent screen came back with no matching attempt: too late, already used, or in a browser that
     * never started it. A link handed to someone else ends here and connects nothing.
     */
    MAILBOX_CONNECT_EXPIRED(HttpStatus.BAD_REQUEST, "That connection attempt has expired. Start again"),

    /** The consultant backed out on the provider's screen; the SPA says nothing. */
    MAILBOX_CONNECT_CANCELLED(HttpStatus.BAD_REQUEST, "The mailbox was not connected"),

    MAILBOX_CONNECT_FAILED(HttpStatus.BAD_GATEWAY, "Your mailbox could not be connected. Try again"),

    /** Never retried on our side, so nothing went twice; trying again may still find the first one delivered. */
    MAILBOX_SEND_FAILED(HttpStatus.BAD_GATEWAY, "The email could not be sent. Try again in a moment"),

    /** No Zoom app resolves for the workspace — neither Uncava's nor its own — so Zoom cannot be connected. */
    ZOOM_UNAVAILABLE(HttpStatus.CONFLICT, "Zoom is not set up for your workspace"),

    ZOOM_NOT_CONNECTED(HttpStatus.CONFLICT, "Connect Zoom first"),

    /** Zoom refused the stored token; nothing was made, and only reconnecting helps. */
    ZOOM_RECONNECT_NEEDED(HttpStatus.CONFLICT, "Your Zoom account needs reconnecting"),

    /** The consultant backed out on Zoom's screen; the SPA says nothing. */
    ZOOM_CONNECT_CANCELLED(HttpStatus.BAD_REQUEST, "Zoom was not connected"),

    ZOOM_CONNECT_FAILED(HttpStatus.BAD_GATEWAY, "Zoom could not be connected. Try again"),

    /** A sequence people are on keeps their history: it can be edited, never deleted. */
    OUTREACH_SEQUENCE_IN_USE(HttpStatus.CONFLICT, "People are on this sequence, so it cannot be deleted"),

    /**
     * Someone chosen may not be approached — no email, do not contact, out of the running or already in a
     * sequence — and nobody was enrolled. The dialog shows each reason; this is the server holding the line.
     */
    OUTREACH_PERSON_SKIPPED(HttpStatus.CONFLICT, "Someone you chose can no longer be added. Review the list again"),

    /** One live sequence per person per position; a racing start lost to another. */
    OUTREACH_ALREADY_ENROLLED(HttpStatus.CONFLICT, "Someone you chose is already in a sequence on this position"),

    /** The To address must be one the person's contact ledger holds. */
    OUTREACH_ADDRESS_NOT_ON_FILE(HttpStatus.BAD_REQUEST, "That address is not on file for this person"),

    /** Only a sequence still due to send can be stopped; one that ended is already the record. */
    OUTREACH_NOT_RUNNING(HttpStatus.CONFLICT, "This sequence has already ended"),

    /** Nobody marked do not contact is invited to anything. */
    MEETING_DO_NOT_CONTACT(HttpStatus.CONFLICT, "This person is marked do not contact"),

    /** The slot was free when offered and is not now; the dialog offers the times again. */
    MEETING_SLOT_TAKEN(HttpStatus.CONFLICT, "That time is no longer free. Pick another"),

    /** The length, the time or the video link asked for is not one the dialog offers. */
    MEETING_SLOT_INVALID(HttpStatus.BAD_REQUEST, "That time cannot be booked"),

    /** The consultant's calendar could not be read, so no time was offered and no invite was sent. */
    MEETING_CALENDAR_UNAVAILABLE(HttpStatus.BAD_GATEWAY, "Your calendar couldn't be read. Try again"),

    /** Never retried on our side: the calendar may still hold the invite, so the consultant checks before trying again. */
    MEETING_BOOK_FAILED(HttpStatus.BAD_GATEWAY, "The invite could not be sent. Check your calendar before trying again"),

    /** A sequence uses {{bookingLink}} where the mail service's plan offers no booking pages. */
    OUTREACH_BOOKING_LINK_UNAVAILABLE(HttpStatus.CONFLICT, "Booking links are not set up on this deployment"),

    /** The booking page behind the link could not be made; nothing was started or sent. */
    OUTREACH_BOOKING_LINK_FAILED(HttpStatus.BAD_GATEWAY, "Your booking page couldn't be set up. Try again"),

    /** No credential encryption key is configured, so a workspace's own app keys cannot be stored or read. */
    INTEGRATION_ENCRYPTION_UNAVAILABLE(HttpStatus.CONFLICT,
            "Your own app's keys cannot be stored on this deployment. Use the shared app"),

    /** An approval for Uncava's shared app on a deployment that offers none at that provider. */
    INTEGRATION_SHARED_APP_UNAVAILABLE(HttpStatus.CONFLICT, "Uncava's shared app is not offered on this deployment"),

    /** A webhook delivery whose signature did not verify, or one this deployment is not set up to read. */
    MAILBOX_WEBHOOK_REJECTED(HttpStatus.UNAUTHORIZED, "Unauthorized"),

    /** A key id that is not in the caller's workspace, or not one the caller may see. */
    API_KEY_NOT_FOUND(HttpStatus.NOT_FOUND, "That API key does not exist"),

    /** The caller already holds {@code lightmove.public-api.max-active-keys-per-user} live personal keys. */
    API_KEY_LIMIT_REACHED(HttpStatus.CONFLICT, "You have the most API keys allowed. Revoke one you no longer use"),

    /** A key asking for {@code mcp:use} alone, which would reach the MCP server and read nothing there. */
    API_KEY_READS_NOTHING(HttpStatus.BAD_REQUEST, "Choose what the key may read besides MCP access"),

    /** Every refusal of a public API key — missing, malformed, unknown, revoked, expired or its owner's access gone — alike. */
    API_KEY_INVALID(HttpStatus.UNAUTHORIZED, "The API key is missing, invalid or no longer active"),

    MCP_CREDENTIAL_INVALID(HttpStatus.UNAUTHORIZED,
            "The access token or API key is missing, invalid or no longer active"),

    /** An OAuth token calling an MCP tool it lacks a scope for; {@code WWW-Authenticate} names the scopes to ask for. */
    MCP_SCOPE_INSUFFICIENT(HttpStatus.FORBIDDEN, "This connection was not granted the access this tool needs"),

    /** A live key asking a route outside its scopes; the body names the scope as {@code requiredScope}. */
    API_KEY_SCOPE_MISSING(HttpStatus.FORBIDDEN, "This API key does not carry the scope this request needs"),

    /** A universe read past {@code lightmove.export.*}: refused, never truncated, as the export is. */
    PUBLIC_API_UNIVERSE_TOO_LARGE(HttpStatus.BAD_REQUEST,
            "This stage is too large to read in one call. Page through the companies and candidates routes instead"),

    /** A grant id that is not in the caller's workspace, or not one the caller may see. */
    OAUTH_GRANT_NOT_FOUND(HttpStatus.NOT_FOUND, "That connection does not exist"),

    /** A client id or redirect the authorization server does not know, asked for on the consent screen. */
    OAUTH_CLIENT_NOT_FOUND(HttpStatus.NOT_FOUND, "That app is not registered with Uncava"),

    /** A consent read for an authorization request that is not the caller's, has ended, or never existed. */
    OAUTH_REQUEST_NOT_FOUND(HttpStatus.NOT_FOUND, "That connection request has expired. Start again from the app"),

    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Something went wrong on our end");

    private final HttpStatus status;
    private final String defaultMessage;
}
