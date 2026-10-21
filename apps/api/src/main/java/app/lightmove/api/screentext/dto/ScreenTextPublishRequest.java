package app.lightmove.api.screentext.dto;

import jakarta.validation.constraints.Size;

/** Publishing every waiting draft as one release; the note is what the Releases list shows beside it. */
public record ScreenTextPublishRequest(@Size(max = 500) String note) {
}
