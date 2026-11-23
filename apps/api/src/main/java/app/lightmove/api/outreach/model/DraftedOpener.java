package app.lightmove.api.outreach.model;

import java.util.UUID;

/**
 * One person's drafted opener; null when the model could not draft one this time. {@code promptVersion} is the
 * registry version that drafted it, null when none did.
 */
public record DraftedOpener(UUID candidateId, String opener, Integer promptVersion) {}
