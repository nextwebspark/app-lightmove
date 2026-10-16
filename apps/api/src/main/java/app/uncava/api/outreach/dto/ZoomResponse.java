package app.uncava.api.outreach.dto;

import app.uncava.api.outreach.constant.ZoomConnectionStatus;
import java.time.Instant;

/**
 * The caller's own Zoom account: whether the workspace offers Zoom at all, and the connection when there is one.
 * {@code status} is {@code ERROR} when Zoom refused the stored token and only reconnecting helps.
 */
public record ZoomResponse(boolean offered, ZoomConnectionStatus status, Instant connectedAt) {}
