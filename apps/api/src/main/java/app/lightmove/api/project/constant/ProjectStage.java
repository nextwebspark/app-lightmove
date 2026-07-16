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
}
