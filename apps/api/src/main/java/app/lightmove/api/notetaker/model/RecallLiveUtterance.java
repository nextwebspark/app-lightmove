package app.lightmove.api.notetaker.model;

import java.time.Instant;

/**
 * One real-time transcript delivery for a live bot: who spoke ({@code speaker} null where Recall could not name them),
 * the offsets into the recording of their first and last word, the absolute time where Recall sent one, and the words.
 * {@code partial}: the words so far of an utterance still being spoken.
 */
public record RecallLiveUtterance(String botId, String participantKey, String speaker, Double startSeconds,
                                  Double endSeconds, Instant startedAt, String text, boolean partial) {}
