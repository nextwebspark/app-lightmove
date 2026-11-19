package app.lightmove.api.candidate.model;

import java.util.UUID;

/** A researcher asked for a person's profile to be researched again, as it is now; {@code linkedinUrl} is the one asked about. */
public record PersonResearchRefreshRequested(UUID workspaceId, UUID personId, UUID requestedBy, String linkedinUrl) {}
