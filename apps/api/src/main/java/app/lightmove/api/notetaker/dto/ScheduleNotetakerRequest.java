package app.lightmove.api.notetaker.dto;

/** Record with Notetaker; {@code liveCopilot} sends the bot ready to stream the call to the copilot. */
public record ScheduleNotetakerRequest(boolean liveCopilot) {}
