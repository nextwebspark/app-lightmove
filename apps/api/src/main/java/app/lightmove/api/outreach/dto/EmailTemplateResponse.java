package app.lightmove.api.outreach.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * One email template the caller may apply. {@code body} is Markdown; {@code plainBody} is its words alone, tokens kept,
 * for a sequence step, which is plain text. {@code canEdit} is the owner's, or an admin's on a shared one.
 */
public record EmailTemplateResponse(
        UUID id,
        String name,
        String subject,
        String body,
        String plainBody,
        boolean shared,
        boolean mine,
        boolean canEdit,
        EmailTemplateOwnerDto owner,
        Instant updatedAt,
        long version) {}
