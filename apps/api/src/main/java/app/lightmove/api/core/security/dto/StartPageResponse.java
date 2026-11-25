package app.lightmove.api.core.security.dto;

import java.util.UUID;

/**
 * Where the member's Uncava opens in the session's workspace. {@code available} is false when the saved page is no
 * longer theirs to open (a seat they lost, a dashboard deleted or no longer shared): the SPA then opens Home and says so.
 */
public record StartPageResponse(
        /** HOME, POSITIONS, CANDIDATES, TASKS, DASHBOARD or POSITION. */
        String kind,
        UUID dashboardId,
        UUID projectId,

        /** For POSITION: BRIEF, STRATEGY, UNIVERSE, OUTREACH or DASHBOARD. */
        String section,
        boolean available
) {

    public static final StartPageResponse HOME = new StartPageResponse("HOME", null, null, null, true);
}
