package app.lightmove.api.notetaker.model;

/**
 * One line the transcriber heard in a piece of audio: how far into the piece it began, and the transcriber's own
 * label for the voice ({@code Speaker 1}), which means nothing outside that piece. {@code named}: the label is
 * already the speaker's name — the backup recorder's transcript names its participants — so captions never rename it.
 */
public record HeardLine(long offsetMillis, String speakerLabel, String text, Boolean named) {

    public HeardLine(long offsetMillis, String speakerLabel, String text) {
        this(offsetMillis, speakerLabel, text, null);
    }

    public boolean carriesName() {
        return Boolean.TRUE.equals(named);
    }
}
