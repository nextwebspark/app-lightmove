package app.lightmove.api.notetaker.model;

import java.time.Instant;
import java.util.List;

/** What the transcriber heard in one audio piece, kept as heard so speakers can be named again later. */
public record TranscribedPiece(int sequence, Instant startedAt, List<HeardLine> lines) {}
