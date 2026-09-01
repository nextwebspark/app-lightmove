package app.lightmove.api.feedback.model;

import app.lightmove.api.feedback.constant.FeedbackKind;
import app.lightmove.api.feedback.constant.FeedbackSeverity;
import java.util.List;

/** A validated, sanitised report on its way to the issue tracker. */
public record FeedbackReport(
        FeedbackKind kind,
        FeedbackSeverity severity,
        String title,
        String message,
        String stepsToReproduce,
        FeedbackReporter reporter,
        FeedbackContext context,
        List<FeedbackAttachment> attachments
) {}
