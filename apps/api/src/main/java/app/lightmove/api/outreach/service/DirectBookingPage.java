package app.lightmove.api.outreach.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.RateLimitGuard;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.outreach.constant.MeetingVideo;
import app.lightmove.api.outreach.dto.BookOnPageRequest;
import app.lightmove.api.outreach.dto.BookingSlotsResponse;
import app.lightmove.api.outreach.dto.SlotDayResponse;
import app.lightmove.api.outreach.model.BookingMade;
import app.lightmove.api.outreach.model.BookingSlug;
import app.lightmove.api.outreach.model.BusyInterval;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.FreeSlots;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.NewCalendarEvent;
import app.lightmove.api.outreach.model.SendingWindow;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A direct mailbox's booking page, Uncava's own where a Nylas one is Nylas's scheduler: the consultant's free
 * half-hours, read from their calendar as Book a call reads them, and a booking that invites whoever picked one.
 * Nobody is signed in, so the link is the only credential, every call is rate-limited per IP and link, and a
 * booking counts towards a person only where that consultant emailed the address ({@link LinkBookings}).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DirectBookingPage {

    private static final Duration LENGTH = Duration.ofMinutes(BookingPages.CALL_MINUTES);

    private final MailboxGateway gateway;
    private final MailboxConnectionRepository mailboxes;
    private final UserRepository users;
    private final LinkBookings linkBookings;
    private final RateLimitGuard rateLimit;
    private final TransactionTemplate transactions;
    private final LightMoveProperties properties;
    private final Clock clock;

    public BookingSlotsResponse slots(String slug, LocalDate from, HttpServletRequest request) {
        rateLimit.checkBookingPageOpen(slug, request);
        MailboxConnection mailbox = requireDirectPage(slug);
        FreeSlots free = freeSlotsOf(mailbox);
        Instant now = clock.instant();
        LocalDate today = free.todayAt(now);
        LocalDate latest = today.plus(MeetingService.SLOT_HORIZON);
        LocalDate firstDay = from == null || from.isBefore(today) ? today : from;
        if (firstDay.isAfter(latest)) {
            throw ApiException.of(ErrorCode.MEETING_SLOT_INVALID);
        }
        List<BusyInterval> busy = busyOf(mailbox, free.startOf(now, firstDay, MeetingService.SLOT_DAYS),
                free.endOf(now, firstDay, MeetingService.SLOT_DAYS));
        List<SlotDayResponse> days = free.offered(now, firstDay, MeetingService.SLOT_DAYS, LENGTH, busy).stream()
                .map(day -> new SlotDayResponse(day.date(), day.starts()))
                .toList();
        return new BookingSlotsResponse(mailbox.getTimeZone(), BookingPages.CALL_MINUTES, today, latest,
                free.previousFrom(now, days.getFirst().date(), MeetingService.SLOT_DAYS), days);
    }

    /** The calendar is asked once more for the slot before the invite goes: an invite cannot be taken back. */
    public void book(String slug, BookOnPageRequest booking, HttpServletRequest request) {
        rateLimit.checkBookingPageBook(slug, request);
        MailboxConnection mailbox = requireDirectPage(slug);
        Instant now = clock.instant();
        Instant startsAt = booking.startsAt();
        Instant endsAt = startsAt.plus(LENGTH);
        String bookerAddress = booking.email().trim();
        if (!freeSlotsOf(mailbox).offers(now, startsAt, LENGTH) || bookerAddress.equalsIgnoreCase(mailbox.getAddress())) {
            throw ApiException.of(ErrorCode.MEETING_SLOT_INVALID);
        }
        if (busyOf(mailbox, startsAt, endsAt).stream().anyMatch(taken -> taken.overlaps(startsAt, endsAt))) {
            throw ApiException.of(ErrorCode.MEETING_SLOT_TAKEN);
        }

        String title = BookingPages.CALL_MINUTES + "-minute call: " + booking.name().trim() + " and "
                + consultantNameOf(mailbox);
        CalendarEvent created;
        try {
            created = gateway.createEvent(mailbox.getGrantId(),
                    new NewCalendarEvent(title, startsAt, endsAt, bookerAddress, videoOf(mailbox)));
        } catch (VendorException failed) {
            throw failedAtProvider(mailbox, failed, ErrorCode.MEETING_BOOK_FAILED);
        }
        BookingMade made = new BookingMade(mailbox.getGrantId(), null, created.id(), created.title(), startsAt,
                endsAt, List.of(mailbox.getAddress(), bookerAddress));
        try {
            transactions.executeWithoutResult(status -> mailboxes.findById(mailbox.getId())
                    .ifPresent(fresh -> linkBookings.apply(fresh, made, now)));
        } catch (RuntimeException failed) {
            // The invite has gone; without this line nothing would say that an event exists with no booking behind it.
            log.error("Invite {} went out from mailbox {} through its booking page, but recording the booking failed",
                    created.id(), mailbox.getId(), failed);
            throw failed;
        }
    }

    /** Any link that leads nowhere, or to a Nylas page, answers alike. */
    private MailboxConnection requireDirectPage(String slug) {
        if (!gateway.isOffered() || slug == null || !BookingSlug.SHAPE.matcher(slug).matches()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return mailboxes.findByBookingSlug(slug)
                .filter(MailboxConnection::canSend)
                .filter(MailboxConnection::isDirect)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private List<BusyInterval> busyOf(MailboxConnection mailbox, Instant from, Instant to) {
        try {
            return gateway.busyTimes(mailbox.getGrantId(), mailbox.getAddress(), from, to);
        } catch (VendorException failed) {
            throw failedAtProvider(mailbox, failed, ErrorCode.MEETING_CALENDAR_UNAVAILABLE);
        }
    }

    /** A withdrawn mailbox is marked for its consultant; the page itself says only that the link leads nowhere. */
    private ApiException failedAtProvider(MailboxConnection mailbox, VendorException failed, ErrorCode otherwise) {
        if (MailboxService.isAccessWithdrawn(failed)) {
            transactions.executeWithoutResult(status -> mailboxes.findById(mailbox.getId())
                    .ifPresent(MailboxConnection::markAccessWithdrawn));
            return ApiException.of(ErrorCode.NOT_FOUND);
        }
        log.warn("Booking page call for mailbox {} failed at the provider: {}", mailbox.getId(), failed.getKind());
        return ApiException.of(otherwise);
    }

    private FreeSlots freeSlotsOf(MailboxConnection mailbox) {
        return new FreeSlots(SendingWindow.of(properties.outreach()), mailbox.zone());
    }

    private static MeetingVideo videoOf(MailboxConnection mailbox) {
        return switch (mailbox.getProvider()) {
            case "google" -> MeetingVideo.GOOGLE_MEET;
            case "microsoft" -> MeetingVideo.MICROSOFT_TEAMS;
            default -> MeetingVideo.NONE;
        };
    }

    private String consultantNameOf(MailboxConnection mailbox) {
        return users.findById(mailbox.getUserId()).map(User::getFullName).orElse(mailbox.getAddress());
    }
}
