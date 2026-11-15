package app.lightmove.api.candidate.model;

import java.util.UUID;

/** A person answered a consent request on their page; whoever asked, else their owner, hears of it. */
public record ConsentAnswered(UUID workspaceId, UUID personId, UUID recipientUserId) {}
