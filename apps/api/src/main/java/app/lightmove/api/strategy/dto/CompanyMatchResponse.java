package app.lightmove.api.strategy.dto;

/**
 * Whether the Apollo universe publishes the company on a captured page, and which one it is.
 *
 * <p>A miss is a 200 with {@code matched: false}, not a 404. The caller asked a question — "do you
 * know this company?" — and "no" is an answer to it; the capture goes ahead either way, carrying the
 * page's own fields instead of the universe's.
 */
public record CompanyMatchResponse(boolean matched, CompanySuggestion company) {

    public static CompanyMatchResponse noMatch() {
        return new CompanyMatchResponse(false, null);
    }
}
