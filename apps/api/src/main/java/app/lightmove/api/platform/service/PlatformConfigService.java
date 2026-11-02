package app.lightmove.api.platform.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.platformconfig.model.PlatformConfigSnapshot;
import app.lightmove.api.core.platformconfig.service.PlatformConfigChanges;
import app.lightmove.api.core.platformconfig.service.PlatformConfigStore;
import app.lightmove.api.core.platformconfig.service.PlatformOverrides;
import app.lightmove.api.platform.dto.PlatformConfigVersionResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The version both pages show, and Refresh, which both pages offer and which reloads every instance's overrides. */
@Service
@RequiredArgsConstructor
public class PlatformConfigService {

    private final PlatformConfigStore store;
    private final PlatformOverrides overrides;
    private final PlatformConfigChanges changes;
    private final AuditService audit;

    public PlatformConfigVersionResponse version() {
        PlatformConfigSnapshot loaded = overrides.current();
        return new PlatformConfigVersionResponse(store.currentVersion(), loaded.version(), loaded.loadedAt());
    }

    /**
     * Raises the version with nothing else changed, so every instance's next read reloads at once rather than at its
     * next interval check. The NOTIFY is delivered at commit, so the response's version is read after it.
     */
    @Transactional
    public long refresh(UUID userId, HttpServletRequest request) {
        long version = changes.announce();
        audit.event(PlatformEventType.PLATFORM_CONFIG_REFRESHED).actor(userId)
                .target("platform_config", version).detail("version", version).from(request).record();
        return version;
    }
}
