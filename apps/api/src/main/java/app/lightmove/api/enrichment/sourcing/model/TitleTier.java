package app.lightmove.api.enrichment.sourcing.model;

/**
 * One tier of a search bought in order: the titles {@code include} finds, less those {@code exclude} finds
 * — the earlier tier, so nobody is bought twice. {@code exclude} is null for the first tier.
 */
public record TitleTier(SearchWords include, SearchWords exclude) {

    public static TitleTier of(SearchWords include) {
        return new TitleTier(include, null);
    }

    public String key() {
        return include.key() + (exclude == null ? "" : "~" + exclude.key());
    }
}
