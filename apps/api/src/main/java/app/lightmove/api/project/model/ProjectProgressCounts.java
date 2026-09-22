package app.lightmove.api.project.model;

/**
 * The five raw numbers a mandate's progress is measured from, counted once for a whole list of
 * mandates by {@code ProjectProgressCounter}.
 *
 * <p>{@code pastIdentified} deliberately counts everyone who has been acted on, including the three
 * statuses that left the running: a person ruled out was still worked. That is the opposite policy to
 * {@code ProjectCandidateCounter}'s headline count, and the two must not be reconciled — one says how
 * many people the mandate has in play, this one says how much engagement has happened.
 */
public record ProjectProgressCounts(long universeTotal, long researched,
                                    long candidatesTotal, long pastIdentified, long qualified) {

    public static final ProjectProgressCounts NONE = new ProjectProgressCounts(0, 0, 0, 0, 0);
}
