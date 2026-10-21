package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * The files a consultant's own email may carry — {@code lightmove.outreach.attachments.*}. The per-file ceiling is the
 * strictest of the three mail services': Microsoft Graph takes a file in one post only below 3 MB, and its body is the
 * file base64-encoded under a 4 MB request limit.
 */
public record OutreachAttachmentSettings(
        @DefaultValue("5") int maxFiles,
        @DefaultValue("3000000") long maxFileBytes,
        /** All of an email's files together; Gmail, Exchange and Nylas's multipart send each take far more. */
        @DefaultValue("10485760") long maxTotalBytes
) {}
