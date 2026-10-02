package app.lightmove.api.outreach.constant;

import java.util.EnumSet;
import java.util.Set;

/**
 * Where one person's run through a sequence stands. Only {@link #SCHEDULED} and {@link #ACTIVE} are
 * live, and a position holds one live enrollment per person.
 */
public enum EnrollmentStatus {

    /** Enrolled and reviewed; the first email has not gone yet. */
    SCHEDULED,
    ACTIVE,
    REPLIED,
    BOUNCED,
    STOPPED,
    COMPLETED,

    /** A call was booked with them, which ends the run as a reply does. */
    BOOKED;

    public static final Set<EnrollmentStatus> LIVE = EnumSet.of(SCHEDULED, ACTIVE);
}
