package app.lightmove.api.outreach.model;

import java.util.UUID;

/** One person's drafted opener; null when the model could not draft one this time. */
public record DraftedOpener(UUID candidateId, String opener) {}
