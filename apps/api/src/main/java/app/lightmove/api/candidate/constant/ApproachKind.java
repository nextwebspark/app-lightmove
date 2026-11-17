package app.lightmove.api.candidate.constant;

/** How someone on the team reached a person: an email through Uncava, their answer, a live sequence, or a logged touch. */
public enum ApproachKind {
    EMAILED,
    REPLIED,
    WHATSAPP_REPLIED,
    IN_SEQUENCE,
    CALL_LOGGED,
    LINKEDIN_LOGGED,
    WHATSAPP_LOGGED,
    MEETING_LOGGED,
    EMAIL_LOGGED;

    /** A live sequence is a standing approach, however long ago it started. */
    public boolean isOngoing() {
        return this == IN_SEQUENCE;
    }

    /** Their answer, by email or logged from WhatsApp: it leads whatever the team did. */
    public boolean isReply() {
        return this == REPLIED || this == WHATSAPP_REPLIED;
    }

    /** A logged "They replied" is their answer on WhatsApp; any other log is the channel it was on. */
    public static ApproachKind loggedAs(PersonNoteKind noteKind, NoteOutcome outcome) {
        return outcome == NoteOutcome.REPLIED ? WHATSAPP_REPLIED : loggedAs(noteKind);
    }

    public static ApproachKind loggedAs(PersonNoteKind noteKind) {
        return switch (noteKind) {
            case CALL -> CALL_LOGGED;
            case LINKEDIN -> LINKEDIN_LOGGED;
            case WHATSAPP -> WHATSAPP_LOGGED;
            case MEETING -> MEETING_LOGGED;
            case EMAIL -> EMAIL_LOGGED;
            case GENERAL -> throw new IllegalArgumentException("A general note is no approach");
        };
    }
}
