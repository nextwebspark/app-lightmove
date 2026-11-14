package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.constant.RecentApproachTier;
import java.util.List;

/**
 * Who on the team has already approached a person, and where else they are mapped. {@code touches} sit inside the
 * workspace's window, a reply first; {@code lastApproach} is the newest one before it, only when none is inside.
 * Staff-only: never on a response a client seat reads.
 */
public record RecentApproachResponse(RecentApproachTier tier, int windowDays, List<ApproachTouchResponse> touches,
                                     List<ApproachPositionResponse> elsewhere, ApproachTouchResponse lastApproach) {}
