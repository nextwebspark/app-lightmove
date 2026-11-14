package app.lightmove.api.candidate.constant;

/**
 * How loudly a screen says a person was approached elsewhere. {@code CAUTION} is amber: a teammate reached them
 * inside the workspace's window, or they are in process on another position. {@code INFO} is a quiet line: only an
 * older approach, or out of process elsewhere. Neither ever blocks a send or an add.
 */
public enum RecentApproachTier {
    NONE,
    INFO,
    CAUTION
}
