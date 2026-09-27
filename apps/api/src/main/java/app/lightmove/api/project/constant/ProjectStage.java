package app.lightmove.api.project.constant;

/** The seven-step mandate pipeline from the Workspace mockup. Order is meaningful — it drives the stage gates. */
public enum ProjectStage {
    BRIEF,
    UNIVERSE,
    LOCKED,
    MAPPING,
    OUTREACH,
    DELIVERED,
    CLOSED;

    /** A mandate that no longer counts as active work. */
    public boolean isDone() {
        return this == DELIVERED || this == CLOSED;
    }

    /**
     * The furthest gate a mandate has reached: the stored stage, or further if its rows say so. Nothing
     * writes the stored stage past BRIEF, so without this every mandate read BRIEF whatever it had done.
     */
    public static ProjectStage reached(ProjectStage recorded, long universeCompanies, long mappedCompanies,
                                       long reachedOutCandidates) {
        ProjectStage evidenced = reachedOutCandidates > 0 ? OUTREACH
                : mappedCompanies > 0 ? MAPPING
                : universeCompanies > 0 ? UNIVERSE
                : BRIEF;
        return evidenced.compareTo(recorded) > 0 ? evidenced : recorded;
    }
}
