package app.lightmove.api.notetaker.model;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

/**
 * A bot to send: into which call, when ({@code joinAt} null for at once), under what name, saying what in the call's
 * chat as it joins, and tagged with the recording it belongs to. {@code liveTranscriptUrl} is where Recall streams the
 * transcript as it is spoken, for the live copilot; null for a bot without one.
 */
public record RecallBotRequest(String meetingUrl, Instant joinAt, String botName, String consentLine,
                               Map<String, String> metadata, URI liveTranscriptUrl) {}
