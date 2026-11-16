package app.lightmove.api.enrichment.importrun.model;

/** One import's enrichment so far, counted from its rows. */
public record ImportEnrichmentProgress(int total, int pending, int researched, int alreadyResearched, int notFound,
                                       int refused, int unavailable, int failed) {

    public boolean isDone() {
        return pending == 0;
    }
}
