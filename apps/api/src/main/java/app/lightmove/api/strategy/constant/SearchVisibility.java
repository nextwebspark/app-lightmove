package app.lightmove.api.strategy.constant;

/**
 * Who a saved search is for.
 *
 * <p>{@link #SHARED} is the mandate's list — every seat sees it, and any seat that may edit the
 * strategy may edit it, which is the collaboration the screen was built around. {@link #PRIVATE} is
 * one person's scratch list on the same mandate: nobody else reads it, renames it or deletes it.
 */
public enum SearchVisibility {
    PRIVATE,
    SHARED
}
