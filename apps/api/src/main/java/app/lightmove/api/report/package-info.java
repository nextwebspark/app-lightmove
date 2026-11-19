/**
 * <b>Report</b> — a mandate's talent mapping, aggregated live from other features' rows, none depending back. The
 * researcher breakdown and Ranking are staff-only ({@code WORK_EXECUTE}); Ranking owns the one table, the position's
 * scenario (V204), which never writes the brief. Unpaged, capped by {@code lightmove.report.*}.
 */
@ApplicationModule(allowedDependencies = {
        "candidate :: api",
        "geocoding :: api",
        "position :: api",
        "project :: api",
        "triagecompany :: api"
})
package app.lightmove.api.report;

import org.springframework.modulith.ApplicationModule;
