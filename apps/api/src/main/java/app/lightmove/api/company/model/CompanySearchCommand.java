package app.lightmove.api.company.model;

/** Validated input to {@code CompanySearchService.search} — the raw query text plus pagination. */
public record CompanySearchCommand(String query, int page, int size) {
}
