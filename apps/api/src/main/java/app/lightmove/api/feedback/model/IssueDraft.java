package app.lightmove.api.feedback.model;

import java.util.List;

/**
 * An issue as this application would like it filed, before any tracker has seen it.
 *
 * <p>Attachments ride along rather than being rendered into {@code body} because hosting an image is
 * the tracker's problem, not the composer's: GitHub commits it to a branch, the logging tracker names
 * it and moves on, and a future tracker might do neither.
 */
public record IssueDraft(
        String title,
        String body,
        List<String> labels,
        List<FeedbackAttachment> attachments
) {}
