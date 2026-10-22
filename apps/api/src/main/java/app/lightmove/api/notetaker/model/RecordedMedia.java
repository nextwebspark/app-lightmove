package app.lightmove.api.notetaker.model;

import java.util.List;

/** Where a finished bot left what it captured: the audio's chunks in order and the captions, as storage keys. */
public record RecordedMedia(List<String> audioKeys, String captionsKey) {

    public RecordedMedia {
        audioKeys = audioKeys == null ? List.of() : List.copyOf(audioKeys);
    }
}
