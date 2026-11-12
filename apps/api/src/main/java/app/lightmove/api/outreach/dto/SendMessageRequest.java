package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An email to one executive: {@code body} is Markdown with sequence tokens; {@code replyInThread} answers the thread
 * their mailbox last wrote in, else {@code subject} starts one; {@code sendAt} null is now; {@code signatureId} null
 * sends none; a {@code templateId} the sender may not see is dropped, never refused.
 */
public record SendMessageRequest(
        @NotBlank @Size(max = 320) String toAddress,
        @Size(max = 200) String subject,
        @NotBlank @Size(max = 10_000) String body,
        Instant sendAt,
        boolean replyInThread,
        List<UUID> documentVersionIds,
        UUID signatureId,
        UUID templateId) {}
