package app.lightmove.api.notetaker.dto;

/** {@code leave}: somebody pressed Remove notetaker, and the bot goes now. */
public record NotetakerHeartbeatResponse(boolean leave) {}
