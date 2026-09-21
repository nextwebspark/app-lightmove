package app.lightmove.api.assistant.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/**
 * A question, and the mandate it is being asked about.
 *
 * <p>{@code projectId} is context for the model, recorded on the thread so the conversation reads
 * back with the screen it was asked from. It authorises nothing: a tool call naming a project is
 * checked against that project, not against this.
 */
public record AskRequest(

        @NotBlank
        @Size(max = 4000, message = "A question must be 4000 characters or fewer.")
        String question,

        UUID projectId
) {}
