package app.lightmove.api.notetaker.service;

import app.lightmove.api.billing.usage.constant.UsageKind;
import app.lightmove.api.billing.usage.model.MeteredUse;
import app.lightmove.api.billing.usage.service.UsageRecorder;
import app.lightmove.api.candidate.model.FiledMeetingNote;
import app.lightmove.api.candidate.service.CandidateMeetingNotes;
import app.lightmove.api.core.security.service.UserTimezones;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.notetaker.model.AudioPiece;
import app.lightmove.api.notetaker.model.CaptionedLine;
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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Turns a run's audio into its transcript and its transcript into one Meeting note. While the call goes, each audio
 * piece is transcribed and its audio deleted; once the bot has left, the rest is transcribed, the pieces stitched and
 * named from the captions, the call summarised against its position's brief, and the note filed — once: a run whose
 * note is filed, or that is no longer waiting for one, is left alone however often it is handed here.
 *
 * <p>No model is asked inside a transaction. A claim committed first keeps every other instance off the run. Every
 * step runs in a transaction of its own, never the caller's: the worker is handed the run after the bot's request
 * committed, when that request's transaction resources may still be bound to the thread.
 */
@Component
@Slf4j
class MeetingNotesProcessor {

    /** A claim this old belonged to an instance that died; every model call is answered well inside it. */
    static final Duration STALE_CLAIM = Duration.ofMinutes(15);
    /** Automatic tries at a note before the run waits for a consultant to press Try again. */
    static final int ATTEMPTS = 3;
    private static final DateTimeFormatter LOCAL_TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotetakerWorkClaims claims;
    private final MeetingRecordingRepository recordings;
    private final MeetingMediaPieces pieces;
    private final MeetingTranscriptPieces transcripts;
    private final MeetingTranscriber transcriber;
    private final MeetingSummariser summariser;
    private final CandidateMeetingNotes meetingNotes;
    private final PositionService positions;
    private final UserTimezones timezones;
    private final UsageRecorder usage;
    private final DocumentStore store;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final JsonMapper json;
    private final Clock clock;

    MeetingNotesProcessor(NotetakerWorkClaims claims, MeetingRecordingRepository recordings, MeetingMediaPieces pieces,
                          MeetingTranscriptPieces transcripts, MeetingTranscriber transcriber,
                          MeetingSummariser summariser, CandidateMeetingNotes meetingNotes, PositionService positions,
                          UserTimezones timezones, UsageRecorder usage, DocumentStore store,
                          ApplicationEventPublisher events, PlatformTransactionManager transactionManager,
                          JsonMapper json, Clock clock) {
        this.claims = claims;
        this.recordings = recordings;
        this.pieces = pieces;
        this.transcripts = transcripts;
        this.transcriber = transcriber;
        this.summariser = summariser;
        this.meetingNotes = meetingNotes;
        this.positions = positions;
        this.timezones = timezones;
        this.usage = usage;
        this.store = store;
        this.events = events;
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.json = json;
        this.clock = clock;
    }

    void work(UUID recordingId) {
        Instant now = clock.instant();
        if (!Boolean.TRUE.equals(transactions.execute(status ->
                claims.claim(recordingId, now, now.minus(STALE_CLAIM))))) {
            return;
        }
        try {
            boolean heardEverything = transcribePending(recordingId);
            boolean processing = Boolean.TRUE.equals(transactions.execute(status ->
                    recordings.findById(recordingId).map(MeetingRecording::isProcessing).orElse(false)));
            if (processing) {
                if (heardEverything) {
                    finish(recordingId);
                } else {
                    failedOnce(recordingId, "a piece could not be transcribed");
                }
            }
        } catch (RuntimeException failed) {
            log.warn("Notetaker run {} could not be noted", recordingId, failed);
            failedOnce(recordingId, failed.toString());
        } finally {
            transactions.executeWithoutResult(status -> claims.release(recordingId));
        }
    }

