package app.lightmove.api.feedback.dto;

/**
 * What the widget tells the tester.
 *
 * <p>{@code published: false} with no number is a success, not a failure — a deployment with no
 * tracker credential has still received the report and logged it. The widget says "received" rather
 * than offering a link that goes nowhere.
 */
public record FeedbackResponse(
        boolean published,
        Integer issueNumber,
        String issueUrl
) {}
