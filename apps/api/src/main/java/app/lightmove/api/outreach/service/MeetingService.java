package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.constant.EnrollmentStatus;
import app.lightmove.api.outreach.constant.MeetingVideo;
import app.lightmove.api.outreach.dto.BookMeetingRequest;
import app.lightmove.api.outreach.dto.MeetingResponse;
import app.lightmove.api.outreach.dto.MeetingSlotsResponse;
import app.lightmove.api.outreach.dto.PersonMeetingsResponse;
import app.lightmove.api.outreach.dto.RescheduleMeetingRequest;
import app.lightmove.api.outreach.dto.SlotDayResponse;
import app.lightmove.api.outreach.model.BusyInterval;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.FreeSlots;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.NewCalendarEvent;
import app.lightmove.api.outreach.model.OutreachCallBooked;
import app.lightmove.api.outreach.model.PersonMeeting;
import app.lightmove.api.outreach.model.SendingWindow;
import app.lightmove.api.outreach.model.SlotPage;
import app.lightmove.api.outreach.model.ZoomMeeting;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import app.lightmove.api.outreach.repository.OutreachEnrollmentRepository;
import app.lightmove.api.outreach.repository.PersonMeetingRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * An executive's meetings on the team's calendars, and Book a call: free times from the consultant's
 * own calendar, and an invite sent from it. A booked call moves the person to Engaged and ends their
 * run on this position, since a follow-up asking for a call that is already booked reads badly. A call booked in
 * Uncava is moved or cancelled from the calendar it was booked on, by its holder alone.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MeetingService {

    static final Set<Integer> LENGTHS = Set.of(15, 30, 45);

    /** The past is context, not a log: the newest of it is enough. */
    static final int PAST_SHOWN = 20;

    private final MailboxGateway gateway;
    private final MailboxConnectionRepository mailboxes;
    private final PersonMeetingRepository meetings;
    private final OutreachEnrollmentRepository enrollments;
    private final CandidateOutreachService people;
    private final MeetingSync meetingSync;
    private final MeetingBackfill backfill;
    private final ZoomService zoom;
    private final OutreachOutcomes outcomes;
    private final UserRepository users;
    private final AuditService audit;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Transactional(readOnly = true)
    public PersonMeetingsResponse ofCandidate(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        backfill.refreshUnpushed(workspaceId);
        List<PersonMeeting> rows = distinctMeetings(
                meetings.findByWorkspaceIdAndPersonIdOrderByStartsAtAsc(workspaceId, recipient.personId()));
        Map<UUID, String> owners = users.findAllById(rows.stream().map(PersonMeeting::getUserId).distinct().toList())
                .stream()
                .filter(user -> user.getFullName() != null)
                .collect(Collectors.toMap(User::getId, User::getFullName));
        Instant now = clock.instant();
        List<MeetingResponse> upcoming = rows.stream()
                .filter(row -> row.getEndsAt().isAfter(now))
                .map(row -> responseOf(row, owners, true, isChangeableBy(userId, row, now)))
                .toList();
        List<MeetingResponse> past = rows.stream()
                .filter(row -> !row.getEndsAt().isAfter(now))
                .sorted(Comparator.comparing(PersonMeeting::getStartsAt).reversed())
                .limit(PAST_SHOWN)
                .map(row -> responseOf(row, owners, false, false))
                .toList();
        return new PersonMeetingsResponse(upcoming, past);
    }

    /**
     * A page of free times from {@code from}: today when absent or past, refused beyond {@link FreeSlots#HORIZON}.
     */
    public MeetingSlotsResponse slots(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, int minutes,
                                      LocalDate from, UUID excludingMeetingId) {
        Duration length = requireLength(minutes);
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        PersonMeeting moving = excludingMeetingId == null ? null
                : requireChangeable(userId, workspaceId, recipient, excludingMeetingId);
        MailboxConnection mailbox = requireMailbox(userId, workspaceId);
        if (moving != null) {
            requireOnMailbox(moving, mailbox);
        }
        SlotPage page = freeSlotsOf(mailbox)
                .page(clock.instant(), from, length, (start, end) -> busyOf(mailbox, start, end, moving))
                .orElseThrow(() -> ApiException.of(ErrorCode.MEETING_SLOT_INVALID));
        return new MeetingSlotsResponse(mailbox.getAddress(), mailbox.getTimeZone(), mailbox.getProvider(), minutes,
                page.earliestDate(), page.latestDate(), page.previousFrom(), SlotDayResponse.listOf(page.days()),
                zoom.isUsableBy(userId, workspaceId));
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
        if (!freeSlotsOf(mailbox).books(now, startsAt, length)) {
            throw ApiException.of(ErrorCode.MEETING_SLOT_INVALID);
        }
        if (busyOf(mailbox, startsAt, endsAt).stream().anyMatch(taken -> taken.overlaps(startsAt, endsAt))) {
            throw ApiException.of(ErrorCode.MEETING_SLOT_TAKEN);
        }

        ZoomMeeting zoomMeeting = request.video() == MeetingVideo.ZOOM
                ? zoomMeetingFor(userId, workspaceId, request.title().trim(), startsAt, endsAt)
                : null;
        CalendarEvent created;
        try {
            created = gateway.createEvent(mailbox.getGrantId(), new NewCalendarEvent(request.title().trim(), startsAt,
                    endsAt, request.inviteAddress().trim(), request.video(),
                    zoomMeeting == null ? null : zoomMeeting.joinUrl()));
        } catch (RuntimeException failed) {
            if (zoomMeeting != null) {
                zoom.deleteQuietly(userId, workspaceId, zoomMeeting);
            }
            if (failed instanceof VendorException vendorFailed) {
                throw failedAtMailService(mailbox, vendorFailed, ErrorCode.MEETING_BOOK_FAILED);
            }
            throw failed;
        }
        if (zoomMeeting != null && created.joinUrl() == null) {
            created = new CalendarEvent(created.id(), created.title(), created.startsAt(), created.endsAt(),
                    created.participantAddresses(), zoomMeeting.joinUrl(), ZoomLinks.PROVIDER);
        }

        try {
            recordBooked(userId, workspaceId, projectId, candidateId, recipient, mailbox, created,
                    zoomMeeting == null ? null : zoomMeeting.id(), request.minutes(), now, httpRequest);
        } catch (RuntimeException failed) {
            // The invite has gone; without this line nothing would say that an event exists with no booking behind it.
            log.error("Invite {} went out from mailbox {} for candidate {} on project {} (user {}), but recording "
                            + "the booking failed", created.id(), mailbox.getId(), candidateId, projectId, userId, failed);
            throw failed;
        }
    }

    /** Made before the invite, whose location carries its link; a Zoom that will not make one books nothing. */
    private ZoomMeeting zoomMeetingFor(UUID userId, UUID workspaceId, String title, Instant startsAt,
                                       Instant endsAt) {
        try {
            return zoom.createMeeting(userId, workspaceId, title, startsAt, endsAt);
        } catch (VendorException failed) {
            log.warn("Zoom would not make a meeting for user {}: {}", userId, failed.getKind());
            throw ApiException.of(ErrorCode.MEETING_BOOK_FAILED);
        }
    }

    private void recordBooked(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                              OutreachRecipient recipient, MailboxConnection mailbox, CalendarEvent created,
                              String zoomMeetingId, int minutes, Instant now, HttpServletRequest httpRequest) {
        transactions.executeWithoutResult(status -> {
            MailboxConnection fresh = mailboxes.findById(mailbox.getId()).orElse(mailbox);
            meetingSync.keep(fresh, created, recipient.personId(), userId, false, zoomMeetingId);
            people.recordMeetingBooked(userId, projectId, candidateId, created.startsAt(), false);
            events.publishEvent(new OutreachCallBooked(workspaceId, projectId, candidateId, recipient.personId(),
                    userId, created.startsAt(), created.endsAt(), false));
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
     * Moves a call booked in Uncava to another free time, checked as a booking is, and sends the executive the update.
     * Its Zoom meeting, where it has one, moves first and is put back if the calendar then refuses. No status moves
     * and no run is touched: the call is still booked.
     */
    public void reschedule(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID meetingId,
                           RescheduleMeetingRequest request, HttpServletRequest httpRequest) {
        Duration length = requireLength(request.minutes());
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        PersonMeeting meeting = requireChangeable(userId, workspaceId, recipient, meetingId);
        if (recipient.doNotContact()) {
            throw ApiException.of(ErrorCode.MEETING_DO_NOT_CONTACT);
        }
        MailboxConnection mailbox = requireMailbox(userId, workspaceId);
        requireOnMailbox(meeting, mailbox);
        Instant now = clock.instant();
        Instant startsAt = request.startsAt();
        Instant endsAt = startsAt.plus(length);
        if (startsAt.equals(meeting.getStartsAt()) && endsAt.equals(meeting.getEndsAt())) {
            return;
        }
        if (!freeSlotsOf(mailbox).books(now, startsAt, length)) {
            throw ApiException.of(ErrorCode.MEETING_SLOT_INVALID);
        }
        if (busyOf(mailbox, startsAt, endsAt, meeting).stream().anyMatch(taken -> taken.overlaps(startsAt, endsAt))) {
            throw ApiException.of(ErrorCode.MEETING_SLOT_TAKEN);
        }

        String zoomMeetingId = meeting.getZoomMeetingId();
        boolean zoomMoved = zoomMeetingId != null && movedAtZoom(userId, workspaceId, zoomMeetingId, startsAt, endsAt);
        try {
            gateway.moveEvent(mailbox.getGrantId(), meeting.getProviderEventId(), startsAt, endsAt);
        } catch (VendorException failed) {
            if (failed.getKind() != VendorFailureKind.MALFORMED_RESPONSE) {
                if (zoomMoved) {
                    zoom.moveBackQuietly(userId, workspaceId, zoomMeetingId, meeting.getStartsAt(), meeting.getEndsAt());
                }
                // An event the calendar no longer holds is not the mailbox's access withdrawn: read as that, one call
                // deleted in Outlook would mark a healthy mailbox for reconnecting and stop every run it sends.
                if (CalendarEventGone.isGone(failed)) {
                    transactions.executeWithoutResult(status ->
                            meetings.deleteEvent(mailbox.getId(), meeting.getProviderEventId()));
                    throw ApiException.of(ErrorCode.MEETING_GONE);
                }
                throw failedAtMailService(mailbox, failed, ErrorCode.MEETING_UPDATE_FAILED);
            }
            // A 2xx the gateway could not read back: the update went out, so the move is recorded and Zoom stays moved.
            log.warn("The calendar moved event {} of mailbox {} but its answer could not be read",
                    meeting.getProviderEventId(), mailbox.getId());
        } catch (RuntimeException failed) {
            if (zoomMoved) {
                zoom.moveBackQuietly(userId, workspaceId, zoomMeetingId, meeting.getStartsAt(), meeting.getEndsAt());
            }
            throw failed;
        }

        Instant previousStartsAt = meeting.getStartsAt();
        try {
            transactions.executeWithoutResult(status -> {
                meetings.moveEvent(mailbox.getId(), meeting.getProviderEventId(), startsAt, endsAt);
                people.recordMeetingRescheduled(userId, projectId, candidateId, previousStartsAt, startsAt);
                audit.projectEvent(ProjectEventType.OUTREACH_MEETING_RESCHEDULED, userId, workspaceId, projectId,
                                httpRequest)
                        .detail("candidateId", candidateId.toString())
                        .detail("previousStartsAt", previousStartsAt.toString())
                        .detail("startsAt", startsAt.toString())
                        .detail("minutes", String.valueOf(request.minutes()))
                        .record();
            });
        } catch (RuntimeException failed) {
            // The update has gone; the next calendar read or push puts the row right, but nothing else would say so.
            log.error("Event {} of mailbox {} was moved for candidate {} on project {} (user {}), but recording the "
                            + "move failed", meeting.getProviderEventId(), mailbox.getId(), candidateId, projectId,
                    userId, failed);
            throw failed;
        }
    }

    /**
     * Cancels a call booked in Uncava: the executive is told, its Zoom meeting goes, and the row with it. The person's
     * status stays where it is (a pipeline moves forward only) and no run that the booking ended starts again.
     */
    public void cancel(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID meetingId,
                       HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        PersonMeeting meeting = requireChangeable(userId, workspaceId, recipient, meetingId);
        MailboxConnection mailbox = requireMailbox(userId, workspaceId);
        requireOnMailbox(meeting, mailbox);
        try {
            gateway.cancelEvent(mailbox.getGrantId(), meeting.getProviderEventId());
        } catch (VendorException failed) {
            throw failedAtMailService(mailbox, failed, ErrorCode.MEETING_UPDATE_FAILED);
        }
        if (meeting.getZoomMeetingId() != null) {
            zoom.deleteQuietly(userId, workspaceId, meeting.getZoomMeetingId());
        }
        try {
            transactions.executeWithoutResult(status -> {
                meetings.deleteEvent(mailbox.getId(), meeting.getProviderEventId());
                people.recordMeetingCancelled(userId, projectId, candidateId, meeting.getStartsAt());
                audit.projectEvent(ProjectEventType.OUTREACH_MEETING_CANCELLED, userId, workspaceId, projectId,
                                httpRequest)
                        .detail("candidateId", candidateId.toString())
                        .detail("startsAt", meeting.getStartsAt().toString())
                        .record();
            });
        } catch (RuntimeException failed) {
            // The attendees have been told; the next calendar read drops the row, but nothing else would say so.
            log.error("Event {} of mailbox {} was cancelled for candidate {} on project {} (user {}), but recording "
                            + "the cancel failed", meeting.getProviderEventId(), mailbox.getId(), candidateId,
                    projectId, userId, failed);
            throw failed;
        }
    }

    /**
     * Moves the call's Zoom meeting first, so a calendar refusal can put it back. A Zoom account since disconnected or
     * refused moves nothing there and does not hold up the invite: the link the executive holds still opens it.
     */
    private boolean movedAtZoom(UUID userId, UUID workspaceId, String zoomMeetingId, Instant startsAt,
                                Instant endsAt) {
        try {
            zoom.moveMeeting(userId, workspaceId, zoomMeetingId, startsAt, endsAt);
            return true;
        } catch (VendorException failed) {
            log.warn("Zoom would not move meeting {} for user {}: {}", zoomMeetingId, userId, failed.getKind());
            throw ApiException.of(ErrorCode.MEETING_UPDATE_FAILED);
        } catch (ApiException unusable) {
            log.warn("Zoom meeting {} was not moved: user {}'s Zoom is not usable ({})", zoomMeetingId, userId,
                    unusable.getCode());
            return false;
        }
    }

    /** The meeting, where its caller may change it: booked in Uncava, still to come, and on their own calendar. */
    private PersonMeeting requireChangeable(UUID userId, UUID workspaceId, OutreachRecipient recipient,
                                            UUID meetingId) {
        PersonMeeting meeting = meetings.findByIdAndWorkspaceIdAndPersonId(meetingId, workspaceId,
                        recipient.personId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!meeting.isBookedInUncava() || !meeting.getStartsAt().isAfter(clock.instant())) {
            throw ApiException.of(ErrorCode.MEETING_NOT_EDITABLE);
        }
        if (!userId.equals(meeting.getUserId())) {
            throw ApiException.of(ErrorCode.MEETING_NOT_YOURS);
        }
        return meeting;
    }

    /** A call booked through a mailbox since disconnected is that calendar's to change, not the one held now. */
    private static void requireOnMailbox(PersonMeeting meeting, MailboxConnection mailbox) {
        if (!mailbox.getId().equals(meeting.getMailboxConnectionId())) {
            throw ApiException.of(ErrorCode.MEETING_NOT_EDITABLE);
        }
    }

    private static boolean isChangeableBy(UUID userId, PersonMeeting row, Instant now) {
        return row.isBookedInUncava() && row.getStartsAt().isAfter(now) && userId.equals(row.getUserId());
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

    private static MeetingResponse responseOf(PersonMeeting row, Map<UUID, String> owners, boolean upcoming,
                                              boolean editable) {
        return new MeetingResponse(row.getId(), row.getTitle(), row.getStartsAt(), row.getEndsAt(), row.getUserId(),
                owners.get(row.getUserId()), upcoming ? row.getJoinUrl() : null, row.getConferencingProvider(),
                row.isBookedViaLink(), row.getBookedByUserId() != null, editable);
    }

    private List<BusyInterval> busyOf(MailboxConnection mailbox, Instant from, Instant to) {
        try {
            return gateway.busyTimes(mailbox.getGrantId(), mailbox.getAddress(), from, to);
        } catch (VendorException failed) {
            throw failedAtMailService(mailbox, failed, ErrorCode.MEETING_CALENDAR_UNAVAILABLE);
        }
    }

    /** The calendar's busy times, less the call being moved: its own time is free to move it within. */
    private List<BusyInterval> busyOf(MailboxConnection mailbox, Instant from, Instant to, PersonMeeting moving) {
        List<BusyInterval> busy = busyOf(mailbox, from, to);
        return moving == null ? busy : withoutInterval(busy, moving.getStartsAt(), moving.getEndsAt());
    }

    static List<BusyInterval> withoutInterval(List<BusyInterval> busy, Instant start, Instant end) {
        List<BusyInterval> left = new ArrayList<>();
        for (BusyInterval interval : busy) {
            if (!interval.overlaps(start, end)) {
                left.add(interval);
                continue;
            }
            if (interval.start().isBefore(start)) {
                left.add(new BusyInterval(interval.start(), start));
            }
            if (interval.end().isAfter(end)) {
                left.add(new BusyInterval(end, interval.end()));
            }
        }
        return left;
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
