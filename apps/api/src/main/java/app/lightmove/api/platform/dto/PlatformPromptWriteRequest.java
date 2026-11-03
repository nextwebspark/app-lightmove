package app.lightmove.api.platform.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A new version of a prompt. {@code expectedVersion} is the one the editor opened on, null when it was shipped. */
public record PlatformPromptWriteRequest(
        @NotNull(message = "Write the prompt")
        @Size(max = 100_000, message = "A prompt can be at most 100,000 characters")
        String body,
        Integer expectedVersion
) {}
