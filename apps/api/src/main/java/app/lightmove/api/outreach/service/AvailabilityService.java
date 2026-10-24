package app.lightmove.api.outreach.service;

import app.lightmove.api.common.location.model.Country;
import app.lightmove.api.common.location.service.Countries;
import app.lightmove.api.core.audit.constant.AuthEventType;
import app.lightmove.api.core.audit.constant.WorkspaceEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.OutreachSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.service.UserTimezones;
import app.lightmove.api.outreach.constant.WorkingHoursSource;
import app.lightmove.api.outreach.dto.MyAvailabilityResponse;
import app.lightmove.api.outreach.dto.UpdateAvailabilityRequest;
import app.lightmove.api.outreach.dto.WorkingDayRequest;
import app.lightmove.api.outreach.dto.WorkingDayResponse;
import app.lightmove.api.outreach.dto.WorkspaceAvailabilityResponse;
import app.lightmove.api.outreach.model.AvailabilityChanged;
import app.lightmove.api.outreach.model.CalendarSchedule;
import app.lightmove.api.outreach.model.DayHours;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.MemberAvailability;
import app.lightmove.api.outreach.model.SendingWindow;
import app.lightmove.api.outreach.model.WeeklyHours;
import app.lightmove.api.outreach.model.WorkingHours;
import app.lightmove.api.outreach.model.WorkingWeeks;
import app.lightmove.api.outreach.model.WorkspaceAvailability;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.MemberAvailabilityRepository;
import app.lightmove.api.outreach.repository.WorkspaceAvailabilityRepository;
import app.lightmove.api.workspace.service.WorkspaceSettingsService;
import jakarta.servlet.http.HttpServletRequest;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * When a consultant works and how much their mailbox may send a day. Working hours are the consultant's own, then the
 * calendar's where it states them, then the workspace's, which until an admin chooses is the week its firm's
 * country keeps. The daily cap is the workspace's, or the consultant's where they chose a lower one.
 */
@Service
@RequiredArgsConstructor
public class AvailabilityService {

    /** The last end time Settings' picker offers, standing for the end of the day. */
    private static final LocalTime LAST_PICKER_TIME = LocalTime.of(23, 59);

    private final WorkspaceAvailabilityRepository workspaceAvailabilities;
    private final MemberAvailabilityRepository memberAvailabilities;
    private final MailboxConnectionRepository mailboxes;
    private final MailboxGateway gateway;
    private final WorkspaceSettingsService workspaces;
    private final UserTimezones timezones;
    private final AuditService audit;
    private final ApplicationEventPublisher events;
    private final LightMoveProperties properties;

    /** The consultant's working hours where the calendar states none, read in their {@code zone}. */
    @Transactional(readOnly = true)
    public WorkingHours workingHoursOf(UUID workspaceId, UUID userId, ZoneId zone) {
        return ownWeekOf(workspaceId, userId)
                .map(own -> new WorkingHours(own, zone, WorkingHoursSource.PERSONAL))
                .orElseGet(() -> new WorkingHours(workspaceWeekOf(workspaceId), zone, WorkingHoursSource.WORKSPACE));
    }

    /**
     * The consultant's own hours first, then the calendar's where it states them, then the workspace's. Outlook states
     * working hours on every mailbox, chosen or not (Exchange's default is Monday to Friday), so a choice made in
     * Uncava has to outrank them or it would lose to a default nobody chose.
     */
    @Transactional(readOnly = true)
    public WorkingHours workingHoursOf(MailboxConnection mailbox, CalendarSchedule schedule) {
        ZoneId zone = mailbox.zone();
        return ownWeekOf(mailbox.getWorkspaceId(), mailbox.getUserId())
                .map(own -> new WorkingHours(own, zone, WorkingHoursSource.PERSONAL))
                .or(() -> schedule.workingHours(zone))
                .orElseGet(() -> new WorkingHours(workspaceWeekOf(mailbox.getWorkspaceId()), zone,
                        WorkingHoursSource.WORKSPACE));
    }

    /**
     * A new sequence's sending days and hours until its author chooses others: the consultant's working week, from its
     * earliest opening to its latest close.
     */
    @Transactional(readOnly = true)
    public SendingWindow sequenceWindowOf(UUID workspaceId, UUID userId) {
        WeeklyHours week = ownWeekOf(workspaceId, userId).orElseGet(() -> workspaceWeekOf(workspaceId));
        LocalTime opens = week.asMap().values().stream().map(DayHours::start).min(Comparator.naturalOrder())
                .orElseThrow();
        LocalTime closes = week.asMap().values().stream().map(DayHours::end).max(Comparator.naturalOrder())
                .orElseThrow();
        // A sequence's window_end is a Postgres time, which rounds the end of the day up to 24:00 and back unreadable.
        return new SendingWindow(opens, closes.equals(LocalTime.MAX) ? LAST_PICKER_TIME : closes, week.days());
    }

