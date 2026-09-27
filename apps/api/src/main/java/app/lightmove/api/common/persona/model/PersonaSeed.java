package app.lightmove.api.common.persona.model;

/** What a persona is seeded from and re-filed by: the universe company picked, as its id, industry and country. */
public record PersonaSeed(String apolloAccountId, String industry, String country) {
}
