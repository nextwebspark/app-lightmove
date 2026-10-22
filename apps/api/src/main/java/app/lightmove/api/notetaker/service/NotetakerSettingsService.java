package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.notetaker.dto.MyNotetakerResponse;
import app.lightmove.api.notetaker.dto.WorkspaceNotetakerResponse;
import jakarta.servlet.http.HttpServletRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The workspace's Notetaker switch (its admin's) and each consultant's own; a no-op change records nothing. */
@Service
@RequiredArgsConstructor
public class NotetakerSettingsService {

    private final NotetakerSwitches switches;
    private final AuditService audit;
    private final LightMoveProperties properties;
    private final ChainedMeetingRecorder recorders;

    @Transactional(readOnly = true)
    public WorkspaceNotetakerResponse workspace(UUID workspaceId) {
        return new WorkspaceNotetakerResponse(properties.notetaker().enabled(), switches.workspaceAllows(workspaceId),
                recorders.joinedPlatforms(workspaceId), recorders.backupOffered(),
                switches.backupAllowed(workspaceId));
    }

    @Transactional
    public WorkspaceNotetakerResponse changeWorkspace(UUID actorId, UUID workspaceId, boolean enabled,
                                                      HttpServletRequest request) {
        requireOffered();
        boolean previous = switches.workspaceAllows(workspaceId);
        if (previous != enabled) {
            switches.setWorkspace(workspaceId, enabled, actorId);
            audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                    .actor(actorId).workspace(workspaceId).from(request)
                    .detail("section", "notetaker")
                    .detail("from", String.valueOf(previous))
                    .detail("to", String.valueOf(enabled))
                    .record();
        }
        return workspace(workspaceId);
    }

    /** The backup recorder is a third party a call's audio goes to: turned on only by an admin, and audited. */
    @Transactional
    public WorkspaceNotetakerResponse changeBackup(UUID actorId, UUID workspaceId, boolean enabled,
                                                   HttpServletRequest request) {
        requireOffered();
        if (enabled && !recorders.backupOffered()) {
            throw ApiException.of(ErrorCode.NOTETAKER_UNAVAILABLE);
        }
        boolean previous = switches.backupAllowed(workspaceId);
        if (previous != enabled) {
            switches.setBackup(workspaceId, enabled, actorId);
            audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                    .actor(actorId).workspace(workspaceId).from(request)
                    .detail("section", "notetaker-backup")
                    .detail("from", String.valueOf(previous))
                    .detail("to", String.valueOf(enabled))
                    .record();
        }
        return workspace(workspaceId);
    }

    @Transactional(readOnly = true)
    public MyNotetakerResponse mine(UUID userId, UUID workspaceId) {
        return new MyNotetakerResponse(properties.notetaker().enabled(), switches.workspaceAllows(workspaceId),
                switches.userAllows(userId, workspaceId));
    }

    @Transactional
    public MyNotetakerResponse changeMine(UUID userId, UUID workspaceId, boolean enabled, HttpServletRequest request) {
        requireOffered();
        if (switches.userAllows(userId, workspaceId) != enabled) {
            switches.setUser(userId, workspaceId, enabled);
            audit.event(AuthEventType.PROFILE_UPDATED)
                    .actor(userId).workspace(workspaceId).from(request)
                    .detail("section", "notetaker")
                    .detail("enabled", String.valueOf(enabled))
                    .record();
        }
        return mine(userId, workspaceId);
    }

    private void requireOffered() {
        if (!properties.notetaker().enabled()) {
            throw ApiException.of(ErrorCode.NOTETAKER_UNAVAILABLE);
        }
    }
}
