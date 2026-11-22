package app.lightmove.api.startpage.service;

import app.lightmove.api.core.security.dto.StartPageResponse;
import app.lightmove.api.core.security.service.StartPageLookup;
import app.lightmove.api.core.tenant.WorkspaceScope;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The auth response's start page. Scoped here because a sign-in assembles it before any principal names a workspace. */
@Component
@RequiredArgsConstructor
class StartPageLookupAdapter implements StartPageLookup {

    private final StartPageService startPages;

    @Override
    public StartPageResponse startPageOf(UUID userId, UUID workspaceId) {
        return WorkspaceScope.call(workspaceId, () -> startPages.read(userId, workspaceId));
    }
}
