package app.lightmove.api.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** A question. Without a {@code threadId} it starts a new chat. */
public record AskRequest(

        @NotBlank
        @Size(max = 4000, message = "A question must be 4000 characters or fewer.")
        String question,

        UUID threadId
) {}