    /** The whole transcript once the note is filed; until then, the lines heard so far. */
    List<TranscriptLine> transcriptOf(UUID recordingId) {
        return transactions.execute(status -> transcripts.transcriptOf(recordingId))
                .orElseGet(() -> assembled(recordingId));
    }

    private List<TranscriptLine> assembled(UUID recordingId) {
        List<TranscribedPiece> heard = transactions.execute(status -> transcripts.piecesOf(recordingId));
        return MeetingTranscriptAssembly.assemble(heard, captionsOf(recordingId));
    }

    /** Answers whether every piece now has its transcript; one that failed is tried again next time. */
    private boolean transcribePending(UUID recordingId) {
        boolean all = true;
        List<AudioPiece> waiting = transactions.execute(status -> pieces.untranscribedAudioOf(recordingId));
        for (AudioPiece piece : waiting) {
            try {
                List<HeardLine> heard = transcriber.transcribe(read(piece.storageKey()));
                transactions.executeWithoutResult(status -> transcripts.store(recordingId,
                        new TranscribedPiece(piece.sequence(), piece.startedAt(), heard)));
                store.delete(piece.storageKey());
                transactions.executeWithoutResult(status -> pieces.forget(recordingId, piece.storageKey()));
            } catch (IOException | RuntimeException failed) {
                log.warn("Notetaker run {} piece {} not transcribed yet: {}", recordingId, piece.sequence(),
                        failed.toString());
                all = false;
            }
        }
        return all;
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
        UUID projectId = transactions.execute(status -> positionOf(recording));
        AssessmentBrief brief = projectId == null ? null
                : transactions.execute(status -> positions.assessmentBriefOf(recording.getWorkspaceId(), projectId));
        Instant callStart = recording.getJoinedAt() != null ? recording.getJoinedAt()
                : transcript.getFirst().at();
        MeetingSummary summary = summariser.summarise(transcript, callStart, brief);
        String stoppedAt = recording.getRecordedUntil() == null ? null
                : LOCAL_TIME.format(recording.getRecordedUntil().atZone(
                        transactions.execute(status -> timezones.zoneOf(recording.getUserId()))));
        String body = MeetingNoteMarkdown.of(summary, stoppedAt);

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

    /**
     * The position rule: a call booked in Uncava belongs to the position it was booked on; otherwise to the one
     * position the consultant works that maps the person; otherwise to none, and the note is about the person.
     */
    private UUID positionOf(MeetingRecording recording) {
        if (recording.getPersonId() == null) {
            return null;
        }
        Optional<UUID> booked = meetingNotes.positionOfBookedCall(recording.getWorkspaceId(),
                recording.getPersonId(), recording.getStartsAt());
        if (booked.isPresent()) {
            return booked.get();
        }
        List<UUID> worked = meetingNotes.livePositionsWorkedBy(recording.getUserId(), recording.getWorkspaceId(),
                recording.getPersonId());
        return worked.size() == 1 ? worked.getFirst() : null;
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

    /** Each caption line arrives again as it grows; its latest reading is the one kept. */
    private List<CaptionedLine> captionsOf(UUID recordingId) {
        Map<Long, CaptionedLine> byId = new LinkedHashMap<>();
        for (String key : transactions.execute(status -> pieces.captionKeysOf(recordingId))) {
            try {
                JsonNode lines = json.readTree(read(key)).path("lines");
                for (JsonNode line : lines) {
                    CaptionedLine caption = new CaptionedLine(line.path("id").asLong(),
                            line.path("speaker").asString(""), line.path("text").asString(""),
                            Instant.parse(line.path("startedAt").asString()),
                            Instant.parse(line.path("updatedAt").asString()));
                    byId.merge(caption.id(), caption, (kept, later) ->
                            later.updatedAt().isBefore(kept.updatedAt()) ? kept : later);
                }
            } catch (IOException | RuntimeException unreadable) {
                log.warn("Notetaker run {} captions {} unreadable: {}", recordingId, key, unreadable.toString());
            }
        }
        List<CaptionedLine> captions = new ArrayList<>(byId.values());
        captions.sort(Comparator.comparing(CaptionedLine::startedAt));
        return captions;
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
