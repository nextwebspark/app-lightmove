package app.lightmove.api.notetaker.dto;

import java.time.Instant;

/** {@code named}: the speaker is someone's name on the call; false for a voice only numbered ("Speaker 3"). */
public record TranscriptLineResponse(Instant at, String speaker, String text, boolean named) {}
