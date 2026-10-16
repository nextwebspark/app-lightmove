package app.uncava.api.candidate.model;

import java.util.UUID;

/** How many of a workspace's mapped executives hold one status. */
public interface CandidateStatusUsage {

    UUID getStatusId();

    long getHolders();
}
