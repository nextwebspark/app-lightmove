package app.lightmove.api.enrichment.sourcing.service;

import lombok.Getter;

/**
 * A company's search that failed part-way, carrying the live profiles it had already bought and read free, so the
 * run still meters what was spent.
 */
@Getter
public class CompanySearchFailure extends RuntimeException {

    private final int credits;
    private final int cached;

    public CompanySearchFailure(int credits, int cached, RuntimeException cause) {
        super(cause.getMessage(), cause);
        this.credits = credits;
        this.cached = cached;
    }
}
