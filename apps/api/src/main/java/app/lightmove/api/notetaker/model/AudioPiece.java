package app.lightmove.api.notetaker.model;

import java.time.Instant;

/** One uploaded audio piece still waiting to be transcribed. */
public record AudioPiece(int sequence, String storageKey, Instant startedAt) {}
