package app.lightmove.api.notetaker.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.service.FairUseGuard;
import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.ratelimit.service.LlmBudget;
import app.lightmove.api.core.ratelimit.service.LlmBudgetGuard;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.core.security.service.UserTimezones;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.core.tenant.EveryWorkspace;
import app.lightmove.api.notetaker.constant.CallSuggestionStatus;
import app.lightmove.api.notetaker.constant.NotetakerJoin;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.dto.CandidateNotetakerResponse;
import app.lightmove.api.notetaker.dto.NotetakerRecordingResponse;
import app.lightmove.api.notetaker.dto.NotetakerTranscriptResponse;
import app.lightmove.api.notetaker.dto.TranscriptLineResponse;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.MeetingTranscriptReady;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.notetaker.model.RecallBotState;
import app.lightmove.api.notetaker.model.RecordingDownload;
import app.lightmove.api.notetaker.repository.MeetingLiveLineRepository;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import app.lightmove.api.outreach.model.KeptMeeting;
import app.lightmove.api.outreach.service.KeptMeetings;
import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The Notetaker as a consultant drives it from the executive drawer: send a Recall.ai bot to a call with the person,
 * call it back, try a failed note again, read the transcript. The bot is claimed in our database before Recall is
 * asked, so two presses (or two teammates on one call) never send two bots, and Recall is never called inside a
 * transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotetakerService {

    /** Spelled as the brand's wordmark, as its camera card draws it: the one name an executive reads in a call. */
    static final String BOT_NAME = "UNCAVA Notetaker";

    static final String LIVE_TRANSCRIPT_PATH = "/api/v1/notetaker/webhooks/recall/live";

    /** How long before the call the bot is asked in: the lobby opens a little early on every platform. */
    static final Duration JOIN_AHEAD = Duration.ofMinutes(1);

    /** Calls run over: a consultant who forgot the bot can still send it in this long after the booked end. */
    static final Duration OVERRUN_GRACE = Duration.ofMinutes(20);

    /** A scheduled bot due in the call sooner than this is left to join on its own rather than replaced. */
    static final Duration JOIN_NOW_LEAD = Duration.ofMinutes(2);

    /** What Recall says of a bot that has left its call; a leave refused for one of these has already happened. */
    private static final Set<String> LEFT_THE_CALL = Set.of("call_ended", "done", "fatal");

    private final RecallBotApi recall;
    private final LightMoveProperties properties;
    private final LlmBudgetGuard llmBudget;
    private final FairUseGuard fairUse;
    private final MeetingRecordingRepository recordings;
    private final MeetingLiveLineRepository liveLines;
    private final CopilotStreamPublisher copilotStream;
    private final KeptMeetings meetings;
    private final CandidateOutreachService people;
    private final UserRepository users;
    private final UserTimezones timezones;
    private final AuditService audit;
    private final EveryWorkspace workspaces;
    private final TransactionTemplate transactions;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    @Transactional(readOnly = true)
    public CandidateNotetakerResponse ofCandidate(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        Instant now = clock.instant();
        List<NotetakerRecordingResponse> rows = recordings
                .findByWorkspaceIdAndProjectIdAndPersonIdOrderByStartsAtDescCreatedAtDesc(workspaceId, projectId,
                        recipient.personId()).stream()
                .map(recording -> responseOf(recording, userId, now))
                .toList();
        if (!recall.isOffered() || recipient.doNotContact()) {
            return new CandidateNotetakerResponse(recall.isOffered(), rows, List.of(), List.of());
        }
        ZoneId zone = timezones.zoneOf(userId);
        List<KeptMeeting> calls = meetings.ofPerson(workspaceId, recipient.personId());
        List<KeptMeeting> recordable = calls.stream()
                .filter(meeting -> joinableOrNull(meeting.joinUrl()) != null
                        && isStillOpen(workspaceId, userId, meeting, now))
                .filter(meeting -> calls.stream()
                        .anyMatch(copy -> copy.isSameCallAs(meeting) && copy.isHeldOrBookedBy(userId)))
                .toList();
        return new CandidateNotetakerResponse(true, rows, recordable.stream().map(KeptMeeting::id).toList(),
                recordable.stream()
                        .filter(meeting -> hasCallDayBegun(meeting.startsAt(), now, zone))
                        .map(KeptMeeting::id)
                        .toList());
    }

    public NotetakerRecordingResponse schedule(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                               UUID meetingId, boolean liveCopilot, NotetakerJoin join,
                                               HttpServletRequest httpRequest) {
        if (!recall.isOffered()) {
            throw ApiException.of(ErrorCode.NOTETAKER_NOT_OFFERED);
        }
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        if (recipient.doNotContact()) {
            throw ApiException.of(ErrorCode.MEETING_DO_NOT_CONTACT);
        }
        KeptMeeting meeting = meetings.of(workspaceId, recipient.personId(), meetingId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        // A recorder joins a call only for someone it is theirs to record: on their own calendar, or booked by them.
        // A teammate's copy of the same call counts, since every calendar keeps its own row of it.
        if (meetings.ofPerson(workspaceId, recipient.personId()).stream()
                .noneMatch(copy -> copy.isSameCallAs(meeting) && copy.isHeldOrBookedBy(userId))) {
            throw ApiException.of(ErrorCode.NOTETAKER_NOT_YOUR_CALL);
        }
        Instant now = clock.instant();
        if (!isStillOpen(workspaceId, userId, meeting, now)) {
            throw ApiException.of(ErrorCode.NOTETAKER_MEETING_OVER);
        }
        String joinUrl = joinableOrNull(meeting.joinUrl());
        if (joinUrl == null) {
            throw ApiException.of(ErrorCode.NOTETAKER_NO_JOIN_LINK);
        }
        boolean atOnce = join == NotetakerJoin.NOW;
        if (atOnce) {
            requireLinkFree(workspaceId, userId, joinUrl, meeting.startsAt(), now);
        }
        // The note is one AI use, and a live copilot's coaching another.
        fairUse.check(workspaceId, userId, UsageKind.AI_ENRICH, liveCopilot ? 2 : 1);
        String liveToken = liveCopilot ? Tokens.generate() : null;
        UUID recordingId = claim(userId, workspaceId, projectId, recipient, meeting, joinUrl, liveCopilot,
                liveToken == null ? null : Tokens.hash(liveToken));
        Instant joinAt = atOnce ? null : joinAtFor(meeting.startsAt(), now);
        String botId;
        try {
            botId = recall.send(new RecallBotRequest(joinUrl, joinAt, BOT_NAME, consentLineFor(userId, liveCopilot),
                    metadataOf(recordingId, workspaceId), liveToken == null ? null : liveTranscriptUrl(liveToken)));
        } catch (VendorException | ApiException refused) {
            log.warn("Recall refused the Notetaker for recording {}: {}", recordingId, refused.getMessage());
            boolean busy = isBusy(refused);
            transactions.executeWithoutResult(status -> recordings.findById(recordingId)
                    .ifPresent(recording -> recording.failed(busy ? "RECALL_BUSY" : "SEND_FAILED")));
            throw ApiException.of(busy ? ErrorCode.NOTETAKER_BUSY : ErrorCode.NOTETAKER_SEND_FAILED);
        }
        MeetingRecording sent = transactions.execute(status -> {
            MeetingRecording recording = recordings.findById(recordingId).orElseThrow();
            recording.sent(botId, joinAt);
            audit.projectEvent(ProjectEventType.NOTETAKER_SCHEDULED, userId, workspaceId, projectId, httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("recordingId", recordingId.toString())
                    .detail("startsAt", meeting.startsAt().toString())
                    .detail("join", atOnce ? NotetakerJoin.NOW.name() : NotetakerJoin.AT_START.name())
                    .detail("liveCopilot", String.valueOf(liveCopilot))
                    .record();
            return recording;
        });
        if (sent.getStatus() == NotetakerStatus.CANCELLED) {
            // Removed while Recall was still being asked: the bot it just made must not go on to join and record.
            removeQuietly(botId);
        }
        return responseOf(sent, userId, clock.instant());
    }

    /** Anyone on the team may call a bot back: a recording nobody wanted is the thing to stop, whoever sent it. */
    public void cancel(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId, UUID recordingId,
                       HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = recordings
                .findByIdAndWorkspaceIdAndProjectIdAndPersonId(recordingId, workspaceId, projectId,
                        recipient.personId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        requireCancellable(recording);
        String removedBotId = recording.getRecallBotId();
        if (removedBotId != null) {
            try {
                recall.remove(removedBotId);
            } catch (VendorException failed) {
                log.warn("Recall could not call back bot {}: {}", removedBotId, failed.getMessage());
                throw ApiException.of(ErrorCode.NOTETAKER_SEND_FAILED);
            }
        }
        String botIdNow = transactions.execute(status -> {
            MeetingRecording held = recordings.findById(recordingId).orElseThrow();
            requireCancellable(held);
            callOff(held);
            audit.projectEvent(ProjectEventType.NOTETAKER_CANCELLED, userId, workspaceId, projectId, httpRequest)
                    .detail("candidateId", candidateId.toString())
                    .detail("recordingId", recordingId.toString())
                    .record();
            return held.getRecallBotId();
        });
        if (botIdNow != null && !botIdNow.equals(removedBotId)) {
            // The send finished between the read and the cancel: that bot is called back too.
            removeQuietly(botIdNow);
        }
        if (botIdNow != null) {
            deleteMediaQuietly(botIdNow);
        }
    }

    /**
     * A bot waits for its call by the time it was sent for. Once that call has moved, or gone from every calendar, the
     * bot would walk into the room at the old time — on a reused link, into somebody else's call — so it is called back,
     * and the consultant switches Join at start on again for the new time. A workspace at a time: read with no
     * workspace, every calendar would look empty and every waiting bot would be called back.
     */
    public int callBackMovedCalls(Instant now) {
        return workspaces.sum(workspaceId -> callBackMovedCallsInScope(now));
    }

    private int callBackMovedCallsInScope(Instant now) {
        List<MeetingRecording> waiting = transactions.execute(status ->
                recordings.findByStatusAndJoinAtAfter(NotetakerStatus.SCHEDULED, now));
        int calledBack = 0;
        for (MeetingRecording scheduled : waiting == null ? List.<MeetingRecording>of() : waiting) {
            if (scheduled.getRecallBotId() == null || isStillOnACalendar(scheduled)) {
                continue;
            }
            try {
                recall.remove(scheduled.getRecallBotId());
            } catch (VendorException failed) {
                if (failed.getKind() != VendorFailureKind.NOT_FOUND && failed.getKind() != VendorFailureKind.GONE) {
                    log.warn("Recall could not call back bot {} of a moved call: {}", scheduled.getRecallBotId(),
                            failed.getMessage());
                    continue;
                }
            }
            Boolean done = transactions.execute(status -> recordings.lockById(scheduled.getId())
                    .filter(held -> held.isScheduledAhead() && scheduled.getRecallBotId().equals(held.getRecallBotId()))
                    .map(held -> {
                        callOff(held);
                        audit.event(ProjectEventType.NOTETAKER_CANCELLED).actor(held.getRequestedBy())
                                .workspace(held.getWorkspaceId()).target(AuditService.PROJECT_TARGET, held.getProjectId())
                                .detail("candidateId", String.valueOf(held.getCandidateId()))
                                .detail("recordingId", held.getId().toString())
                                .detail("reason", "CALL_MOVED")
                                .record();
                        return true;
                    })
                    .orElse(false));
            if (Boolean.TRUE.equals(done)) {
                calledBack++;
            }
        }
        return calledBack;
    }

    private boolean isStillOnACalendar(MeetingRecording scheduled) {
        return meetings.ofPerson(scheduled.getWorkspaceId(), scheduled.getPersonId()).stream()
                .anyMatch(call -> call.startsAt().equals(scheduled.getStartsAt())
                        && scheduled.getJoinUrl().equals(joinableOrNull(call.joinUrl())));
    }

    private void callOff(MeetingRecording held) {
        held.cancelled();
        if (held.isLiveCopilot()) {
            liveLines.deleteByRecording(held.getId());
            copilotStream.publish(held.getId());
        }
    }

    /**
     * Brings a scheduled bot in at once. The new bot is sent before the scheduled one is called back, so a refusal —
     * an empty pool included — leaves the call with the bot it had; nothing is fair-use charged twice for one call.
     */
    public NotetakerRecordingResponse joinNow(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                              UUID recordingId, HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        if (recipient.doNotContact()) {
            throw ApiException.of(ErrorCode.MEETING_DO_NOT_CONTACT);
        }
        Instant now = clock.instant();
        ScheduledBot scheduled = transactions.execute(status -> {
            MeetingRecording held = recordings.lockForDecision(recordingId, workspaceId, projectId,
                    recipient.personId()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            if (!held.isScheduledAhead() || !held.getJoinAt().isAfter(now.plus(JOIN_NOW_LEAD))) {
                throw ApiException.of(ErrorCode.NOTETAKER_NOT_CHANGEABLE);
            }
            if (isLinkInUse(workspaceId, held.getRequestedBy(), held.getJoinUrl(), held.getStartsAt(), now)) {
                throw ApiException.of(ErrorCode.NOTETAKER_LINK_IN_USE);
            }
            ScheduledBot bot = new ScheduledBot(held.getRecallBotId(), held.getJoinAt(), held.getJoinUrl(),
                    held.getRequestedBy(), held.isLiveCopilot());
            held.joiningNow();
            return bot;
        });
        String liveToken = scheduled.liveCopilot() ? Tokens.generate() : null;
        String botId;
        try {
            botId = recall.send(new RecallBotRequest(scheduled.joinUrl(), null, BOT_NAME,
                    consentLineFor(scheduled.requestedBy(), scheduled.liveCopilot()),
                    metadataOf(recordingId, workspaceId), liveToken == null ? null : liveTranscriptUrl(liveToken)));
        } catch (VendorException | ApiException refused) {
            log.warn("Recall refused to bring recording {} in now: {}", recordingId, refused.getMessage());
            transactions.executeWithoutResult(status -> recordings.lockById(recordingId)
                    .filter(held -> held.getStatus() == NotetakerStatus.SCHEDULED
                            && scheduled.botId().equals(held.getRecallBotId()) && held.getJoinAt() == null)
                    .ifPresent(held -> held.stayScheduledFor(scheduled.joinAt())));
            throw ApiException.of(isBusy(refused) ? ErrorCode.NOTETAKER_BUSY : ErrorCode.NOTETAKER_SEND_FAILED);
        }
        MeetingRecording swapped = transactions.execute(status -> {
            MeetingRecording held = recordings.lockById(recordingId).orElseThrow();
            if (held.getStatus().isLive() && scheduled.botId().equals(held.getRecallBotId())) {
                held.sent(botId, null);
                if (liveToken != null) {
                    held.rekeyLive(Tokens.hash(liveToken));
                }
                audit.projectEvent(ProjectEventType.NOTETAKER_JOINED_NOW, userId, workspaceId, projectId, httpRequest)
                        .detail("candidateId", candidateId.toString())
                        .detail("recordingId", recordingId.toString())
                        .record();
            }
            return held;
        });
        if (botId.equals(swapped.getRecallBotId())) {
            removeQuietly(scheduled.botId());
        } else {
            // Removed while Recall was being asked: the remove took the scheduled bot, and this one must not join.
            removeQuietly(botId);
        }
        return responseOf(swapped, userId, clock.instant());
    }

    /**
     * End & file notes: the bot leaves a call that finished before its booked end, and the recording ends as any call
     * does — Recall's {@code call_ended}, then the transcript and the note. Anyone on the team may press it.
     */
    public NotetakerRecordingResponse endAndFile(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                                 UUID recordingId, HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        Instant now = clock.instant();
        String botId = transactions.execute(status -> {
            MeetingRecording held = recordings.lockForDecision(recordingId, workspaceId, projectId,
                    recipient.personId()).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
            if (held.isEnding()) {
                return null;
            }
            if (held.getStatus() != NotetakerStatus.RECORDING || held.getRecallBotId() == null) {
                throw ApiException.of(ErrorCode.NOTETAKER_NOT_RECORDING);
            }
            held.endRequested(userId, now);
            return held.getRecallBotId();
        });
        if (botId != null) {
            try {
                recall.leave(botId);
            } catch (VendorException refused) {
                if (!hasLeft(botId, refused)) {
                    log.warn("Recall would not take bot {} out of its call: {}", botId, refused.getMessage());
                    transactions.executeWithoutResult(status -> recordings.lockById(recordingId)
                            .filter(held -> held.getStatus() == NotetakerStatus.RECORDING)
                            .ifPresent(MeetingRecording::endRequestCleared));
                    throw ApiException.of(ErrorCode.NOTETAKER_END_FAILED);
                }
            }
        }
        return transactions.execute(status -> {
            MeetingRecording held = recordings.findById(recordingId).orElseThrow();
            if (botId != null) {
                audit.projectEvent(ProjectEventType.NOTETAKER_ENDED_EARLY, userId, workspaceId, projectId,
                                httpRequest)
                        .detail("candidateId", candidateId.toString())
                        .detail("recordingId", recordingId.toString())
                        .record();
            }
            return responseOf(held, userId, clock.instant());
        });
    }

    /** Recall answers a 400 to a bot already gone as it does to a request to retry, so the bot itself is asked. */
    private boolean hasLeft(String botId, VendorException refused) {
        if (refused.getKind() != VendorFailureKind.BAD_REQUEST && refused.getKind() != VendorFailureKind.GONE) {
            return false;
        }
        try {
            RecallBotState state = recall.state(botId);
            return state.statusCode() != null && LEFT_THE_CALL.contains(state.statusCode());
        } catch (VendorException unreadable) {
            return false;
        }
    }

    /** Try again is a press that spends a model call, so it takes the same meter Assess with AI does. */
    @Transactional
    public NotetakerRecordingResponse retry(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                            UUID recordingId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = recordings
                .findByIdAndWorkspaceIdAndProjectIdAndPersonId(recordingId, workspaceId, projectId,
                        recipient.personId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!recording.isRetryable()) {
            throw ApiException.of(ErrorCode.NOTETAKER_NOT_CHANGEABLE);
        }
        llmBudget.require(LlmBudget.CANDIDATE_AI_ENRICH, userId);
        fairUse.check(workspaceId, userId, UsageKind.AI_ENRICH, 1);
        recording.retrying();
        recordings.releaseProcessing(recording.getId());
        events.publishEvent(new MeetingTranscriptReady(recording.getId()));
        return responseOf(recording, userId, clock.instant());
    }

    /**
     * Checks that the caller sent this bot and that Recall still holds its video, and audits the download. Streaming
     * follows: the link is read here, before a byte is sent, so a gone recording answers 410 rather than a broken file.
     */
    public RecordingDownload openRecording(UUID userId, UUID workspaceId, UUID projectId, UUID candidateId,
                                           UUID recordingId, HttpServletRequest httpRequest) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = recordings
                .findByIdAndWorkspaceIdAndProjectIdAndPersonId(recordingId, workspaceId, projectId,
                        recipient.personId())
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        if (!recording.getRequestedBy().equals(userId)) {
            throw ApiException.of(ErrorCode.NOTETAKER_RECORDING_NOT_YOURS);
        }
        if (!recording.isRecordingKept(clock.instant())) {
            throw ApiException.of(ErrorCode.NOTETAKER_RECORDING_GONE);
        }
        URI video;
        try {
            video = recall.recordingVideo(recording.getRecallBotId())
                    .orElseThrow(() -> ApiException.of(ErrorCode.NOTETAKER_RECORDING_GONE));
        } catch (VendorException failed) {
            log.warn("Notetaker recording {}: Recall could not give the video: {}", recordingId, failed.getMessage());
            throw ApiException.of(ErrorCode.NOTETAKER_RECORDING_UNAVAILABLE);
        }
        audit.projectEvent(ProjectEventType.NOTETAKER_RECORDING_DOWNLOADED, userId, workspaceId, projectId,
                        httpRequest)
                .detail("candidateId", candidateId.toString())
                .detail("recordingId", recordingId.toString())
                .record();
        return new RecordingDownload(video, recordingFileName(recording));
    }

    public void copyRecording(RecordingDownload download, OutputStream out) throws IOException {
        recall.copyVideo(download.video(), out);
    }

    static String recordingFileName(MeetingRecording recording) {
        String title = recording.getTitle() == null || recording.getTitle().isBlank() ? "Call"
                : recording.getTitle().strip().replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        String date = recording.getStartsAt().atZone(ZoneOffset.UTC).toLocalDate().toString();
        return title + " " + date + ".mp4";
    }

    @Transactional(readOnly = true)
    public NotetakerTranscriptResponse transcript(UUID workspaceId, UUID projectId, UUID candidateId,
                                                  UUID recordingId) {
        OutreachRecipient recipient = requireRecipient(workspaceId, projectId, candidateId);
        MeetingRecording recording = recordings
                .findByIdAndWorkspaceIdAndProjectIdAndPersonId(recordingId, workspaceId, projectId,
                        recipient.personId())
                .filter(found -> found.getTranscript() != null)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        return new NotetakerTranscriptResponse(recording.getId(), recording.getTitle(), recording.getStartsAt(),
                recording.getTranscript().stream()
                        .map(line -> new TranscriptLineResponse(line.at(), line.speaker(), line.text()))
                        .toList());
    }

    /**
     * Past its booked end a call is open only while nothing else began on its link: on a personal room used back to
     * back, the bot would otherwise record the next interview and file it on this person.
     */
    private boolean isStillOpen(UUID workspaceId, UUID userId, KeptMeeting meeting, Instant now) {
        if (meeting.endsAt().isAfter(now)) {
            return true;
        }
        return isStillJoinable(meeting.endsAt(), now) && !meetings.linkReusedSince(workspaceId, userId, meeting, now);
    }

    static boolean isStillJoinable(Instant endsAt, Instant now) {
        return endsAt.plus(OVERRUN_GRACE).isAfter(now);
    }

    /** Recall asks the bot in just before the call; one due within the minute goes at once. */
    static Instant joinAtFor(Instant startsAt, Instant now) {
        Instant joinAt = startsAt.minus(JOIN_AHEAD);
        return joinAt.isAfter(now.plus(JOIN_AHEAD)) ? joinAt : null;
    }

    /**
     * The call's day has begun at midnight in the consultant's time zone. Send now is never refused before it — a call
     * can start early — but the drawer asks first, since a link used on another day may hold another meeting.
     */
    static boolean hasCallDayBegun(Instant startsAt, Instant now, ZoneId zone) {
        return !now.isBefore(startsAt.atZone(zone).toLocalDate().atStartOfDay(zone).toInstant());
    }

    private void requireLinkFree(UUID workspaceId, UUID userId, String joinUrl, Instant startsAt, Instant now) {
        if (isLinkInUse(workspaceId, userId, joinUrl, startsAt, now)) {
            throw ApiException.of(ErrorCode.NOTETAKER_LINK_IN_USE);
        }
    }

    /**
     * A bot sent at once into a room still holding an earlier call would record that call on this person: one on the
     * consultant's calendar, or one any teammate's bot is in.
     */
    private boolean isLinkInUse(UUID workspaceId, UUID userId, String joinUrl, Instant startsAt, Instant now) {
        return meetings.linkBusyBefore(workspaceId, userId, joinUrl, startsAt, now)
                || recordings.existsByWorkspaceIdAndJoinUrlAndStartsAtNotAndStatusIn(workspaceId, joinUrl, startsAt,
                        NotetakerStatus.IN_CALL);
    }

    private static boolean isBusy(RuntimeException refused) {
        return refused instanceof ApiException api && api.getCode() == ErrorCode.NOTETAKER_BUSY;
    }

    private static Map<String, String> metadataOf(UUID recordingId, UUID workspaceId) {
        return Map.of("recordingId", recordingId.toString(), "workspaceId", workspaceId.toString());
    }

    /** Only an https link goes to Recall; a dial-in number or a room name is not something a bot can open. */
    static String joinableOrNull(String joinUrl) {
        if (joinUrl == null || joinUrl.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(joinUrl.strip());
            return "https".equals(uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT))
                    && uri.getHost() != null && uri.getUserInfo() == null ? uri.toString() : null;
        } catch (IllegalArgumentException unreadable) {
            return null;
        }
    }

    /**
     * Where Recall streams a live copilot's transcript: our own public endpoint, with a token that is this bot's alone,
     * so a delivery is taken only for the recording it names.
     */
    URI liveTranscriptUrl(String liveToken) {
        String publicBaseUrl = properties.recall().publicBaseUrl();
        String base = publicBaseUrl == null || publicBaseUrl.isBlank() ? properties.web().baseUrl() : publicBaseUrl;
        return URI.create(base.replaceAll("/+$", "") + LIVE_TRANSCRIPT_PATH + "?t=" + liveToken);
    }

    private UUID claim(UUID userId, UUID workspaceId, UUID projectId, OutreachRecipient recipient,
                       KeptMeeting meeting, String joinUrl, boolean liveCopilot, String liveTokenHash) {
        try {
            return transactions.execute(status -> {
                if (recordings.existsByWorkspaceIdAndJoinUrlAndStartsAtAndStatusIn(workspaceId, joinUrl,
                        meeting.startsAt(), NotetakerStatus.LIVE)) {
                    throw ApiException.of(ErrorCode.NOTETAKER_ALREADY_SCHEDULED);
                }
                MeetingRecording claimed = MeetingRecording.claimed(workspaceId, recipient.personId(), projectId,
                        recipient.candidateId(), meeting.id(), meeting.title(), meeting.startsAt(), meeting.endsAt(),
                        joinUrl, userId);
                claimed.liveCopilot(liveCopilot, userId, liveTokenHash);
                return recordings.saveAndFlush(claimed).getId();
            });
        } catch (DataIntegrityViolationException raced) {
            throw ApiException.of(ErrorCode.NOTETAKER_ALREADY_SCHEDULED);
        }
    }

    /** Nobody is recorded without being told, and told whose notes they are, and that an AI follows it live. */
    String consentLineFor(UUID userId, boolean liveCopilot) {
        String name = users.findById(userId).map(User::getFullName).map(String::strip).filter(full -> !full.isEmpty())
                .orElse("the consultant");
        return "Hi everyone, I'm " + BOT_NAME + ", the AI notetaker for " + name + ". This call is being recorded and"
                + " transcribed so " + name + " can keep accurate notes"
                + (liveCopilot ? ", and an AI assistant follows it live to help them" : "")
                + ". The recording stays private to " + name + "'s team. If you'd prefer not to be recorded, just say so"
                + " and I'll be removed.";
    }

    /** A recording whose bot was asked to leave is filing its notes: discarding it now would race that. */
    private static void requireCancellable(MeetingRecording recording) {
        if (recording.isEnding() || (recording.getStatus() != NotetakerStatus.SCHEDULED
                && recording.getStatus() != NotetakerStatus.JOINING
                && recording.getStatus() != NotetakerStatus.RECORDING)) {
            throw ApiException.of(ErrorCode.NOTETAKER_NOT_CHANGEABLE);
        }
    }

    void removeQuietly(String botId) {
        try {
            recall.remove(botId);
        } catch (VendorException failed) {
            if (failed.getKind() == VendorFailureKind.NOT_FOUND || failed.getKind() == VendorFailureKind.GONE) {
                return;
            }
            log.error("Recall could not call back bot {}; remove it from Recall's dashboard: {}", botId,
                    failed.getMessage());
        }
    }

    void deleteMediaQuietly(String botId) {
        try {
            recall.deleteMedia(botId);
        } catch (VendorException failed) {
            log.warn("Recall kept the media of cancelled bot {}: {}", botId, failed.getMessage());
        }
    }

    private OutreachRecipient requireRecipient(UUID workspaceId, UUID projectId, UUID candidateId) {
        return people.currentRecipient(workspaceId, projectId, candidateId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    /** {@code mayChangeCopilot}: only the bot's sender switches its copilot, since it reconfigures their bot. */
    static NotetakerRecordingResponse responseOf(MeetingRecording recording, UUID userId, Instant now) {
        boolean heldRecording = recording.isRecordingKept(now);
        return new NotetakerRecordingResponse(recording.getId(), recording.getMeetingId(), recording.getStartsAt(),
                recording.getTitle(), recording.getStatus(), recording.getFailureReason(),
                recording.getTranscript() != null, recording.isRetryable(), recording.getNoteId(),
                recording.getSuggestions() != null, recording.suggestionsWith(CallSuggestionStatus.PENDING),
                recording.suggestionsWith(CallSuggestionStatus.APPLIED),
                recording.getSuggestions() == null ? 0 : recording.getSuggestions().size(),
                recording.isSuggesting(now.minus(CallSuggestionService.CLAIM_LIFETIME)), recording.isLiveCopilot(),
                recording.getLiveStatus(), recording.getRequestedBy().equals(userId),
                recording.getJoinAt(), recording.getRecordingStartedAt(), recording.isEnding(),
                heldRecording ? recording.getMediaExpiresAt() : null,
                heldRecording && recording.getRequestedBy().equals(userId));
    }

    /** The bot Join now replaces, as the claim found it: put back if Recall will not send the one replacing it. */
    private record ScheduledBot(String botId, Instant joinAt, String joinUrl, UUID requestedBy, boolean liveCopilot) {}
}
