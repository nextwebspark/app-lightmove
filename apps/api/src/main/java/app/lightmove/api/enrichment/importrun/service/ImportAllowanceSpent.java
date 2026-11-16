package app.lightmove.api.enrichment.importrun.service;

/** Thrown from a paid-read hook when fair use has run out; the worker finishes the row {@code OVER_ALLOWANCE}. */
final class ImportAllowanceSpent extends RuntimeException {

    ImportAllowanceSpent() {
        super(null, null, false, false);
    }
}
