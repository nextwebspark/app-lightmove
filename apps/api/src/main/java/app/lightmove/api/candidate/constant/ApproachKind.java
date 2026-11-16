package app.lightmove.api.candidate.constant;

/** How someone on the team reached a person: an email through Uncava, their answer, a live sequence, or a logged touch. */
public enum ApproachKind {
    EMAILED,
    REPLIED,
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

    /** A logged "They replied" is their answer, so it ranks as one; any other log is the channel it was on. */
    public static ApproachKind loggedAs(PersonNoteKind noteKind, NoteOutcome outcome) {
        return outcome == NoteOutcome.REPLIED ? REPLIED : loggedAs(noteKind);
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