    /** What this mailbox may send a day: its owner's own cap, or their workspace's. */
    @Transactional(readOnly = true)
    public int dailyCapOf(MailboxConnection mailbox) {
        return dailyCapOf(mailbox.getWorkspaceId(), mailbox.getUserId());
    }

    /** What one mailbox of this consultant may send a day: the workspace's cap, or their own lower one. */
    @Transactional(readOnly = true)
    public int dailyCapOf(UUID workspaceId, UUID userId) {
        int workspaceCap = workspaceCapOf(workspaceId);
        return memberAvailabilities.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(MemberAvailability::getDailyEmailCap)
                .map(own -> Math.min(own, workspaceCap))
                .orElse(workspaceCap);
    }

    @Transactional(readOnly = true)
    public WorkspaceAvailabilityResponse workspace(UUID workspaceId) {
        Optional<WorkspaceAvailability> chosen = workspaceAvailabilities.findByWorkspaceId(workspaceId);
        boolean weekChosen = chosen.map(WorkspaceAvailability::getWeeklyHours).isPresent();
        boolean capChosen = chosen.map(WorkspaceAvailability::getDailyEmailCap).isPresent();
        String suggestedFrom = weekChosen ? null
                : Countries.resolve(workspaces.companyCountryOf(workspaceId)).map(Country::name).orElse(null);
        return new WorkspaceAvailabilityResponse(WorkingDayResponse.listOf(workspaceWeekOf(workspaceId)), weekChosen,
                suggestedFrom, workspaceCapOf(workspaceId), capChosen, settings().maxDailyCap());
    }

