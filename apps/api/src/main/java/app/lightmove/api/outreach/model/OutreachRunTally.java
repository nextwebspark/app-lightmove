package app.lightmove.api.outreach.model;

import app.lightmove.api.outreach.constant.EnrollmentStatus;
import java.time.Instant;

/** One status's share of a position's runs: how many, the emails they sent, and the earliest still due. */
public interface OutreachRunTally {

    EnrollmentStatus getStatus();

    long getTotal();

    long getSent();

    long getReached();

    Instant getNextSendAt();
}
