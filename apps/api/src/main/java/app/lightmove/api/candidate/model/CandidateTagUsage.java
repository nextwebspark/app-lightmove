package app.lightmove.api.candidate.model;

import java.util.UUID;

/** How many of a workspace's people hold one tag. */
public interface CandidateTagUsage {

    UUID getTagId();

    long getHolders();
}
