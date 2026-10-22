package app.lightmove.api.notetaker.dto;

/** {@code heard}: every word the transcriber heard in the canary's piece, in order; empty for silence. */
public record NotetakerCanaryHearingResponse(String heard) {}
