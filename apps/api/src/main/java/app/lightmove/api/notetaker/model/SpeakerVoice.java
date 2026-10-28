package app.lightmove.api.notetaker.model;

/** A voice heard on a call, as its transcript names it; {@code named}: the name is the one the participant gave. */
public record SpeakerVoice(String speaker, boolean named) {}
