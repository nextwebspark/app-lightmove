package app.lightmove.api.feedback.model;

import app.lightmove.api.feedback.constant.AttachmentOrigin;

/**
 * One image on a report, in memory only.
 *
 * <p>Nothing here is persisted: the bytes travel from the browser, through the request, into the issue
 * tracker, and are then gone. That is the whole storage design — see {@code FeedbackService}.
 */
public record FeedbackAttachment(
        String fileName,
        String contentType,
        byte[] content,
        AttachmentOrigin origin
) {}
