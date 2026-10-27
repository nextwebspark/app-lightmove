package app.lightmove.api.notetaker.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.candidate.service.PersonNoteService;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.MeetingSummary;
import app.lightmove.api.notetaker.model.MeetingTranscriptReady;
import app.lightmove.api.notetaker.model.TranscriptLine;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.service.PositionService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns a finished recording into its Meeting note, off the webhook's thread: the transcript from Recall, a summary
 * from the model against the position's brief, the note filed in the scheduler's name, and Recall's copy of the call
 * deleted, then Suggest changes asked of the transcript with no press. Not {@code @Transactional}: neither vendor call
 * may hold a connection, so each write is its own short transaction. Recall's copy is deleted only once the note is
 * filed, so Try again can still read the transcript.
 */
@Component
@Slf4j
class MeetingNotesWorker {

    /** Longer than a transcript read and a summary take; past it, the worker holding the claim is taken for dead. */
    static final Duration CLAIM_LIFETIME = Duration.ofMinutes(15);

    private final RecallBotApi recall;
    private final MeetingRecordingRepository recordings;
    private final MeetingSummariser summariser;
    private final PositionService positions;
    private final PersonNoteService notes;
    private final AuditService audit;
    private final UsageRecorder usage;
    private final CallSuggestionService suggestions;
    private final Clock clock;
    private final TransactionTemplate transactions;

    MeetingNotesWorker(RecallBotApi recall, MeetingRecordingRepository recordings, MeetingSummariser summariser,
                       PositionService positions, PersonNoteService notes, AuditService audit, UsageRecorder usage,
                       CallSuggestionService suggestions, Clock clock, PlatformTransactionManager transactionManager) {
        this.recall = recall;
        this.recordings = recordings;
        this.summariser = summariser;
        this.positions = positions;
        this.notes = notes;
        this.audit = audit;
        this.usage = usage;
        this.suggestions = suggestions;
        this.clock = clock;
        // An after-commit listener run on the publishing thread would otherwise join the finished transaction, and
        // every write here would be silently dropped with it.
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void onTranscriptReady(MeetingTranscriptReady event) {
        process(event.recordingId());
    }

    void process(UUID recordingId) {
        Instant now = clock.instant();
        // One worker per transcript, however many times it was handed over: the second spends nothing.
        Integer claimed = transactions.execute(status ->
                recordings.claimProcessing(recordingId, now, now.minus(CLAIM_LIFETIME)));
        if (claimed == null || claimed == 0) {
            return;
        }
        MeetingRecording recording = transactions.execute(status -> recordings.findById(recordingId).orElse(null));
        if (recording == null) {
            return;
        }
        List<TranscriptLine> lines = recording.getTranscript();
        if (lines == null) {
            try {
                lines = recall.transcript(recording.getTranscriptId());
            } catch (VendorException | IllegalStateException unavailable) {
                log.warn("Notetaker recording {}: the transcript could not be read: {}", recordingId,
                        unavailable.getMessage());
                fail(recordingId, "TRANSCRIPT_UNAVAILABLE");
                return;
            }
            List<TranscriptLine> heard = lines;
            transactions.executeWithoutResult(status ->
                    recordings.findById(recordingId).ifPresent(found -> found.transcribed(heard)));
        }
        if (lines.isEmpty()) {
            fail(recordingId, "NOTHING_HEARD");
            return;
        }
        MeetingSummary summary;
        try {
            summary = summariser.summarise(lines, callStartOf(lines, recording.getStartsAt()),
                    briefOrNull(recording));
        } catch (RuntimeException unusable) {
            log.warn("Notetaker recording {}: the summary could not be written", recordingId, unusable);
            fail(recordingId, "SUMMARY_FAILED");
            return;
        }
        String body = MeetingNoteMarkdown.of(summary);
        Integer claimedForChanges = transactions.execute(status -> {
            MeetingRecording held = recordings.findById(recordingId).orElseThrow();
            if (held.getStatus() != NotetakerStatus.PROCESSING || held.getNoteId() != null) {
                return null;
            }
            UUID noteId = notes.fileMeetingNote(held.getWorkspaceId(), held.getPersonId(), held.getProjectId(),
                    held.getRequestedBy(), body);
            held.filed(noteId);
            audit.event(ProjectEventType.NOTETAKER_NOTE_FILED).actor(held.getRequestedBy())
                    .workspace(held.getWorkspaceId()).target(AuditService.PROJECT_TARGET, held.getProjectId())
                    .detail("recordingId", recordingId.toString())
                    .detail("noteId", noteId.toString())
                    .detail("lines", String.valueOf(held.getTranscript().size()))
                    .record();
            return recordings.claimSuggestingOnFiling(recordingId, clock.instant());
        });
        if (claimedForChanges == null) {
            return;
        }
        usage.record(new MeteredUse(recording.getWorkspaceId(), recording.getRequestedBy(), recording.getProjectId(),
                UsageKind.AI_ENRICH, 1, "notetaker:" + recordingId));
        if (claimedForChanges > 0) {
            suggestions.suggestOnceFiled(recordingId);
        }
    }

    /** The first word said, which is what the summary's times count from; the calendar's start without one. */
    static Instant callStartOf(List<TranscriptLine> lines, Instant scheduledStart) {
        return lines.stream().map(TranscriptLine::at).min(Comparator.naturalOrder()).orElse(scheduledStart);
    }

    private AssessmentBrief briefOrNull(MeetingRecording recording) {
        try {
            return positions.assessmentBriefOf(recording.getWorkspaceId(), recording.getProjectId());
        } catch (RuntimeException unreadable) {
            log.info("Notetaker recording {}: no brief to summarise against", recording.getId());
            return null;
        }
    }

    private void fail(UUID recordingId, String reason) {
        transactions.executeWithoutResult(status ->
                recordings.findById(recordingId).ifPresent(found -> found.failed(reason)));
    }
}
