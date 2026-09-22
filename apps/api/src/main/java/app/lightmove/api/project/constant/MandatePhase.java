package app.lightmove.api.project.constant;

/**
 * Which half of the work a mandate is in. Derived from how much of its universe has been researched,
 * never from {@link ProjectStage} — nothing moves a mandate's stage, so every one of them would read
 * as being in its first phase forever.
 */
public enum MandatePhase {

    /** Building the universe and researching the people in it. */
    MAP,

    /** Working the people it found, through to a shortlist. Search mandates only. */
    ENGAGE
}
