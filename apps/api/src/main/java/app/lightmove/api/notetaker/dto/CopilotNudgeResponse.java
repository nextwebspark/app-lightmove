package app.lightmove.api.notetaker.dto;

/** A question the copilot suggests asking next, and why. */
public record CopilotNudgeResponse(String id, String text, String reason) {}
