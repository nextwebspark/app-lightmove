package app.lightmove.api.notetaker.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.candidate.model.FiledMeetingNote;
import app.lightmove.api.candidate.service.CandidateMeetingNotes;
import app.lightmove.api.core.security.service.UserTimezones;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.notetaker.model.AudioPiece;
import app.lightmove.api.notetaker.model.HeardLine;
import app.lightmove.api.notetaker.model.MeetingNotesFiled;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.MeetingSummary;
import app.lightmove.api.notetaker.model.TranscribedPiece;
import app.lightmove.api.notetaker.model.TranscriptLine;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.service.PositionService;
import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Turns a run's audio into its transcript and its transcript into one Meeting note. While the call goes, each audio
 * piece is transcribed and its audio deleted; once the bot has left and every piece is heard — or given up as a gap
 * after {@link #PIECE_ATTEMPTS} passes — the pieces are stitched and named from the captions, the call summarised
 * against its position's brief, and the note filed, once: a run whose note is filed, or that no longer waits for
 * one, is left alone however often it is handed here.
 *
 * <p>No model is asked inside a transaction, and the run's claim is refreshed before each piece, so an instance that
 * finds it taken over stops. Every step runs in a transaction of its own, never the caller's: the worker is handed
 * the run after the bot's request committed, when that request's transaction resources may still be bound.
 */
@Component
@Slf4j
class MeetingNotesProcessor {

    /** A claim unrefreshed this long belonged to an instance that died; it is refreshed before every model call. */
    static final Duration STALE_CLAIM = Duration.ofMinutes(15);
    /** Automatic tries at a summary before the run waits for a consultant to press Try again. */
    static final int ATTEMPTS = 3;
    /** Passes that fail on one piece before it is given up as a gap in the transcript. */
    static final int PIECE_ATTEMPTS = 3;
    private static final Duration PIECE_LENGTH = Duration.ofSeconds(30);
    /** How long a note waits for the backup's transcript after its bot left; past it, the note goes without. */
    static final Duration BACKUP_TRANSCRIPT_WAIT = Duration.ofHours(1);
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotetakerWorkClaims claims;
    private final MeetingRecordingRepository recordings;
    private final MeetingMediaPieces pieces;
    private final MeetingTranscriptPieces transcripts;
    private final CaptionPieces captions;
    private final MeetingTranscriber transcriber;
    private final MeetingSummariser summariser;
    private final MeetingPositionRule positionRule;
    private final CandidateMeetingNotes meetingNotes;
    private final PositionService positions;
    private final UserTimezones timezones;
    private final UsageRecorder usage;
    private final DocumentStore store;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    MeetingNotesProcessor(NotetakerWorkClaims claims, MeetingRecordingRepository recordings, MeetingMediaPieces pieces,
                          MeetingTranscriptPieces transcripts, CaptionPieces captions, MeetingTranscriber transcriber,
                          MeetingSummariser summariser, MeetingPositionRule positionRule,
                          CandidateMeetingNotes meetingNotes, PositionService positions, UserTimezones timezones,
                          UsageRecorder usage, DocumentStore store, ApplicationEventPublisher events,
                          PlatformTransactionManager transactionManager, Clock clock) {
        this.claims = claims;
        this.recordings = recordings;
        this.pieces = pieces;
        this.transcripts = transcripts;
        this.captions = captions;
        this.transcriber = transcriber;
        this.summariser = summariser;
        this.positionRule = positionRule;
        this.meetingNotes = meetingNotes;
        this.positions = positions;
        this.timezones = timezones;
        this.usage = usage;
        this.store = store;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.clock = clock;
    }

    /** Claims the run first; a run another instance holds is left to it. */
    void work(UUID recordingId) {
        Instant now = clock.instant();
        transactions.execute(status -> claims.claim(recordingId, now, now.minus(STALE_CLAIM)))
                .ifPresent(claim -> workClaimed(recordingId, claim));
    }

    /** For a run the caller already claimed at {@code claim}. */
    void workClaimed(UUID recordingId, Instant claim) {
        Instant held = claim;
        try {
            Optional<Instant> stillHeld = transcribeWaiting(recordingId, held);
            if (stillHeld.isEmpty()) {
                return;
            }
            held = stillHeld.get();
            boolean readyForNote = Boolean.TRUE.equals(transactions.execute(status ->
                    recordings.findById(recordingId).filter(MeetingRecording::isProcessing)
                            .filter(recording -> !waitsForBackup(recording)).isPresent()
                            && pieces.untranscribedAudioOf(recordingId).isEmpty()));
            if (readyForNote) {
                finish(recordingId);
            }
        } catch (RuntimeException failed) {
            log.warn("Notetaker run {} could not be noted", recordingId, failed);
            failedOnce(recordingId, failed.toString());
        } finally {
            Instant release = held;
            transactions.executeWithoutResult(status -> claims.release(recordingId, release));
        }
    }

    /** The whole transcript once the note is filed; until then, the lines heard so far. */
    List<TranscriptLine> transcriptOf(UUID recordingId) {
        return transactions.execute(status -> transcripts.transcriptOf(recordingId))
                .orElseGet(() -> assembled(recordingId));
    }

    /**
     * Transcribes every piece waiting, refreshing the claim before each. Answers the claim still held, or empty when
     * another instance has taken the run over and this one must stop. A piece that fails is counted and tried on a
     * later pass; one that has failed {@link #PIECE_ATTEMPTS} times is kept as a gap.
     */
    private Optional<Instant> transcribeWaiting(UUID recordingId, Instant claim) {
        Instant held = claim;
        for (AudioPiece piece : transactions.execute(status -> pieces.untranscribedAudioOf(recordingId))) {
            Instant current = held;
            Optional<Instant> refreshed = transactions.execute(status ->
                    claims.refresh(recordingId, current, clock.instant()));
            if (refreshed.isEmpty()) {
                log.info("Notetaker run {} was taken over by another instance", recordingId);
                return Optional.empty();
            }
            held = refreshed.get();
            if (piece.failedAttempts() >= PIECE_ATTEMPTS) {
                log.warn("Notetaker run {} piece {} given up after {} tries", recordingId, piece.sequence(),
                        piece.failedAttempts());
                transactions.executeWithoutResult(status ->
                        transcripts.storeSkipped(recordingId, piece.sequence(), piece.startedAt()));
                deleteAudio(recordingId, piece);
                continue;
            }
            List<HeardLine> heard;
            try {
                heard = transcriber.transcribe(read(piece.storageKey()));
            } catch (IOException | RuntimeException failed) {
                log.warn("Notetaker run {} piece {} not transcribed yet: {}", recordingId, piece.sequence(),
                        failed.toString());
                transactions.executeWithoutResult(status -> pieces.countFailedAttempt(recordingId, piece.sequence()));
                continue;
            }
            transactions.executeWithoutResult(status -> transcripts.store(recordingId,
                    new TranscribedPiece(piece.sequence(), piece.startedAt(), heard)));
            deleteAudio(recordingId, piece);
        }
        return Optional.of(held);
    }

    /** A file that will not delete keeps its row, so the purge deletes it once the run is done. */
    private void deleteAudio(UUID recordingId, AudioPiece piece) {
        try {
            store.delete(piece.storageKey());
        } catch (IOException | RuntimeException failed) {
            log.warn("Notetaker run {} piece {} audio left for the purge: {}", recordingId, piece.sequence(),
                    failed.toString());
            return;
        }
        transactions.executeWithoutResult(status -> pieces.forget(recordingId, piece.storageKey()));
    }

    private void finish(UUID recordingId) {
        MeetingRecording recording = transactions.execute(status -> recordings.findById(recordingId).orElseThrow());
        List<TranscriptLine> transcript = assembled(recordingId);
        // Nobody heard, or the person is gone from the workspace: the run ends with nothing to file.
        if (transcript.isEmpty() || recording.getPersonId() == null) {
            transactions.executeWithoutResult(status -> recordings.findById(recordingId)
                    .filter(MeetingRecording::isProcessing)
                    .ifPresent(fresh -> {
                        transcripts.keepTranscript(recordingId, transcript);
                        transcripts.forgetPieces(recordingId);
                        fresh.noted(null, null, clock.instant());
                    }));
            return;
        }
        UUID projectId = transactions.execute(status -> positionRule.positionOf(recording)).orElse(null);
        AssessmentBrief brief = projectId == null ? null
                : transactions.execute(status -> positions.assessmentBriefOf(recording.getWorkspaceId(), projectId));
        Instant callStart = recording.getJoinedAt() != null ? recording.getJoinedAt() : transcript.getFirst().at();
        MeetingSummary summary = summariser.summarise(transcript, callStart, brief);
        String body = recording.resumedByBackup()
                ? MeetingNoteMarkdown.of(summary, localTimeOf(recording, recording.getRecordedUntil()),
                        localTimeOf(recording, recording.getBackupJoinedAt()), missingMinutesOf(recordingId))
                : MeetingNoteMarkdown.of(summary, stoppedAtOf(recording), missingMinutesOf(recordingId));

        boolean filed = Boolean.TRUE.equals(transactions.execute(status -> {
            MeetingRecording fresh = recordings.findById(recordingId).orElse(null);
            if (fresh == null || !fresh.isProcessing() || fresh.getNoteId() != null || fresh.getPersonId() == null) {
                return false;
            }
            Optional<FiledMeetingNote> note = meetingNotes.file(fresh.getWorkspaceId(), fresh.getPersonId(),
                    projectId, fresh.getUserId(), body, recordingId);
            transcripts.keepTranscript(recordingId, transcript);
            transcripts.forgetPieces(recordingId);
            fresh.noted(note.map(FiledMeetingNote::projectId).orElse(null),
                    note.map(FiledMeetingNote::noteId).orElse(null), clock.instant());
            note.ifPresent(filedNote -> events.publishEvent(new MeetingNotesFiled(fresh.getWorkspaceId(),
                    fresh.getUserId(), filedNote.projectId(), fresh.getPersonId(), filedNote.candidateId(),
                    filedNote.noteId(), recordingId)));
            return note.isPresent();
        }));
        if (filed) {
            usage.record(new MeteredUse(recording.getWorkspaceId(), recording.getUserId(), projectId,
                    UsageKind.MEETING_MINUTES, minutesOf(transcript), "meeting:" + recordingId));
        }
    }

    private List<TranscriptLine> assembled(UUID recordingId) {
        List<TranscribedPiece> heard = transactions.execute(status -> transcripts.piecesOf(recordingId));
        return MeetingTranscriptAssembly.assemble(heard, captions.of(recordingId, transactions));
    }

    /** The local time a bot that stopped mid-call stopped, in the consultant's own zone; null for a whole call. */
    private String stoppedAtOf(MeetingRecording recording) {
        return recording.getRecordedUntil() == null ? null : localTimeOf(recording, recording.getRecordedUntil());
    }

    private String localTimeOf(MeetingRecording recording, Instant at) {
        return LOCAL_TIME.format(at.atZone(transactions.execute(status -> timezones.zoneOf(recording.getUserId()))));
    }

    /** A backup that got in sends its transcript after it leaves; the note waits for it, but not for ever. */
    private boolean waitsForBackup(MeetingRecording recording) {
        return recording.awaitsBackupTranscript() && (recording.getLeftAt() == null
                || recording.getLeftAt().plus(BACKUP_TRANSCRIPT_WAIT).isAfter(clock.instant()));
    }

    private int missingMinutesOf(UUID recordingId) {
        int skipped = transactions.execute(status -> transcripts.skippedCountOf(recordingId));
        return (int) ((skipped * PIECE_LENGTH.toSeconds() + 59) / 60);
    }

    private void failedOnce(UUID recordingId, String why) {
        transactions.executeWithoutResult(status -> recordings.findById(recordingId)
                .filter(MeetingRecording::isProcessing)
                .ifPresent(recording -> {
                    if (recording.processingFailed(ATTEMPTS, clock.instant())) {
                        log.warn("Notetaker run {} gave up on its note after {} tries: {}", recordingId, ATTEMPTS,
                                why);
                    }
                }));
    }

    private byte[] read(String key) throws IOException {
        try (InputStream content = store.open(key)) {
            return content.readAllBytes();
        }
    }

    private static int minutesOf(List<TranscriptLine> transcript) {
        long seconds = Duration.between(transcript.getFirst().at(), transcript.getLast().at()).toSeconds();
        return (int) Math.max(1, (seconds + 59) / 60);
    }
}
