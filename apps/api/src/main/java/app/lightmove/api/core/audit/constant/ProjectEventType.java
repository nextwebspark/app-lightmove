package app.lightmove.api.core.audit.constant;

/**
 * Project-domain audit events: the mandate, its positions, and its strategy. See
 * {@link AuditEventType} for why the ledger's event set is split this way.
 */
public enum ProjectEventType implements AuditEventType {

    PROJECT_CREATED,
    PROJECT_UPDATED,
    PROJECT_TEAM_CHANGED,

    CLIENT_CREATED,
    CLIENT_UPDATED,
    CLIENT_REP_INVITED,
    CLIENT_REP_ACCEPTED,

    POSITION_UPDATED,
    POSITION_TEMPLATE_APPLIED,
    POSITION_PUBLISHED,
    POSITION_PUBLICATION_WITHDRAWN,
    POSITION_DOCUMENT_ATTACHED,
    POSITION_DOCUMENT_REMOVED,
    POSITION_DOCUMENT_EXTRACTED,

    STRATEGY_UPDATED,
    STRATEGY_SEARCH_SAVED,
    STRATEGY_SEARCH_RENAMED,
    STRATEGY_SEARCH_FILTER_UPDATED,
    STRATEGY_SEARCH_VISIBILITY_CHANGED,
    STRATEGY_SEARCH_DELETED,

    TRIAGE_COMPANY_ADDED,
    TRIAGE_COMPANY_CAPTURED,
    TRIAGE_COMPANY_MOVED,
    TRIAGE_COMPANY_EDITED,
    TRIAGE_COMPANY_REMOVED,
    TRIAGE_BULK_ADDED,

    CANDIDATE_ADDED,
    CANDIDATE_UPDATED,
    CANDIDATE_REMOVED,
    CANDIDATE_CONTACT_LOOKED_UP,
    CANDIDATE_AI_ENRICH_REQUESTED,
    PERSON_NOTE_ADDED,
    PERSON_NOTE_EDITED,
    PERSON_NOTE_REMOVED,
    PERSON_NOTE_PINNED,
    PERSON_OWNER_CHANGED,
    PERSON_TAGGED,
    PERSON_UNTAGGED,
    PERSON_DO_NOT_CONTACT_SET,
    PERSON_DO_NOT_CONTACT_CLEARED,
    PEOPLE_MAPPED_FROM_POOL,
    CANDIDATE_TAG_CREATED,
    CANDIDATE_TAG_UPDATED,
    EXECUTIVE_SOURCING_REQUESTED,
    EXECUTIVE_SOURCING_COMPLETED,

    /** A page of Strategy's people search, recorded because a page not already cached is bought. */
    PEOPLE_SEARCH_PAGE_FETCHED,

    CUSTOM_COLUMN_DEFINED,
    CUSTOM_COLUMN_UPDATED,
    CUSTOM_COLUMN_REORDERED,
    CUSTOM_COLUMN_REMOVED,

    SPREADSHEET_IMPORTED,

    COMPANIES_EXPORTED,
    CANDIDATES_EXPORTED,

    OUTREACH_SEQUENCE_CREATED,
    OUTREACH_SEQUENCE_UPDATED,
    OUTREACH_SEQUENCE_DELETED,

    /** One person put on a sequence; nothing is sent by this alone. */
    OUTREACH_ENROLLED,

    /** One email of a sequence went from the sender's mailbox; the actor is the sender, the dispatcher sent it. */
    OUTREACH_EMAIL_SENT,

    /** The person answered; only that they did is recorded, never what they said. */
    OUTREACH_REPLIED,
    OUTREACH_BOUNCED,

    /** A sequence ended short — by a consultant's Stop, or by the send-time re-check. */
    OUTREACH_STOPPED,

    /** A call booked with an executive from a consultant's own calendar. */
    OUTREACH_MEETING_BOOKED,

    /** Openers drafted by the model for a review — recorded because it spends model money. */
    OUTREACH_OPENERS_DRAFTED,

    /** A question answered by the assistant — recorded because it spends model and vendor money. */
    ASSISTANT_ASKED;

    @Override
    public String code() {
        return name();
    }
}
