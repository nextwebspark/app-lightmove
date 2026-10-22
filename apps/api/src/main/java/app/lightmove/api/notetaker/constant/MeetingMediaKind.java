package app.lightmove.api.notetaker.constant;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/** The two things a bot captures, each in 30-second pieces: the call's audio and the platform's own captions. */
public enum MeetingMediaKind {

    AUDIO("webm", "audio/webm"),
    CAPTIONS("json", "application/json");

    private final String extension;
    private final String contentType;

    MeetingMediaKind(String extension, String contentType) {
        this.extension = extension;
        this.contentType = contentType;
    }

    public String contentType() {
        return contentType;
    }

    /** The server names every key, so a bot can only ever write under its own run. */
    public String storageKeyOf(UUID recordingId, int sequence) {
        return "notetaker/%s/%s-%05d.%s".formatted(recordingId, name().toLowerCase(Locale.ROOT), sequence, extension);
    }

    /** The route's spelling, {@code audio} or {@code captions}. */
    public static Optional<MeetingMediaKind> fromPath(String segment) {
        for (MeetingMediaKind kind : values()) {
            if (kind.name().equalsIgnoreCase(segment)) {
                return Optional.of(kind);
            }
        }
        return Optional.empty();
    }
}
