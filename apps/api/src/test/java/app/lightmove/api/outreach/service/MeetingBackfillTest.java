package app.lightmove.api.outreach.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import app.lightmove.api.outreach.constant.MailboxGatewayKind;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.GrantedMailbox;
import app.lightmove.api.outreach.model.MailboxConnection;
import app.lightmove.api.outreach.model.MailboxGrants;
import app.lightmove.api.outreach.repository.MailboxConnectionRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

/** How long a calendar that could not be read waits, and which calendars a drawer opening reads again. */
class MeetingBackfillTest {

    private static final Instant NOW = Instant.parse("2026-10-05T09:00:00Z");
    private static final UUID WORKSPACE = UUID.randomUUID();

    private final MailboxGateway gateway = mock(MailboxGateway.class);
    private final MailboxConnectionRepository mailboxes = mock(MailboxConnectionRepository.class);
    private final MeetingSync meetings = mock(MeetingSync.class);
    private final MeetingBackfill backfill = new MeetingBackfill(gateway, mailboxes, meetings,
            mock(PlatformTransactionManager.class), Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    @DisplayName("a quarter of an hour after the first failure, doubling each time, never more than a day")
    void theWaitDoublesUpToADay() {
        assertThat(MeetingBackfill.retryWaitAfter(1)).isEqualTo(Duration.ofMinutes(15));
        assertThat(MeetingBackfill.retryWaitAfter(2)).isEqualTo(Duration.ofMinutes(30));
        assertThat(MeetingBackfill.retryWaitAfter(4)).isEqualTo(Duration.ofHours(2));
        assertThat(MeetingBackfill.retryWaitAfter(9)).isEqualTo(Duration.ofHours(24));
        assertThat(MeetingBackfill.retryWaitAfter(40)).isEqualTo(Duration.ofHours(24));
    }

    @Test
    @DisplayName("a drawer opening reads again only a stale direct calendar nothing pushes changes from")
    void onlyStaleUnpushedCalendarsAreReadAgain() {
        MailboxConnection stale = direct(NOW.minus(Duration.ofMinutes(10)));
        MailboxConnection pushedByRecall = direct(NOW.minus(Duration.ofMinutes(10)));
        pushedByRecall.holdRecallCalendar("recall-calendar-1");
        MailboxConnection fresh = direct(NOW.minus(Duration.ofMinutes(2)));
        MailboxConnection neverRead = direct(null);
        when(mailboxes.findByWorkspaceIdAndGateway(WORKSPACE, MailboxGatewayKind.DIRECT))
                .thenReturn(List.of(stale, pushedByRecall, fresh, neverRead));
        when(mailboxes.findById(stale.getId())).thenReturn(Optional.of(stale));
        List<CalendarEvent> found = List.of(new CalendarEvent("evt-1", "Call", NOW.plus(Duration.ofDays(1)),
                NOW.plus(Duration.ofDays(1)).plus(Duration.ofMinutes(30)), List.of("priya@client.example"), null,
                null));
        Instant from = NOW.minus(MeetingBackfill.RECHECKED_PAST);
        Instant to = NOW.plus(MeetingBackfill.REACH);
        when(gateway.calendarEvents(stale.getGrantId(), from, to)).thenReturn(found);

        backfill.refreshUnpushed(WORKSPACE);

        verify(gateway).calendarEvents(stale.getGrantId(), from, to);
        verify(gateway, never()).calendarEvents(eq(pushedByRecall.getGrantId()), any(), any());
        verify(gateway, never()).calendarEvents(eq(fresh.getGrantId()), any(), any());
        verify(gateway, never()).calendarEvents(eq(neverRead.getGrantId()), any(), any());
        verify(meetings).replaceWindow(stale, from, to, found);
        assertThat(stale.getCalendarSyncedAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("a read that fails leaves the meetings and the last read as they were")
    void aFailedReadChangesNothing() {
        Instant lastRead = NOW.minus(Duration.ofMinutes(10));
        MailboxConnection stale = direct(lastRead);
        when(mailboxes.findByWorkspaceIdAndGateway(WORKSPACE, MailboxGatewayKind.DIRECT)).thenReturn(List.of(stale));
        when(gateway.calendarEvents(anyString(), any(), any())).thenThrow(new IllegalStateException("unreadable"));

        backfill.refreshUnpushed(WORKSPACE);

        verify(meetings, never()).replaceWindow(any(), any(), any(), any());
        assertThat(stale.getCalendarSyncedAt()).isEqualTo(lastRead);
    }

    private static MailboxConnection direct(Instant calendarSyncedAt) {
        MailboxConnection mailbox = MailboxConnection.connected(WORKSPACE, UUID.randomUUID(),
                new GrantedMailbox(MailboxGrants.mintDirect("google"), "yara@firm.example", "google", "refresh"),
                "sealed-refresh-token", 50, NOW.minus(Duration.ofDays(1)));
        ReflectionTestUtils.setField(mailbox, "id", UUID.randomUUID());
        if (calendarSyncedAt != null) {
            mailbox.markCalendarSynced(calendarSyncedAt);
        }
        return mailbox;
    }
}
