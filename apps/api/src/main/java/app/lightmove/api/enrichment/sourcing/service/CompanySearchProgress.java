package app.lightmove.api.enrichment.sourcing.service;

import app.lightmove.api.enrichment.sourcing.constant.SourcingStage;

/** Told as one company's search moves from step to step, and as a counted step ticks. */
@FunctionalInterface
public interface CompanySearchProgress {

    CompanySearchProgress NONE = (stage, considered, done, total) -> { };

    void reached(SourcingStage stage, int considered, int done, int total);
}
