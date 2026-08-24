package app.lightmove.api.triagecompany.constant;

/**
 * How a triaged company reached a mandate, and therefore how far its snapshot can be trusted.
 *
 * <p>This is not a stage — {@link TriageCompanyStatus} is. It answers a different question: whether
 * the row's company fields were resolved from the Apollo universe or read off a web page, which is
 * what decides whether the row has an {@code apolloAccountId} or a {@code captureKey} identifying it.
 */
public enum TriageCompanyOrigin {

    /**
     * Taken out of the Apollo universe from the Strategy screen. Keyed on {@code apolloAccountId},
     * snapshot resolved server-side, so the fields are the pipeline's and no client chose them.
     */
    STRATEGY,

    /**
     * Read off a page by the Chrome extension because the universe had no match. Keyed on the
     * normalised domain, and the snapshot is only as good as the page — which is why every field is
     * editable in the popup before it is written, and why the triage screen marks these rows.
     */
    CAPTURE
}
