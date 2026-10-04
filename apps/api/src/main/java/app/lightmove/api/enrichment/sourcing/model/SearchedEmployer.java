package app.lightmove.api.enrichment.sourcing.model;

/**
 * The company a people search asks about, in each key a provider can take: Bright Data keys on the
 * LinkedIn slug, ContactOut on the website domain or, without one, the name. The slug is also what the
 * people cache files a person under, whichever provider answered.
 */
public record SearchedEmployer(String linkedinSlug, String domain, String name) {}
