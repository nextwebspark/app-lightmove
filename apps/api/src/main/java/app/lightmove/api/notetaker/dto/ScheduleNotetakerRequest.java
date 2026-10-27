package app.lightmove.api.notetaker.dto;

import app.lightmove.api.notetaker.constant.NotetakerJoin;

/**
 * Record with Notetaker; {@code liveCopilot} sends the bot ready to stream the call to the copilot, and {@code join}
 * says when it goes in — null, as an older tab sends, for the call's start.
 */
public record ScheduleNotetakerRequest(boolean liveCopilot, NotetakerJoin join) {}
