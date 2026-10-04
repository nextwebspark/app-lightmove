package app.lightmove.api.outreach.constant;

/** Whether a connected Zoom account can still make meetings. {@code ERROR} is Zoom having refused its token. */
public enum ZoomConnectionStatus {
    ACTIVE,
    ERROR
}
