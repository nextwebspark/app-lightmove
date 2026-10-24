package app.lightmove.api.notetaker.model;

/** A fact a call stated for the record, when it was said, and whether only the team said or assumed it. */
public record StatedFact(String topic, String said, String at, boolean byTeam) {}
