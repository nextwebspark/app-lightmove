package app.lightmove.api.outreach.model;

import java.util.UUID;

/** How many people a sequence has ever had on it — a sequence card's count. */
public interface SequenceEnrollmentCount {

    UUID getSequenceId();

    long getTotal();
}
