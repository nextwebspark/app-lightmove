package app.lightmove.api.notetaker.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.NotetakerSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.model.ClaimedCall;
import app.lightmove.api.notetaker.model.NotetakerCall;
import app.lightmove.api.notetaker.model.PendingLaunch;
import app.lightmove.api.outreach.service.KeptMeetings;
import app.lightmove.api.outreach.service.ZoomService;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute: ends the runs whose bot is gone, deletes what nobody will read, hands the calls whose audio or note
 * is waiting to {@link MeetingNotesWorker}, then sends a bot to each kept call about to start that
 * {@link NotetakerCallEligibility} reads as switched on. Safe on several instances at once — see
 * {@link MeetingRecordingClaims}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotetakerScheduler {

    private static final int RUNS_WORKED_PER_PASS = 20;

    private final KeptMeetings meetings;
    private final NotetakerCallEligibility eligibility;
    private final MeetingRecordingClaims claims;
    private final NotetakerLauncher launcher;
    private final NotetakerSweeper sweeper;
    private final NotetakerMediaPurge mediaPurge;
    private final NotetakerWorkClaims workClaims;
    private final MeetingNotesWorker notesWorker;
    private final ZoomService zoom;
    private final FairUseGuard fairUse;
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
            mediaPurge.purge(now);
        } catch (RuntimeException failed) {
            log.error("Notetaker media purge failed; launching anyway", failed);
        }
        try {
            Instant claim = NotetakerWorkClaims.claimOf(now);
            notesDue(now).forEach(recordingId -> notesWorker.workOnClaimed(recordingId, claim));
        } catch (RuntimeException failed) {
            log.error("Notetaker transcription pass failed; launching anyway", failed);
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
            Optional<NotetakerEndReason> refused = billingRefusalOf(claimed);
            if (refused.isPresent()) {
                claims.claimSkipped(claimed, refused.get());
                continue;
            }
            // A Zoom call is joined on the consultant's own Zoom account; without a usable one it is never tried.
            if (call.platform() == NotetakerPlatform.ZOOM
                    && !zoom.isUsableBy(claimed.sentFrom().userId(), claimed.sentFrom().workspaceId())) {
                claims.claimSkipped(claimed, NotetakerEndReason.ZOOM_NOT_CONNECTED);
                continue;
            }
            String runToken = Tokens.generate();
            claims.claimLaunching(claimed, Tokens.hash(runToken)).ifPresent(recordingId -> pending.add(
                    new PendingLaunch(recordingId, call.platform(), call.first().joinUrl(), runToken,
                            call.first().endsAt(), claimed.sentFrom().workspaceId(), claimed.sentFrom().userId(),
                            claimed.sentFrom().personId())));
        }
        return pending;
    }

    /**
     * Recorded minutes are a fair-use kind (#956), asked before a bot is sent: a workspace with any minute left this
     * billing month gets its bot, and the minutes it records are counted once its note is filed.
     */
    private Optional<NotetakerEndReason> billingRefusalOf(ClaimedCall claimed) {
        try {
            fairUse.check(claimed.sentFrom().workspaceId(), claimed.sentFrom().userId(), UsageKind.MEETING_MINUTES, 1);
            return Optional.empty();
        } catch (ApiException refused) {
            if (refused.getCode() == ErrorCode.FAIR_USE_REACHED) {
                return Optional.of(NotetakerEndReason.FAIR_USE_REACHED);
            }
            if (refused.getCode() == ErrorCode.TRIAL_ENDED) {
                return Optional.of(NotetakerEndReason.TRIAL_ENDED);
            }
            throw refused;
        }
    }

    private List<UUID> notesDue(Instant now) {
        return workClaims.claimDue(now, now.minus(properties.notetaker().transcribeEvery()),
                now.minus(MeetingNotesProcessor.STALE_CLAIM), RUNS_WORKED_PER_PASS);
    }
}
