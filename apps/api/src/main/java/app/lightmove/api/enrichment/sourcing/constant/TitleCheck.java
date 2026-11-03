package app.lightmove.api.enrichment.sourcing.constant;

/** How a person found at a company is known to hold a title the search wants, or not. */
public enum TitleCheck {
    /** Read from the live index's own record: the title held today. */
    LIVE_TITLE,
    /** Read from the profile index's record, which may be a few months old. */
    INDEX_TITLE,
    /** No title on record, but a free live count narrowed to their name confirms one the search wants. */
    CONFIRMED,
    /** No title on record, and none confirmed. */
    UNCONFIRMED
}
