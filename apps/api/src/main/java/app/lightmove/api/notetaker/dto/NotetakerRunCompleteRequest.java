package app.lightmove.api.notetaker.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * A bot leaving: the storage keys of the audio it captured, in order, and of its captions. Each is
 * {@code notetaker/<run id>/<one flat file name>}; {@code NotetakerRunService} checks the run id is the caller's.
 */
public record NotetakerRunCompleteRequest(
        @Size(max = 1000) List<@Pattern(regexp = MEDIA_KEY) String> audioKeys,
        @Pattern(regexp = MEDIA_KEY) String captionsKey
) {

    /** No path segments, no {@code ..}, no control characters, 200 characters at most for the name. */
    public static final String MEDIA_KEY = "notetaker/[0-9a-f-]{36}/[A-Za-z0-9_-][A-Za-z0-9._-]{0,199}";
}
