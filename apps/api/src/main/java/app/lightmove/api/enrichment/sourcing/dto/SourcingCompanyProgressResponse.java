package app.lightmove.api.enrichment.sourcing.dto;

import app.lightmove.api.enrichment.sourcing.model.CompanyProgress;
import java.time.Instant;
import java.util.UUID;

/** One company's step while it is searched, as the panel's progress strip and company block read it. */
public record SourcingCompanyProgressResponse(UUID triageCompanyId, String stage, int considered, int done, int total,
                                              Instant startedAt, Instant finishedAt) {

    public static SourcingCompanyProgressResponse of(UUID triageCompanyId, CompanyProgress progress) {
        return new SourcingCompanyProgressResponse(triageCompanyId,
                progress.stage() == null ? null : progress.stage().name(), progress.considered(), progress.done(),
                progress.total(), instantOf(progress.startedAtMillis()), instantOf(progress.finishedAtMillis()));
    }

    private static Instant instantOf(Long epochMillis) {
        return epochMillis == null ? null : Instant.ofEpochMilli(epochMillis);
    }
}
