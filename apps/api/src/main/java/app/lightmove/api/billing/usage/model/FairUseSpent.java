package app.lightmove.api.billing.usage.model;

/** One fair-use kind this billing month: the units used, and the ceiling, null where none is set. */
public record FairUseSpent(long used, Long ceiling) {

    public boolean passedBy(long units) {
        return ceiling != null && used + units > ceiling;
    }
}
