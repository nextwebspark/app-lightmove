package app.lightmove.api.strategy.constant;

/**
 * {@link #SHARED} is the mandate's list, editable by any seat that may edit the strategy;
 * {@link #PRIVATE} is one person's, which nobody else reads, renames or deletes.
 */
public enum SearchVisibility {
    PRIVATE,
    SHARED
}
