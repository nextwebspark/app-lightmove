package app.lightmove.api.core.security.apikey;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.ProjectAccess;
import app.lightmove.api.core.security.rbac.ProjectAction;
import app.lightmove.api.project.repository.ProjectRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The public API's guard bean behind {@link RequirePublicScope} and {@link RequirePublicProjectRead}:
 * the scope first, then the position. A personal key reads a position only where its owner holds
 * {@code WORK_VIEW}, re-read every call as {@code ProjectAccess} does; a workspace key reads every
 * position of its own workspace and none of another's. Each check answers true or throws, so a refusal
 * keeps its own code rather than becoming Spring's bare 403.
 */
@Component("publicApiAuthorizer")
@RequiredArgsConstructor
public class PublicApiAuthorizer {

    private final ProjectAccess projectAccess;
    private final ProjectRepository projects;

    public boolean holds(ApiKeyPrincipal key, String scope) {
        requireScope(key, ApiKeyScope.valueOf(scope));
        return true;
    }

    /** Every one of {@code scopes}, then the position. */
    public boolean canReadProject(ApiKeyPrincipal key, UUID projectId, String... scopes) {
        for (String scope : scopes) {
            requireScope(key, ApiKeyScope.valueOf(scope));
        }
        requireProjectRead(PublicReader.of(key), projectId);
        return true;
    }

    /** The position alone, for a reader whose scopes were already checked. */
    public void requireProjectRead(PublicReader reader, UUID projectId) {
        if (reader.readsWholeWorkspace()) {
            projects.requireInWorkspace(projectId, reader.workspaceId());
        } else {
            projectAccess.requireAction(reader.userId(), reader.workspaceId(), projectId, ProjectAction.WORK_VIEW);
        }
    }

    private static void requireScope(ApiKeyPrincipal key, ApiKeyScope scope) {
        if (!key.holds(scope)) {
            throw ApiException.withProperty(ErrorCode.API_KEY_SCOPE_MISSING, "requiredScope", scope.value());
        }
    }
}
