package app.lightmove.api.retention.constant;

/** What the retention sweep deletes, one per row of {@code docs/gdpr/retention-schedule.md} it acts on. */
public enum RetentionTarget {

    REFRESH_TOKENS,
    VERIFICATION_TOKENS,
    MFA_CHALLENGES,
    VENDOR_COMPANIES,
    VENDOR_PEOPLE,
    INVITATIONS,
    NOTIFICATIONS,
    NOTIFICATION_DIGESTS,
    ASSISTANT_THREADS,
    USAGE_EVENTS
}
