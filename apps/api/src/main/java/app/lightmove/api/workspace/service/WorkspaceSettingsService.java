package app.lightmove.api.workspace.service;

import app.lightmove.api.billing.plan.service.WorkspaceBillingClosure;
import app.lightmove.api.common.persona.model.HiringPersona;
import app.lightmove.api.common.time.constant.DateFormatStyle;
import app.lightmove.api.common.time.constant.TimeFormatStyle;
import app.lightmove.api.common.time.service.StandardTimeZones;
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
import app.lightmove.api.workspace.model.InferredBackgroundCounts;
import app.lightmove.api.workspace.model.SuggestedDateTime;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceAiControls;
import app.lightmove.api.workspace.model.WorkspaceDateTimeChanged;
import app.lightmove.api.workspace.model.WorkspaceDeleted;
import app.lightmove.api.workspace.repository.InvitationRepository;
import app.lightmove.api.workspace.repository.WorkspaceMemberRepository;
import app.lightmove.api.workspace.repository.WorkspaceRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → General. Deletion ends access at once and leaves the rest to {@link WorkspacePurge} after the grace
 * period, so Uncava can still restore a workspace deleted by mistake.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class WorkspaceSettingsService implements WorkspaceAiControlsReader {

    private final WorkspaceRepository workspaces;
    private final WorkspaceMemberRepository members;
    private final InvitationRepository invitations;
    private final AuditService audit;
    private final WorkspaceCompanyResolver companyResolver;
    private final ApplicationEventPublisher events;
    private final ApiKeyService apiKeys;
    private final OAuthGrantService oauthGrants;
    private final DateTimePreferenceService dateTimePreferences;
    private final WorkspaceBillingClosure billing;
    private final UserRepository users;
    private final InferredBackgroundEraser inferredBackground;
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
        ZoneId previousZone = dateTimePreferences.forWorkspace(workspace).zone();
        workspace.applySettings(identity.name(), identity.company(), defaultRegion, defaultCurrency);
        if (!previousZone.equals(dateTimePreferences.forWorkspace(workspace).zone())) {
            events.publishEvent(new WorkspaceDateTimeChanged(workspaceId));
        }

        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("name", workspace.getName())
                .detailIfPresent("apolloAccountId", workspace.getApolloAccountId())
                .detail("defaultCurrency", workspace.getDefaultCurrency())
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

    /** A null is "not chosen", so a later country change still reaches it. */
    @Transactional
    public WorkspaceDetail changeDateTime(UUID actorId, UUID workspaceId, String timeZone,
                                          DateFormatStyle dateFormat, TimeFormatStyle timeFormat,
                                          HttpServletRequest request) {
        Workspace workspace = requireWorkspace(workspaceId);
        ZoneId previousSettledZone = dateTimePreferences.forWorkspace(workspace).zone();
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
        if (!previousSettledZone.equals(dateTimePreferences.forWorkspace(workspace).zone())) {
            events.publishEvent(new WorkspaceDateTimeChanged(workspaceId));
        }
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

    /**
     * Turning an inference off clears what it left on the workspace's people in the same transaction, so a value
     * the workspace has said it does not want is never held past the switch. The row is locked before anything is
     * cleared, so an AI write in flight either lands first and is cleared, or waits and sees the switch off
     * ({@link #aiControlsHeldForWrite}). A no-op change records nothing.
     */
    @Transactional
    public WorkspaceDetail changeAiControls(UUID actorId, UUID workspaceId, WorkspaceAiControls controls,
                                            HttpServletRequest request) {
        Workspace workspace = workspaces.lockById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND));
        WorkspaceAiControls previous = workspace.getAiControls();
        if (previous.equals(controls)) {
            return detail(workspace);
        }
        workspace.changeAiControls(controls);
        InferredBackgroundCounts cleared = inferredBackground.clear(workspaceId,
                previous.inferGender() && !controls.inferGender(),
                previous.inferNationality() && !controls.inferNationality());
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(request)
                .detail("section", "aiControls")
                .detailIfPresent("inferGender", switched(previous.inferGender(), controls.inferGender()))
                .detailIfPresent("inferNationality",
                        switched(previous.inferNationality(), controls.inferNationality()))
                .detailIfPresent("draftAssessments",
                        switched(previous.draftAssessments(), controls.draftAssessments()))
                .detailIfPresent("enrichAutomatically",
                        switched(previous.enrichAutomatically(), controls.enrichAutomatically()))
                .detail("genderCleared", String.valueOf(cleared.gender()))
                .detail("nationalityCleared", String.valueOf(cleared.nationality()))
                .record();
        return detail(workspace);
    }

    @Override
    @Transactional(readOnly = true)
    public WorkspaceAiControls aiControlsOf(UUID workspaceId) {
        return requireWorkspace(workspaceId).getAiControls();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public WorkspaceAiControls aiControlsHeldForWrite(UUID workspaceId) {
        return workspaces.lockForShareById(workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.WORKSPACE_NOT_FOUND))
                .getAiControls();
    }

    /** What turning the two inferences off would clear now, for the confirmation's count. */
    @Transactional(readOnly = true)
    public InferredBackgroundCounts inferredBackgroundOf(UUID workspaceId) {
        requireWorkspace(workspaceId);
        return inferredBackground.heldIn(workspaceId);
    }

    /** What a new brief and a new package start in; {@code position} reads it when it drafts a brief. */
    @Transactional(readOnly = true)
    public String defaultCurrencyOf(UUID workspaceId) {
        return requireWorkspace(workspaceId).getDefaultCurrency();
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

    private static String switched(boolean before, boolean after) {
        return before == after ? null : after ? "on" : "off";
    }

    private WorkspaceDetail detail(Workspace workspace) {
        long memberCount = members.countByWorkspaceIdAndStatus(workspace.getId(), MemberStatus.ACTIVE);
        return new WorkspaceDetail(workspace, memberCount, dateTimePreferences.suggestionOf(workspace),
                members.countWithAutomaticTimeZone(workspace.getId()));
    }

    public record WorkspaceDetail(Workspace workspace, long memberCount, SuggestedDateTime suggestedDateTime,
                                  long membersAutomaticTimeZone) {
    }
}
