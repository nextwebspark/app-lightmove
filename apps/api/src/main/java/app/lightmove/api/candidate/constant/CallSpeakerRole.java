package app.lightmove.api.candidate.constant;

/**
 * Who a voice on a recorded call is, decided from names before any model reads the transcript, so an interviewer's
 * question or a hiring manager's remark is never read as the executive's own evidence.
 */
public enum CallSpeakerRole {
    /** The executive the assessment is about. */
    CANDIDATE,
    /** A staff member of the workspace, or whoever sent the bot: the interviewer. */
    TEAM,
    /** Someone else on a call whose executive was told apart — a hiring manager, a colleague from outside. */
    OTHER,
    /** Not the team's, but on a call where no voice could be told to be the executive's. */
    UNCONFIRMED
}
