package app.lightmove.api.common.transcript;

import java.time.Duration;
import java.time.Instant;

/**
 * How a call's transcript is written for a model, shared by the Notetaker's summary and the assessment so a time the
 * evidence quotes finds its line on the transcript sheet ({@code lib/meetingTimes.ts}'s {@code elapsedOf}).
 */
public final class CallTranscriptText {

    public static final String MIDDLE_LEFT_OUT = "\n[… the middle of the call is left out …]\n";

    private CallTranscriptText() {}

    /** Time into the call from its booked start: {@code MM:SS}, or {@code H:MM:SS} past the hour. */
    public static String elapsed(Instant callStart, Instant at) {
        long seconds = at == null ? 0 : Math.max(0, Duration.between(callStart, at).toSeconds());
        return seconds >= 3600 ? "%d:%02d:%02d".formatted(seconds / 3600, seconds / 60 % 60, seconds % 60)
                : "%02d:%02d".formatted(seconds / 60, seconds % 60);
    }

    /** A call too long for {@code max} keeps its start and its end, where the opening and closing questions sit. */
    public static String keepStartAndEnd(String text, int max) {
        if (text.length() <= max) {
            return text;
        }
        int half = max / 2;
        return text.substring(0, half) + MIDDLE_LEFT_OUT + text.substring(text.length() - half);
    }
}
