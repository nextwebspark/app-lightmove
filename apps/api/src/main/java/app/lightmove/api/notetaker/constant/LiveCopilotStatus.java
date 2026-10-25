package app.lightmove.api.notetaker.constant;

/**
 * The live copilot of a recording that has one: following the call, its feed stopped by Recall (the recording goes on),
 * or stopped by someone on the team.
 */
public enum LiveCopilotStatus {
    LISTENING,
    FEED_STOPPED,
    STOPPED
}
