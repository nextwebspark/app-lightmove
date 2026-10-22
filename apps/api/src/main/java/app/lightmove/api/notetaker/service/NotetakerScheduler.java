package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.NotetakerSettings;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.model.ClaimedCall;
import app.lightmove.api.notetaker.model.PendingLaunch;
import app.lightmove.api.outreach.model.KeptMeeting;
import app.lightmove.api.outreach.service.KeptMeetings;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Every minute: ends the runs whose bot is gone, then sends a bot to each kept call about to start whose three
 * switches are on. Safe on several instances at once — see {@link MeetingRecordingClaims}.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotetakerScheduler {

    private final KeptMeetings meetings;
    private final CandidateOutreachService people;
    private final NotetakerSwitches switches;
    private final MeetingRecordingClaims claims;
    private final NotetakerLauncher launcher;
    private final NotetakerSweeper sweeper;
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
        sweeper.failStale(now);
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
        Set<NotetakerPlatform> offered = offeredPlatforms(settings);
        Map<UUID, Boolean> workspacesAllowing = new HashMap<>();
        List<PendingLaunch> pending = new ArrayList<>();
        for (List<KeptMeeting> call : callsStartingAround(now, settings)) {
            KeptMeeting first = call.getFirst();
            Optional<NotetakerPlatform> platform = NotetakerPlatform.of(first.joinUrl(), first.conferencingProvider())
                    .filter(offered::contains);
            if (platform.isEmpty() || !first.endsAt().isAfter(now)
                    || !workspacesAllowing.computeIfAbsent(first.workspaceId(), switches::workspaceAllows)
                    || !switches.meetingAllows(call)) {
                continue;
            }
            Optional<KeptMeeting> sentFrom = call.stream()
                    .filter(meeting -> switches.userAllows(meeting.userId(), meeting.workspaceId()))
                    .min(Comparator.comparing((KeptMeeting meeting) -> meeting.userId().toString()));
            if (sentFrom.isEmpty()) {
                continue;
            }
            ClaimedCall claimed = new ClaimedCall(NotetakerCallKeys.of(first.joinUrl(), first.startsAt()),
                    platform.get(), sentFrom.get());
            Set<UUID> doNotContact = people.doNotContactAmong(first.workspaceId(),
                    call.stream().map(KeptMeeting::personId).toList());
            if (!doNotContact.isEmpty()) {
                claims.claimSkipped(claimed, NotetakerEndReason.DO_NOT_CONTACT);
                continue;
            }
            String runToken = Tokens.generate();
            claims.claimLaunching(claimed, Tokens.hash(runToken)).ifPresent(recordingId -> pending.add(
                    new PendingLaunch(recordingId, claimed.platform(), first.joinUrl(), runToken, first.endsAt())));
        }
        return pending;
    }

    /** The kept rows starting in the window, one list per call: teammates' calendars hold one call twice. */
    private List<List<KeptMeeting>> callsStartingAround(Instant now, NotetakerSettings settings) {
        Map<String, List<KeptMeeting>> calls = new LinkedHashMap<>();
        for (KeptMeeting meeting : meetings.startingBetween(now.minus(settings.joinLate()),
                now.plus(settings.joinAhead()))) {
            calls.computeIfAbsent(meeting.workspaceId() + "|" + NotetakerCallKeys.of(meeting.joinUrl(),
                    meeting.startsAt()), key -> new ArrayList<>()).add(meeting);
        }
        return List.copyOf(calls.values());
    }

    static Set<NotetakerPlatform> offeredPlatforms(NotetakerSettings settings) {
        Set<NotetakerPlatform> offered = EnumSet.noneOf(NotetakerPlatform.class);
        for (String platform : settings.platforms()) {
            if (platform != null && !platform.isBlank()) {
                offered.add(NotetakerPlatform.valueOf(platform.trim()));
            }
        }
        return offered;
    }
}
