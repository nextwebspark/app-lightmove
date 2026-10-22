package app.lightmove.api.notetaker.model;

import java.time.Instant;

/** One uploaded audio piece still waiting to be transcribed, and how many passes have failed on it. */
public record AudioPiece(int sequence, String storageKey, Instant startedAt, int failedAttempts) {}
