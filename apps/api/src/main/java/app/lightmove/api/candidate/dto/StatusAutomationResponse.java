package app.lightmove.api.candidate.dto;

import java.util.UUID;

/**
 * One event's automation. {@code broken} says the status it moves people to was retired or taken out of process
 * since, so it moves nobody until another is chosen.
 */
public record StatusAutomationResponse(String trigger, boolean enabled, UUID statusId, boolean broken) {}
