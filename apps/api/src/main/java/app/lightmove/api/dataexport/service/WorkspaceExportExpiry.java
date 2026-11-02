package app.lightmove.api.dataexport.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.tenant.ResolvedRow;
import app.lightmove.api.core.tenant.TenantResolvers;
import app.lightmove.api.core.tenant.WorkspaceScope;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Deletes a whole-workspace export's zip once it expires, and fails one whose worker died mid-build. V179's resolver
 * names the exports due, so only the workspaces holding one are visited, each in its own scope.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class WorkspaceExportExpiry {

    private final TenantResolvers resolvers;
    private final WorkspaceExportService exports;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${lightmove.retention.sweep-interval}",
            initialDelayString = "${lightmove.retention.sweep-interval}")
    public void scheduledClose() {
        closeDueAt(clock.instant());
    }

    /** Answers how many exports were closed. */
    public int closeDueAt(Instant now) {
        List<ResolvedRow> due = resolvers.workspaceExportsToClose(now,
                now.minus(properties.retention().workspaceExportDeadline()));
        for (ResolvedRow export : due) {
            try {
                WorkspaceScope.run(export.workspaceId(), () -> exports.close(export.id(), now));
            } catch (RuntimeException failed) {
                log.error("Could not close workspace export {}", export.id(), failed);
            }
        }
        return due.size();
    }
}