    @Transactional(readOnly = true)
    public MyAvailabilityResponse mine(UUID workspaceId, UUID userId) {
        Optional<MemberAvailability> own = memberAvailabilities.findByWorkspaceIdAndUserId(workspaceId, userId);
        int workspaceCap = workspaceCapOf(workspaceId);
        boolean calendarStatesHours = gateway.isOffered() && mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId)
                .filter(MailboxConnection::canSend)
                .map(mailbox -> gateway.statesWorkingHours(mailbox.getGrantId()))
                .orElse(false);
        return new MyAvailabilityResponse(
                WorkingDayResponse.listOf(own.map(MemberAvailability::getWeeklyHours).orElse(null)),
                WorkingDayResponse.listOf(workspaceWeekOf(workspaceId)),
                own.map(MemberAvailability::getDailyEmailCap).map(cap -> Math.min(cap, workspaceCap))
                        .orElse(null),
                workspaceCap,
                timezones.zoneOf(userId).getId(), calendarStatesHours);
    }

    /** Settings → Meetings & email. Recorded only where something changed, as every workspace setting is. */
    @Transactional
    public WorkspaceAvailabilityResponse changeWorkspace(UUID actorId, UUID workspaceId,
                                                         UpdateAvailabilityRequest request,
                                                         HttpServletRequest httpRequest) {
        WeeklyHours week = weekOf(request.weeklyHours());
        Integer cap = request.dailyEmailCap();
        if (cap != null && (cap < 1 || cap > settings().maxDailyCap())) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "dailyEmailCap",
                    "Choose a daily limit from 1 to " + settings().maxDailyCap());
        }
        Optional<WorkspaceAvailability> held = workspaceAvailabilities.findByWorkspaceId(workspaceId);
        if (Objects.equals(held.map(WorkspaceAvailability::getWeeklyHours).orElse(null), week)
                && Objects.equals(held.map(WorkspaceAvailability::getDailyEmailCap).orElse(null), cap)) {
            return workspace(workspaceId);
        }
        // Inserted if missing first, so two first saves racing write one row rather than refusing the second.
        workspaceAvailabilities.insertIfMissing(workspaceId);
        WorkspaceAvailability availability = workspaceAvailabilities.findByWorkspaceId(workspaceId).orElseThrow();
        availability.change(week, cap);
        workspaceAvailabilities.flush();
        memberAvailabilities.lowerCapsAbove(workspaceId, workspaceCapOf(workspaceId));
        audit.event(WorkspaceEventType.WORKSPACE_UPDATED)
                .actor(actorId).workspace(workspaceId).from(httpRequest)
                .detail("section", "availability")
                .detail("weeklyHours", week == null ? "country" : week.toString())
                .detail("dailyEmailCap", cap == null ? "default" : cap.toString())
                .record();
        events.publishEvent(new AvailabilityChanged(workspaceId, null));
        return workspace(workspaceId);
    }

    /** Settings → Availability, the consultant's own; nothing left of theirs removes the row. */
    @Transactional
    public MyAvailabilityResponse changeMine(UUID userId, UUID workspaceId, UpdateAvailabilityRequest request,
                                             HttpServletRequest httpRequest) {
        WeeklyHours week = weekOf(request.weeklyHours());
        Integer cap = request.dailyEmailCap();
        int workspaceCap = workspaceCapOf(workspaceId);
        if (cap != null && (cap < 1 || cap > workspaceCap)) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "dailyEmailCap",
                    "Choose a daily limit from 1 to your workspace's " + workspaceCap);
        }
        Optional<MemberAvailability> held = memberAvailabilities.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (Objects.equals(held.map(MemberAvailability::getWeeklyHours).orElse(null), week)
                && Objects.equals(held.map(MemberAvailability::getDailyEmailCap).orElse(null), cap)) {
            return mine(workspaceId, userId);
        }
        if (week == null && cap == null) {
            held.ifPresent(memberAvailabilities::delete);
        } else {
            memberAvailabilities.insertIfMissing(workspaceId, userId);
            memberAvailabilities.findByWorkspaceIdAndUserId(workspaceId, userId).orElseThrow().change(week, cap);
        }
        audit.event(AuthEventType.PROFILE_UPDATED)
                .actor(userId).workspace(workspaceId).from(httpRequest)
                .detail("section", "availability")
                .detail("weeklyHours", week == null ? "workspace" : week.toString())
                .detail("dailyEmailCap", cap == null ? "workspace" : cap.toString())
                .record();
        events.publishEvent(new AvailabilityChanged(workspaceId, userId));
        return mine(workspaceId, userId);
    }

    private Optional<WeeklyHours> ownWeekOf(UUID workspaceId, UUID userId) {
        return memberAvailabilities.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(MemberAvailability::getWeeklyHours);
    }

    private WeeklyHours workspaceWeekOf(UUID workspaceId) {
        return workspaceAvailabilities.findByWorkspaceId(workspaceId)
                .map(WorkspaceAvailability::getWeeklyHours)
                .orElseGet(() -> WorkingWeeks.defaultFor(workspaces.companyCountryOf(workspaceId),
                        Set.copyOf(settings().workingDays())));
    }

    private int workspaceCapOf(UUID workspaceId) {
        return workspaceAvailabilities.findByWorkspaceId(workspaceId)
                .map(WorkspaceAvailability::getDailyEmailCap)
                .orElse(settings().dailyCap());
    }

    /**
     * A week as Settings may send one: each day once, on the half hour (or ending at 23:59, the last time the picker
     * offers, kept as the end of the day so a last half hour fits), opening before it closes, and at least one day worked — a week nobody works offers a booking link
     * nothing. Null is "not chosen".
     */
    static WeeklyHours weekOf(List<WorkingDayRequest> days) {
        if (days == null) {
            return null;
        }
        Map<DayOfWeek, DayHours> week = new EnumMap<>(DayOfWeek.class);
        for (WorkingDayRequest day : days) {
            if (!isOnPicker(day.start()) || !isOnPicker(day.end())) {
                throw invalidWeek("Choose times on the hour or half hour");
            }
            if (!day.start().isBefore(day.end())) {
                throw invalidWeek("A working day has to end after it starts");
            }
            LocalTime end = day.end().equals(LAST_PICKER_TIME) ? LocalTime.MAX : day.end();
            if (week.put(day.day(), new DayHours(day.start(), end)) != null) {
                throw invalidWeek("Each day can be listed once");
            }
        }
        if (week.isEmpty()) {
            throw invalidWeek("Choose at least one working day");
        }
        return WeeklyHours.of(week);
    }

    private static boolean isOnPicker(LocalTime time) {
        return time.equals(LAST_PICKER_TIME)
                || (time.getSecond() == 0 && time.getNano() == 0 && time.getMinute() % 30 == 0);
    }

    private static ApiException invalidWeek(String message) {
        return ApiException.withField(ErrorCode.VALIDATION_FAILED, "weeklyHours", message);
    }

    private OutreachSettings settings() {
        return properties.outreach();
    }
}
