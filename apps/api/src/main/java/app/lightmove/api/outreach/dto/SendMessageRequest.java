package app.lightmove.api.outreach.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * An email a consultant writes to one executive. {@code body} is the composer's Markdown and may carry the
 * sequence tokens. {@code replyInThread} answers the thread their mailbox last wrote to this person in, under its own
 * subject; otherwise {@code subject} starts a thread of its own. {@code sendAt} null is now.
 */
public record SendMessageRequest(
        @NotBlank @Size(max = 320) String toAddress,
        @Size(max = 200) String subject,
        @NotBlank @Size(max = 10_000) String body,
        Instant sendAt,
        boolean replyInThread) {}
