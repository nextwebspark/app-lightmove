package app.lightmove.api.core.llm.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Plain text to embed. */
public record EmbedRequest(
        @NotBlank(message = "Give some text to embed")
        @Size(max = 20_000, message = "That text is too long")
        String text
) {}
