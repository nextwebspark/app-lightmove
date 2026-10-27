package app.lightmove.api.notetaker.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.candidate.service.CandidateCallUpdates;
import app.lightmove.api.core.config.RecallSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.notetaker.constant.CoachState;
import app.lightmove.api.notetaker.constant.RecordGap;
import app.lightmove.api.notetaker.model.InterviewPlan;
import app.lightmove.api.notetaker.model.InterviewQuestion;
import app.lightmove.api.notetaker.model.LiveCoaching;
import app.lightmove.api.notetaker.model.LiveLinesHeard;
import app.lightmove.api.notetaker.model.MeetingLiveLine;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.repository.MeetingLiveLineRepository;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import app.lightmove.api.notetaker.service.InterviewCoach.HeardLine;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.service.PositionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Paces the live copilot's coach: a run is due once enough new talk has arrived — a few lines, or a stretch of speech —
 * and never sooner than {@code copilot-coach-interval} after the last, up to {@code copilot-max-coach-runs} a call.
 * New lines wake it after their commit; a sweep catches a call that went quiet with lines still unread. One run at a
 * time per call, by a claim on the recording; the model call holds no transaction.
 */
@Component
@Slf4j
public class InterviewCoachWorker {

    static final int LINES_FOR_A_RUN = 3;
    static final Duration SPEECH_FOR_A_RUN = Duration.ofSeconds(20);
    /** A line left unread this long is read whatever came after it, so a quiet call still gets its last reading. */
    static final Duration LONGEST_WAIT = Duration.ofSeconds(20);
    static final Duration CLAIM_LIFETIME = Duration.ofSeconds(90);
    static final int FAILURES_BEFORE_PAUSE = 3;

    private static final String PENDING = """
            SELECT r.id FROM app_lm_meeting_recording r
            WHERE r.live_copilot AND r.live_status = 'LISTENING' AND r.status IN ('JOINING', 'RECORDING')
              AND EXISTS (SELECT 1 FROM app_lm_meeting_live_line l
                          WHERE l.recording_id = r.id AND NOT l.partial AND l.seq > r.coached_through)
            """;

    private final MeetingRecordingRepository recordings;
    private final MeetingLiveLineRepository lines;
    private final InterviewCoach coach;
    private final InterviewPlanService plans;
    private final PositionService positions;
    private final CandidateCallUpdates updates;
    private final TeamVoices teamVoices;
    private final CopilotStreamPublisher publisher;
    private final UsageRecorder usage;
    private final RecallSettings settings;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final Clock clock;

