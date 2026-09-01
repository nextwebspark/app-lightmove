package app.lightmove.api.feedback.model;

/**
 * What became of a report.
 *
 * <p>{@link #unpublished()} is an ordinary outcome, not a failure: a deployment with no tracker
 * credential logs the issue it would have filed and answers this, so the widget can say the report
 * was received without inventing a link that goes nowhere.
 */
public record PublishedIssue(Integer number, String url) {

    private static final PublishedIssue UNPUBLISHED = new PublishedIssue(null, null);

    public static PublishedIssue at(Integer number, String url) {
        return new PublishedIssue(number, url);
    }

    public static PublishedIssue unpublished() {
        return UNPUBLISHED;
    }

    public boolean isPublished() {
        return url != null;
    }
}
