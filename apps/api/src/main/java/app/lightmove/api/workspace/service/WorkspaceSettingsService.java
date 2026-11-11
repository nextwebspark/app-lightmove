package app.lightmove.api.workspace.service;

import app.lightmove.api.billing.plan.service.WorkspaceBillingClosure;
import app.lightmove.api.common.persona.model.HiringPersona;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.apikey.ApiKeyService;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.oauth.OAuthGrantService;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.constant.CalendarSync;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import app.lightmove.api.workspace.model.CalendarSyncChanged;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceDeleted;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → General. Deletion ends access at once and leaves the rest to {@link WorkspacePurge} after the grace
 * period, so Uncava can still restore a workspace deleted by mistake.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceSettingsService {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final InvitationRepository invitations;
    private final AuditService audit;
    private final WorkspaceCompanyResolver companyResolver;
    private final ApplicationEventPublisher events;
    private final ApiKeyService apiKeys;
    private final OAuthGrantService oauthGrants;
    private final WorkspaceBillingClosure billing;
    private final UserRepository users;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public WorkspaceDetail get(UUID workspaceId) {
        return detail(requireWorkspace(workspaceId));
    }

    @Transactional
    public WorkspaceDetail update(UUID actorId, UUID workspaceId, String name, String apolloAccountId,
                                  String defaultRegion, String defaultCurrency,
                                  HttpServletRequest request) {
        Workspace workspace = requireWorkspace(workspaceId);
        WorkspaceIdentity identity = companyResolver.resolve(name, apolloAccountId);
        workspace.applySettings(identity.name(), identity.company(), defaultRegion, defaultCurrency);

        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("name", workspace.getName())
                .detailIfPresent("apolloAccountId", workspace.getApolloAccountId())
                .record();

        return detail(workspace);
    }

    @Transactional
    public WorkspaceDetail updatePersona(UUID actorId, UUID workspaceId, HiringPersona persona,
                                         HttpServletRequest request) {
        Workspace workspace = requireWorkspace(workspaceId);
        workspace.describePersona(persona);

        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("section", "persona")
                .record();

        return detail(workspace);
    }

    /** Nothing stored changes with the mode, so a switch migrates no row; a no-op switch records nothing. */
    @Transactional
    public WorkspaceDetail changeMode(UUID actorId, UUID workspaceId, WorkspaceMode mode,
                                      HttpServletRequest request) {
        Workspace workspace = requireWorkspace(workspaceId);
        WorkspaceMode previous = workspace.getMode();
        if (previous != mode) {
            workspace.changeMode(mode);
            audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                    .actor(actorId).workspace(workspaceId).from(request)
                    .detail("section", "mode")
                    .detail("from", previous.name())
                    .detail("to", mode.name())
                    .record();
        }
        return detail(workspace);
    }

    /** Like the mode: nothing stored is migrated by a switch, and a no-op switch records nothing. */
    @Transactional
    public WorkspaceDetail changeCalendarSync(UUID actorId, UUID workspaceId, CalendarSync calendarSync,
                                              HttpServletRequest request) {
        Workspace workspace = requireWorkspace(workspaceId);
        CalendarSync previous = workspace.getCalendarSync();
        if (previous != calendarSync) {
            workspace.changeCalendarSync(calendarSync);
            audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                    .actor(actorId).workspace(workspaceId).from(request)
                    .detail("section", "calendarSync")
                    .detail("from", previous.name())
                    .detail("to", calendarSync.name())
                    .record();
            events.publishEvent(new CalendarSyncChanged(workspaceId, calendarSync));
        }
        return detail(workspace);
    }

    /** How the workspace's calendars are read; {@code outreach} asks before handing anything to Recall. */
    @Transactional(readOnly = true)
    public CalendarSync calendarSyncOf(UUID workspaceId) {
        return requireWorkspace(workspaceId).getCalendarSync();
    }

    /** The firm's country as signup or Settings picked it, or null; {@code outreach} reads its working week from it. */
    @Transactional(readOnly = true)
    public String companyCountryOf(UUID workspaceId) {
        return requireWorkspace(workspaceId).getCompanyCountry();
    }


    /** The typed name is verified here, not only in the browser. */
    @Transactional
    public void delete(UUID actorId, UUID workspaceId, String confirmName, HttpServletRequest request) {
        Workspace workspace = requireWorkspace(workspaceId);

        if (confirmName == null || !workspace.getName().equalsIgnoreCase(confirmName.trim())) {
            throw ApiException.of(ErrorCode.WORKSPACE_NAME_MISMATCH);
        }

        Instant now = clock.instant();
        // The certificate is emailed at the purge, when nobody can sign in to read where it should go.
        String noticeEmail = users.findById(actorId).map(User::getEmail).orElse(null);
        workspace.delete(actorId, now, properties.retention().deletedWorkspacesAfter(), noticeEmail);
        members.findByWorkspaceIdAndStatus(workspaceId, MemberStatus.ACTIVE)
                .forEach(member -> member.endWithWorkspace());
        invitations.findByWorkspaceIdAndStatus(workspaceId, InvitationStatus.PENDING)
                .forEach(invitation -> invitation.revoke());
        apiKeys.revokeOnWorkspaceDeletion(actorId, workspaceId, request);
        oauthGrants.revokeOnWorkspaceDeletion(actorId, workspaceId, request);
        billing.onWorkspaceDeleted(workspaceId);
        events.publishEvent(new WorkspaceDeleted(workspaceId, actorId, now));

        log.info("User {} deleted workspace {}, to be purged after {}", actorId, workspaceId,
                workspace.getPurgeAfter());
        audit.event(WorkspaceEventType.WORKSPACE_DELETED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("name", workspace.getName())
                .detail("purgeAfter", workspace.getPurgeAfter().toString())
                .record();
    }

    private Workspace requireWorkspace(UUID workspaceId) {
        return workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private WorkspaceDetail detail(Workspace workspace) {
        long memberCount = members.countByWorkspaceIdAndStatus(workspace.getId(), MemberStatus.ACTIVE);
        return new WorkspaceDetail(workspace, memberCount);
    }

    public record WorkspaceDetail(Workspace workspace, long memberCount) {
    }
}
