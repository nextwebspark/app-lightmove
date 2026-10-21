package app.lightmove.api.screentext.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A draft for one key. {@code version} is the draft's as the editor read it, or null when it read none. */
public record ScreenTextDraftRequest(@NotNull @Size(max = 2000) String text, Long version) {
}
