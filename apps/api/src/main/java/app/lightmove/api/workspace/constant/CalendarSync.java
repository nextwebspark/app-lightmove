package app.lightmove.api.workspace.constant;

/**
 * How a workspace's calendar events are read. {@code RECALL} hands the app's keys and each user's calendar refresh
 * token to Recall.ai, which pushes changes back; {@code DIRECT} keeps them here and reads a calendar on demand.
 */
public enum CalendarSync {
    RECALL,
    DIRECT
}
