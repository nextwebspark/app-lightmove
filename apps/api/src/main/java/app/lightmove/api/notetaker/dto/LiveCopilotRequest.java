package app.lightmove.api.notetaker.dto;

/** Live copilot on or off for a call whose Notetaker has not joined yet. */
public record LiveCopilotRequest(boolean enabled) {}
