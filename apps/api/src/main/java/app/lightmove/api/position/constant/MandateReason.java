package app.lightmove.api.position.constant;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.experimental.Accessors;

/** Why the mandate exists — internal-only context, mirrored by the position screen's select. */
@Getter
@Accessors(fluent = true)
@RequiredArgsConstructor
public enum MandateReason {
    NEW_ROLE("a new role"),
    BACKFILL("a backfill"),
    SUCCESSION("succession"),
    RESTRUCTURING("restructuring"),
    GROWTH_EXPANSION("growth or expansion");

    /** How the reason is named to the model. */
    private final String promptLabel;
}
