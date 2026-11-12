package app.lightmove.api.core.security.dto;

/**
 * How dates and times read for the caller in the session's workspace, and the layers it is settled from: their own
 * choices (a null zone is Automatic, a null format the workspace's), the zone their browser last reported, and the
 * workspace's, every value settled. Outside a workspace the workspace layer is null.
 */
public record UserDateTimeResponse(
        DateTimeChoiceResponse effective,
        DateTimeChoiceResponse own,
        String deviceTimeZone,
        DateTimeChoiceResponse workspace
) {}
