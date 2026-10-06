package app.lightmove.api.publicapi.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.security.apikey.ApiKeyPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;

/** One {@code PUBLIC_API_READ} line per public API read: rows leaving through a key are an export. */
@Service
@RequiredArgsConstructor
public class PublicApiReadAudit {

    private final AuditService audit;

    public void record(ApiKeyPrincipal key, @Nullable UUID projectId, HttpServletRequest request, int rows) {
        AuditService.Builder event = audit.event(ProjectEventType.PUBLIC_API_READ)
                .actor(key.ownerUserId())
                .workspace(key.workspaceId())
                .from(request)
                .detail("keyId", key.keyId().toString())
                .detail("kind", key.kind().name())
                .detail("endpoint", request.getRequestURI())
                .detail("rows", rows);
        if (projectId != null) {
            event.target(AuditService.PROJECT_TARGET, projectId);
        }
        event.record();
    }
}
