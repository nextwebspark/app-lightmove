package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.dto.BookMeetingRequest;
import app.lightmove.api.outreach.dto.MeetingResponse;
import app.lightmove.api.outreach.dto.MeetingSlotsResponse;
import app.lightmove.api.outreach.dto.PersonMeetingsResponse;
import app.lightmove.api.outreach.dto.SlotDayResponse;
import app.lightmove.api.outreach.model.BusyInterval;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.FreeSlots;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.NewCalendarEvent;
import app.lightmove.api.outreach.model.PersonMeeting;
import app.lightmove.api.outreach.model.SendingWindow;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.PersonMeetingRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An executive's meetings on the team's calendars, and Book a call: free times from the consultant's
 * own calendar, and an invite sent from it. A booked call moves the person to Engaged and ends their
 * run on this position, since a follow-up asking for a call that is already booked reads badly.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingService {

    static final Set<Integer> LENGTHS = Set.of(15, 30, 45);

    /** A working week of times, as the dialog's grid shows them. */
    static final int SLOT_DAYS = 5;

    /** The past is context, not a log: the newest of it is enough. */
    static final int PAST_SHOWN = 20;

    private final MailboxGateway gateway;
    private final MailboxConnectionRepository mailboxes;
    private final PersonMeetingRepository meetings;
    private final OutreachEnrollmentRepository enrollments;
    private final CandidateOutreachService people;
    private final MeetingSync meetingSync;
    private final OutreachOutcomes outcomes;
    private final UserRepository users;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PersonMeetingsResponse ofCandidate(UUID workspaceId, UUID projectId, UUID candidateId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        List<PersonMeeting> rows = distinctMeetings(
                meetings.findByWorkspaceIdAndPersonIdOrderByStartsAtAsc(workspaceId, recipient.personId()));
        Map<UUID, String> owners = users.findAllById(rows.stream().map(PersonMeeting::getUserId).distinct().toList())
                .stream()
                .filter(user -> user.getFullName() != null)
                .collect(Collectors.toMap(User::getId, User::getFullName));
        Instant now = clock.instant();
        List<MeetingResponse> upcoming = rows.stream()
                .filter(row -> row.getEndsAt().isAfter(now))
                .map(row -> responseOf(row, owners, true))
                .toList();
        List<MeetingResponse> past = rows.stream()
                .filter(row -> !row.getEndsAt().isAfter(now))
                .sorted(Comparator.comparing(PersonMeeting::getStartsAt).reversed())
                .limit(PAST_SHOWN)
                .map(row -> responseOf(row, owners, false))
                .toList();
        return new PersonMeetingsResponse(upcoming, past);
    }

    public MeetingSlotsResponse slots(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, int minutes) {
        Duration length = requireLength(minutes);
        requireRecipient(workspaceId, projectId, candidateId);
        MailboxConnection mailbox = requireMailbox(userId, workspaceId);
        FreeSlots free = freeSlotsOf(mailbox);
        Instant now = clock.instant();
        List<BusyInterval> busy = busyOf(mailbox, now, free.endOf(now, SLOT_DAYS));
        List<SlotDayResponse> days = free.offered(now, SLOT_DAYS, length, busy).stream()
                .map(day -> new SlotDayResponse(day.date(), day.starts()))
                .toList();
        return new MeetingSlotsResponse(mailbox.getAddress(), mailbox.getTimeZone(), mailbox.getProvider(), minutes,
                days);
    }

    /**
     * Everything that could refuse the booking is checked before the invite goes, and the calendar is asked
     * once more for the slot: an invite cannot be taken back quietly once the executive has it.
     */
    public void book(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, BookMeetingRequest request,
                     HttpServletRequest httpRequest) {
        Duration length = requireLength(request.minutes());
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        if (recipient.doNotContact()) {
            throw ApiException.of(ErrorCode.MEETING_DO_NOT_CONTACT);
        }
        if (!recipient.holdsEmail(request.inviteAddress())) {
            throw ApiException.of(ErrorCode.OUTREACH_ADDRESS_NOT_ON_FILE);
        }
        MailboxConnection mailbox = requireMailbox(userId, workspaceId);
        Instant now = clock.instant();
        Instant startsAt = request.startsAt();
        Instant endsAt = startsAt.plus(length);
        if (!freeSlotsOf(mailbox).offers(now, startsAt, length)) {
            throw ApiException.of(ErrorCode.MEETING_SLOT_INVALID);
        }
        if (busyOf(mailbox, startsAt, endsAt).stream().anyMatch(taken -> taken.overlaps(startsAt, endsAt))) {
            throw ApiException.of(ErrorCode.MEETING_SLOT_TAKEN);
        }

        CalendarEvent created;
        try {
            created = gateway.createEvent(mailbox.getGrantId(), new NewCalendarEvent(request.title().trim(), startsAt,
                    endsAt, request.inviteAddress().trim(), request.video()));
        } catch (VendorException failed) {
            throw failedAtMailService(mailbox, failed, ErrorCode.MEETING_BOOK_FAILED);
        }

        try {
            recordBooked(userId, workspaceId, projectId, candidateId, recipient, mailbox, created, request.minutes(),
                    now, httpRequest);
        } catch (RuntimeException failed) {
            // The invite has gone; without this line nothing would say that an event exists with no booking behind it.
            log.error("Invite {} went out from mailbox {} for candidate {} on project {} (user {}), but recording "
                            + "the booking failed", created.id(), mailbox.getId(), candidateId, projectId, userId, failed);
            throw failed;
        }
    }

    private void recordBooked(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                              OutreachRecipient recipient, MailboxConnection mailbox, CalendarEvent created,
                              int minutes, Instant now, HttpServletRequest httpRequest) {
        transactions.executeWithoutResult(status -> {
            MailboxConnection fresh = mailboxes.findById(mailbox.getId()).orElse(mailbox);
            meetingSync.keep(fresh, created, recipient.personId(), userId, false);
            people.recordMeetingBooked(userId, projectId, candidateId, created.startsAt(), false);
            enrollments.findByProjectIdAndPersonIdInAndStatusIn(projectId, List.of(recipient.personId()),
                            EnrollmentStatus.LIVE)
                    .forEach(enrollment -> outcomes.booked(enrollment, userId, false, now, httpRequest));
            audit.projectEvent(ProjectEventType.OUTREACH_MEETING_BOOKED, userId, workspaceId, projectId, httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("startsAt", created.startsAt().toString())
                    .detail("minutes", String.valueOf(minutes))
                    .record();
        });
    }

    /**
     * One call on two teammates' calendars is two rows, one per calendar; the drawer lists it once, as
     * the teammate who booked it through Uncava where one did.
     */
    private static List<PersonMeeting> distinctMeetings(List<PersonMeeting> rows) {
        Map<String, PersonMeeting> byCall = new LinkedHashMap<>();
        for (PersonMeeting row : rows) {
            String call = row.getStartsAt() + "|" + row.getEndsAt() + "|"
                    + Objects.toString(row.getTitle(), "").trim().toLowerCase(Locale.ROOT);
            PersonMeeting held = byCall.get(call);
            if (held == null || (held.getBookedByUserId() == null && row.getBookedByUserId() != null)) {
                byCall.put(call, row);
            }
        }
        return new ArrayList<>(byCall.values());
    }

    private static MeetingResponse responseOf(PersonMeeting row, Map<UUID, String> owners, boolean upcoming) {
        return new MeetingResponse(row.getId(), row.getTitle(), row.getStartsAt(), row.getEndsAt(), row.getUserId(),
                owners.get(row.getUserId()), upcoming ? row.getJoinUrl() : null, row.getConferencingProvider(),
                row.isBookedViaLink(), row.getBookedByUserId() != null);
    }

    private List<BusyInterval> busyOf(MailboxConnection mailbox, Instant from, Instant to) {
        try {
            return gateway.busyTimes(mailbox.getGrantId(), mailbox.getAddress(), from, to);
        } catch (VendorException failed) {
            throw failedAtMailService(mailbox, failed, ErrorCode.MEETING_CALENDAR_UNAVAILABLE);
        }
    }

    private ApiException failedAtMailService(MailboxConnection mailbox, VendorException failed, ErrorCode otherwise) {
        if (MailboxService.isAccessWithdrawn(failed)) {
            transactions.executeWithoutResult(status -> mailboxes.findById(mailbox.getId())
                    .ifPresent(MailboxConnection::markAccessWithdrawn));
            return ApiException.of(ErrorCode.MAILBOX_RECONNECT_NEEDED);
        }
        log.warn("Calendar call for mailbox {} failed at the mail service: {}", mailbox.getId(), failed.getKind());
        return ApiException.of(otherwise);
    }

    private OutreachRecipient requireRecipient(UUID workspaceId, UUID projectId, UUID candidateId) {
        return people.currentRecipient(workspaceId, projectId, candidateId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private MailboxConnection requireMailbox(UUID userId, UUID workspaceId) {
        if (!gateway.isOffered()) {
            throw ApiException.of(ErrorCode.MAILBOX_UNAVAILABLE);
        }
        MailboxConnection mailbox = mailboxes.findByWorkspaceIdAndUserId(workspaceId, userId)
                .orElseThrow(() -> ApiException.of(ErrorCode.MAILBOX_NOT_CONNECTED));
        if (!mailbox.canSend()) {
            throw ApiException.of(ErrorCode.MAILBOX_RECONNECT_NEEDED);
        }
        return mailbox;
    }

    private FreeSlots freeSlotsOf(MailboxConnection mailbox) {
        return new FreeSlots(SendingWindow.of(properties.outreach()), mailbox.zone());
    }

    private static Duration requireLength(Integer minutes) {
        if (minutes == null || !LENGTHS.contains(minutes)) {
            throw ApiException.of(ErrorCode.MEETING_SLOT_INVALID);
        }
        return Duration.ofMinutes(minutes);
    }
}
