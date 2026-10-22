package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.NotetakerSettings;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.model.ClaimedCall;
import app.lightmove.api.notetaker.model.NotetakerCall;
import app.lightmove.api.notetaker.model.PendingLaunch;
import app.lightmove.api.outreach.service.KeptMeetings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute: ends the runs whose bot is gone, then sends a bot to each kept call about to start that
 * {@link NotetakerCallEligibility} reads as switched on. Safe on several instances at once — see
 * {@link MeetingRecordingClaims}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotetakerScheduler {

    private final KeptMeetings meetings;
    private final NotetakerCallEligibility eligibility;
    private final MeetingRecordingClaims claims;
    private final NotetakerLauncher launcher;
    private final NotetakerSweeper sweeper;
    private final NotetakerMediaPurge mediaPurge;
    private final LightMoveProperties properties;
    private final Clock clock;

    @Scheduled(fixedDelayString = "${lightmove.notetaker.schedule-interval}",
            initialDelayString = "${lightmove.notetaker.schedule-interval}")
    public void schedule() {
        if (properties.notetaker().enabled()) {
            scheduleAt(clock.instant());
        }
    }

    /** One pass. The timer calls it with the clock; tests call it with the moment they arranged. */
    public void scheduleAt(Instant now) {
        try {
            sweeper.endStale(now);
        } catch (RuntimeException failed) {
            // A call starting now must not wait a minute because an old run could not be closed.
            log.error("Notetaker sweep failed; launching anyway", failed);
        }
        try {
            mediaPurge.purgeRemoved();
        } catch (RuntimeException failed) {
            log.error("Notetaker media purge failed; launching anyway", failed);
        }
        for (PendingLaunch pending : claimDue(now)) {
            try {
                launcher.launch(pending);
            } catch (RuntimeException failed) {
                log.error("Notetaker launch of run {} failed", pending.recordingId(), failed);
            }
        }
    }

    private List<PendingLaunch> claimDue(Instant now) {
        NotetakerSettings settings = properties.notetaker();
        List<PendingLaunch> pending = new ArrayList<>();
        List<NotetakerCall> calls = eligibility.callsOf(meetings.startingBetween(now.minus(settings.joinLate()),
                now.plus(settings.joinAhead())));
        for (NotetakerCall call : calls) {
            if (!call.isSwitchedOn() || !call.first().endsAt().isAfter(now)) {
                continue;
            }
            ClaimedCall claimed = new ClaimedCall(call.callKey(), call.platform(), call.sentFrom().orElseThrow());
            if (call.doNotContact()) {
                claims.claimSkipped(claimed, NotetakerEndReason.DO_NOT_CONTACT);
                continue;
            }
            String runToken = Tokens.generate();
            claims.claimLaunching(claimed, Tokens.hash(runToken)).ifPresent(recordingId -> pending.add(
                    new PendingLaunch(recordingId, call.platform(), call.first().joinUrl(), runToken,
                            call.first().endsAt())));
        }
        return pending;
    }
}