    InterviewCoachWorker(MeetingRecordingRepository recordings, MeetingLiveLineRepository lines, InterviewCoach coach,
                         InterviewPlanService plans, PositionService positions, CandidateCallUpdates updates,
                         TeamVoices teamVoices, CopilotStreamPublisher publisher, UsageRecorder usage,
                         LightMoveProperties properties, JdbcTemplate jdbc,
                         PlatformTransactionManager transactionManager, Clock clock) {
        this.recordings = recordings;
        this.lines = lines;
        this.coach = coach;
        this.plans = plans;
        this.positions = positions;
        this.updates = updates;
        this.teamVoices = teamVoices;
        this.publisher = publisher;
        this.usage = usage;
        this.settings = properties.recall();
        this.jdbc = jdbc;
        // Woken after another transaction's commit, often on its thread: every write here is a transaction of its own.
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLinesHeard(LiveLinesHeard heard) {
        coachIfDue(heard.recordingId(), clock.instant());
    }

    @Scheduled(fixedDelayString = "${lightmove.recall.copilot-coach-interval}",
            initialDelayString = "${lightmove.recall.copilot-coach-interval}")
    public void sweep() {
        if (settings.isBotOffered()) {
            sweepAt(clock.instant());
        }
    }

    public void sweepAt(Instant now) {
        for (UUID recordingId : jdbc.queryForList(PENDING, UUID.class)) {
            try {
                coachIfDue(recordingId, now);
            } catch (RuntimeException failed) {
                log.warn("Live copilot sweep could not coach recording {}: {}", recordingId, failed.toString());
            }
        }
    }

    /** Answers whether a run happened, whatever it found. */
    public boolean coachIfDue(UUID recordingId, Instant now) {
        CoachTurn turn = transactions.execute(status -> turnOf(recordingId, now));
        if (turn == null) {
            return false;
        }
        Integer claimed = transactions.execute(status ->
                recordings.claimCoaching(recordingId, now, now.minus(CLAIM_LIFETIME)));
        if (claimed == null || claimed == 0) {
            return false;
        }
        try {
            run(turn, now);
            return true;
        } finally {
            transactions.executeWithoutResult(status -> recordings.releaseCoaching(recordingId));
        }
    }

    /** Null when no run is due: not listening, out of runs, too soon, or not enough new talk. */
    private CoachTurn turnOf(UUID recordingId, Instant now) {
        MeetingRecording recording = recordings.findById(recordingId).orElse(null);
        if (recording == null || !recording.isListening()) {
            return null;
        }
        if (recording.getCoachRuns() >= settings.copilotMaxCoachRuns()) {
            LiveCoaching reading = recording.getLive() == null ? LiveCoaching.empty() : recording.getLive();
            if (reading.coach() != CoachState.CAPPED) {
                recording.liveCoaching(reading.withCoach(CoachState.CAPPED));
                publisher.publish(recordingId);
            }
            return null;
        }
        if (recording.getCoachedAt() != null
                && recording.getCoachedAt().plus(settings.copilotCoachInterval()).isAfter(now)) {
            return null;
        }
        List<MeetingLiveLine> unread = lines.findByRecordingIdAndPartialFalseAndSeqGreaterThanOrderBySeqAsc(
                recordingId, recording.getCoachedThrough());
        if (!isEnoughTalk(unread, now)) {
            return null;
        }
        return new CoachTurn(recording.getId(), recording.getWorkspaceId(), recording.getProjectId(),
                recording.getPersonId(), recording.getCandidateId(), recording.getStartsAt(),
                recording.callClockStart(), recording.getTitle(), InterviewCopilotService.teamOf(recording),
                unread.getLast().getSeq(), !recording.isLiveCharged(), recording.getRequestedBy());
    }

    static boolean isEnoughTalk(List<MeetingLiveLine> unread, Instant now) {
        if (unread.isEmpty()) {
            return false;
        }
        Instant first = unread.getFirst().getSaidAt();
        Instant last = unread.getLast().getSaidAt();
        return unread.size() >= LINES_FOR_A_RUN
                || !Duration.between(first, last).minus(SPEECH_FOR_A_RUN).isNegative()
                || !first.plus(LONGEST_WAIT).isAfter(now);
    }

    private void run(CoachTurn turn, Instant now) {
        LiveCoaching reading;
        try {
            reading = readCall(turn);
        } catch (InterviewCoach.CoachGuarded guarded) {
            log.info("Live copilot skipped a guarded stretch of recording {}", turn.recordingId());
            transactions.executeWithoutResult(status -> recordings.findById(turn.recordingId()).ifPresent(held -> {
                held.coachSkipped(turn.throughSeq(), clock.instant());
                publisher.publish(held.getId());
            }));
            return;
        } catch (RuntimeException failed) {
            log.warn("Live copilot run failed for recording {}: {}", turn.recordingId(), failed.toString());
            transactions.executeWithoutResult(status -> recordings.findById(turn.recordingId()).ifPresent(held -> {
                held.coachFailed(held.getCoachFailures() + 1 >= FAILURES_BEFORE_PAUSE ? CoachState.PAUSED
                        : coachStateOf(held), clock.instant());
                publisher.publish(held.getId());
            }));
            return;
        }
        transactions.executeWithoutResult(status -> recordings.findById(turn.recordingId()).ifPresent(held -> {
            held.coached(reconciled(reading, held.getLive()), turn.throughSeq(), clock.instant());
            publisher.publish(held.getId());
        }));
        if (turn.firstRun()) {
            // A live call is one AI use, counted once its first reading is on file whatever it goes on to cost; a
            // call no run ever answered costs nothing. The key keeps it to one.
            usage.record(new MeteredUse(turn.workspaceId(), turn.requestedBy(), turn.projectId(), UsageKind.AI_ENRICH,
                    1, "interview-copilot:" + turn.recordingId()));
            transactions.executeWithoutResult(status ->
                    recordings.findById(turn.recordingId()).ifPresent(MeetingRecording::liveCharged));
        }
    }

    private LiveCoaching readCall(CoachTurn turn) {
        AssessmentBrief brief = positions.assessmentBriefOf(turn.workspaceId(), turn.projectId());
        List<InterviewQuestion> plan = plans.planOfCall(turn.workspaceId(), turn.projectId(), turn.personId(),
                        turn.startsAt(), turn.title())
                .map(InterviewPlan::includedQuestions)
                .orElse(List.of());
        Set<RecordGap> missing = missingOf(turn);
        Set<String> team = teamVoices.namesOf(turn.workspaceId(), turn.team());
        List<HeardLine> heard = transactions.execute(status ->
                lines.findByRecordingIdAndPartialFalseOrderBySeqAsc(turn.recordingId()).stream()
                        .filter(line -> line.getSeq() <= turn.throughSeq())
                        .map(line -> new HeardLine(line.getSaidAt(), line.getSpeaker(), line.getText(),
                                TeamVoices.isTeam(team, line.getSpeaker(), line.isNamed())))
                        .toList());
        LiveCoaching before = transactions.execute(status -> recordings.findById(turn.recordingId())
                .map(MeetingRecording::getLive).orElse(null));
        return coach.coach(brief, plan, missing, before, heard, turn.clockStart());
    }

    /** An unmapped executive has no record to read gaps from; the copilot then follows the competencies alone. */
    private Set<RecordGap> missingOf(CoachTurn turn) {
        if (turn.candidateId() == null) {
            return EnumSet.noneOf(RecordGap.class);
        }
        try {
            List<RecordGap> missing = InterviewPlanService.missingOf(
                    updates.heldOf(turn.workspaceId(), turn.projectId(), turn.candidateId()));
            return missing.isEmpty() ? EnumSet.noneOf(RecordGap.class) : EnumSet.copyOf(missing);
        } catch (ApiException gone) {
            return EnumSet.noneOf(RecordGap.class);
        }
    }

    /** A question ticked as asked while the model was reading stays ticked. */
    static LiveCoaching reconciled(LiveCoaching reading, LiveCoaching meanwhile) {
        if (meanwhile == null) {
            return reading;
        }
        Set<String> asked = new LinkedHashSet<>(reading.asked());
        asked.addAll(meanwhile.asked());
        return reading.withAsked(List.copyOf(asked));
    }

    private static CoachState coachStateOf(MeetingRecording recording) {
        return recording.getLive() == null ? CoachState.RUNNING : recording.getLive().coach();
    }

    private record CoachTurn(UUID recordingId, UUID workspaceId, UUID projectId, UUID personId, UUID candidateId,
                             Instant startsAt, Instant clockStart, String title, List<UUID> team, long throughSeq,
                             boolean firstRun, UUID requestedBy) {}
}
