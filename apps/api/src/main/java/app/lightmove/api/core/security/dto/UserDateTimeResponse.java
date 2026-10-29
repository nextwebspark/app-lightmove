package app.lightmove.api.core.security.dto;

/**
 * How dates and times read for the caller in the session's workspace, and the two layers it is settled from: their
 * own choices (nulls where they follow) and the workspace's, every value settled. Outside a workspace the workspace
 * layer is null.
 */
public record UserDateTimeResponse(
        DateTimeChoiceResponse effective,
        DateTimeChoiceResponse own,
        DateTimeChoiceResponse workspace
) {}
