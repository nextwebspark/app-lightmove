package app.lightmove.api.company.model;

/** A company's rebuild-stable identity in app_lm_companies — the {@code (source, source_id)} pair. */
public record CompanyKey(String source, String sourceId) {}
