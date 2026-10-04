package app.lightmove.api.outreach.model;

import java.util.UUID;

/** A sequence card's counts: everyone ever put on it, how many emails went, and how many people answered. */
public interface SequenceEnrollmentCount {

    UUID getSequenceId();

    long getTotal();

    long getSent();

    long getReplied();
}
