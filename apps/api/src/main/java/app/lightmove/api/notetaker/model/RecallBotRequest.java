package app.lightmove.api.notetaker.model;

import java.time.Instant;
import java.util.Map;

/**
 * A bot to send: into which call, when ({@code joinAt} null for at once), under what name, saying what in the call's
 * chat as it joins, and tagged with the recording it belongs to.
 */
public record RecallBotRequest(String meetingUrl, Instant joinAt, String botName, String consentLine,
                               Map<String, String> metadata) {}
