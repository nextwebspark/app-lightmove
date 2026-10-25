package app.lightmove.api.notetaker.dto;

import java.time.Instant;

public record TranscriptLineResponse(Instant at, String speaker, String text) {}
