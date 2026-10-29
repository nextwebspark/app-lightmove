package app.lightmove.api.workspace.service;

import app.lightmove.api.common.persona.model.HiringPersona;
import app.lightmove.api.common.time.constant.DateFormatStyle;
import app.lightmove.api.common.time.constant.TimeFormatStyle;
import app.lightmove.api.common.time.model.DateTimePreferences;
import app.lightmove.api.common.time.service.StandardTimeZones;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.apikey.ApiKeyService;
import app.lightmove.api.core.security.oauth.OAuthGrantService;
import app.lightmove.api.workspace.constant.CalendarSync;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import app.lightmove.api.workspace.model.CalendarSyncChanged;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceDateTimeChanged;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → General. Deletion is soft — statuses flip, so the audit trail keeps its referents and
 * freed members can join elsewhere.
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
    private final DateTimePreferenceService dateTimePreferences;

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
        ZoneId previousZone = dateTimePreferences.forWorkspace(workspace).zone();
        workspace.applySettings(identity.name(), identity.company(), defaultRegion, defaultCurrency);
        if (!previousZone.equals(dateTimePreferences.forWorkspace(workspace).zone())) {
            events.publishEvent(new WorkspaceDateTimeChanged(workspaceId));
        }

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

    /**
     * Settings → General's Date &amp; time: a null is "not chosen" — the country's zone, the standard formats — so a
     * later country change still reaches it. A no-op save records nothing.
     */
    @Transactional
    public WorkspaceDetail changeDateTime(UUID actorId, UUID workspaceId, String timeZone,
                                          DateFormatStyle dateFormat, TimeFormatStyle timeFormat,
                                          HttpServletRequest request) {
        Workspace workspace = requireWorkspace(workspaceId);
        String previousZone = workspace.getTimeZone();
        DateFormatStyle previousDateFormat = workspace.getDateFormat();
        TimeFormatStyle previousTimeFormat = workspace.getTimeFormat();
        boolean isZoneMoved = !Objects.equals(previousZone, timeZone);
        if (!isZoneMoved && previousDateFormat == dateFormat && previousTimeFormat == timeFormat) {
            return detail(workspace);
        }
        if (isZoneMoved && timeZone != null && !StandardTimeZones.isOffered(timeZone)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "timeZone", "Pick a time zone from the list");
        }
        workspace.changeDateTime(timeZone, dateFormat, timeFormat);
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("section", "dateTime")
                .detail("timeZoneFrom", orCountry(previousZone))
                .detail("timeZoneTo", orCountry(timeZone))
                .detail("dateFormatFrom", orStandard(previousDateFormat))
                .detail("dateFormatTo", orStandard(dateFormat))
                .detail("timeFormatFrom", orStandard(previousTimeFormat))
                .detail("timeFormatTo", orStandard(timeFormat))
                .record();
        events.publishEvent(new WorkspaceDateTimeChanged(workspaceId));
        return detail(workspace);
    }

    private static String orCountry(String zone) {
        return zone == null ? "country" : zone;
    }

    private static String orStandard(Enum<?> format) {
        return format == null ? "standard" : format.name();
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

        workspace.delete();
        members.findByWorkspaceIdAndStatus(workspaceId, MemberStatus.ACTIVE)
                .forEach(member -> member.remove());
        invitations.findByWorkspaceIdAndStatus(workspaceId, InvitationStatus.PENDING)
                .forEach(invitation -> invitation.revoke());
        apiKeys.revokeOnWorkspaceDeletion(actorId, workspaceId, request);
        oauthGrants.revokeOnWorkspaceDeletion(actorId, workspaceId, request);

        log.info("User {} deleted workspace {}", actorId, workspaceId);
        audit.event(WorkspaceEventType.WORKSPACE_DELETED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("name", workspace.getName())
                .record();
    }

    private Workspace requireWorkspace(UUID workspaceId) {
        return workspaces.findById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
    }

    private WorkspaceDetail detail(Workspace workspace) {
        long memberCount = members.countByWorkspaceIdAndStatus(workspace.getId(), MemberStatus.ACTIVE);
        return new WorkspaceDetail(workspace, memberCount, dateTimePreferences.suggestedFor(workspace),
                members.countFollowingWorkspaceDateTime(workspace.getId()));
    }

    /** @param suggestedDateTime what the workspace reads in where its admin has chosen nothing */
    public record WorkspaceDetail(Workspace workspace, long memberCount, DateTimePreferences suggestedDateTime,
                                  long membersFollowingDateTime) {
    }
}
