package app.lightmove.api.enrichment.importrun.dto;

import app.lightmove.api.enrichment.importrun.model.ImportEnrichmentProgress;

/** The dialog's progress line for one import's enrichment. Counts only — no names, no profiles. */
public record ImportEnrichmentProgressResponse(int total, int pending, int researched, int alreadyResearched,
                                               int notFound, int refused, int overAllowance, int unavailable,
                                               int failed, boolean done) {

    public static ImportEnrichmentProgressResponse of(ImportEnrichmentProgress progress) {
        return new ImportEnrichmentProgressResponse(progress.total(), progress.pending(), progress.researched(),
                progress.alreadyResearched(), progress.notFound(), progress.refused(), progress.overAllowance(),
                progress.unavailable(), progress.failed(), progress.isDone());
    }
}
