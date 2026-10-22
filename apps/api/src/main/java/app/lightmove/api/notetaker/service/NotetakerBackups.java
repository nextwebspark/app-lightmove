package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerStatus;
import app.lightmove.api.notetaker.model.HeardLine;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.MeetingRecordingCompleted;
import app.lightmove.api.notetaker.model.RecallBotNotice;
import app.lightmove.api.notetaker.model.TranscribedPiece;
import app.lightmove.api.notetaker.model.TranscriptLine;
import app.lightmove.api.notetaker.repository.MeetingRecordingRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * What the backup recorder's webhook says about its bot, applied to the run it was sent on. Its bot reports by
 * webhook rather than by run token, so this is its whole lifecycle: in the waiting room, recording, gone, and its
 * transcript — stored as one more piece of the run, after whatever our own bot captured, its speakers already named.
 * A run removed in the meantime keeps nothing, so what the backup kept is deleted there too.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class NotetakerBackups {

    /** After every piece our own bot could have sent, so the backup's transcript sorts as one more piece. */
    static final int BACKUP_SEQUENCE = 1_000_000;
    private static final Set<String> RECORDING = Set.of("in_call_recording", "in_call_not_recording");
    private static final Set<String> GONE = Set.of("done", "fatal");

    private final RecallBotApi recall;
    private final MeetingRecordingRepository recordings;
    private final NotetakerRunEndings endings;
    private final MeetingTranscriptPieces transcripts;
    private final MeetingNotesWorker notesWorker;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate transactions;
    private final Clock clock;

    void receive(byte[] body, String id, String timestamp, String signature) {
        for (RecallBotNotice notice : recall.notices(body, id, timestamp, signature)) {
            switch (notice) {
                case RecallBotNotice.StatusChanged changed -> statusChanged(changed);
                case RecallBotNotice.TranscriptReady ready -> transcriptReady(ready);
                case RecallBotNotice.TranscriptFailed failed -> transcriptFailed(failed);
            }
        }
    }

    private void statusChanged(RecallBotNotice.StatusChanged changed) {
        Instant now = clock.instant();
        Instant at = changed.at() != null && !changed.at().isAfter(now) ? changed.at() : now;
        boolean removed = Boolean.TRUE.equals(transactions.execute(status -> runOf(changed.botId())
                .map(recording -> applyStatus(recording, changed, at, now))
                .orElse(false)));
        if (removed) {
            deleteMediaQuietly(changed.botId());
        }
    }

    /** Answers whether the run ended removed, whose recording Recall must not keep either. */
    private boolean applyStatus(MeetingRecording recording, RecallBotNotice.StatusChanged changed, Instant at,
                                Instant now) {
        if (!recording.getStatus().isInCall()) {
            return false;
        }
        if (changed.code().equals("in_waiting_room")) {
            recording.reportWaiting(at);
        } else if (RECORDING.contains(changed.code())) {
            recording.backupJoined(at);
        } else if (GONE.contains(changed.code())) {
            left(recording, changed, now);
            return recording.getStatus() == NotetakerStatus.REMOVED;
        }
        return false;
    }

    /**
     * A bot that got in has a transcript coming, even one that died mid-call; one that never got in leaves the run as
     * our own bot left it — a partial note of what ours heard, or a failure.
     */
    private void left(MeetingRecording recording, RecallBotNotice.StatusChanged changed, Instant now) {
        if (recording.getStatus() == NotetakerStatus.REMOVE_REQUESTED) {
            recording.complete(now);
            return;
        }
        if (recording.getBackupJoinedAt() == null) {
            log.warn("Notetaker run {}: the backup's bot left without recording ({} {})", recording.getId(),
                    changed.code(), changed.subCode());
            recording.backupTranscribed(now);
            endings.failWithoutBackup(recording, NotetakerEndReason.BACKUP_FAILED, now);
            return;
        }
        recording.complete(now);
        events.publishEvent(new MeetingRecordingCompleted(recording.getWorkspaceId(), recording.getId()));
    }

    /** Read outside any transaction — two calls to Recall — then stored as the run's last piece, once. */
    private void transcriptReady(RecallBotNotice.TranscriptReady ready) {
        Optional<MeetingRecording> run = transactions.execute(status -> runOf(ready.botId()));
        if (run.isEmpty() || run.get().getBackupTranscriptAt() != null) {
            return;
        }
        if (run.get().getStatus() == NotetakerStatus.REMOVED) {
            deleteMediaQuietly(ready.botId());
            return;
        }
        List<TranscriptLine> lines = recall.transcript(ready.transcriptId());
        boolean stored = Boolean.TRUE.equals(transactions.execute(status -> runOf(ready.botId())
                .filter(recording -> recording.getBackupTranscriptAt() == null
                        && recording.getStatus() != NotetakerStatus.REMOVED)
                .map(recording -> {
                    if (!lines.isEmpty()) {
                        transcripts.store(recording.getId(), pieceOf(lines));
                    }
                    recording.backupTranscribed(clock.instant());
                    return true;
                })
                .orElse(false)));
        deleteMediaQuietly(ready.botId());
        if (stored) {
            run.ifPresent(recording -> notesWorker.workOn(recording.getId()));
        }
    }

    private void transcriptFailed(RecallBotNotice.TranscriptFailed failed) {
        Optional<MeetingRecording> run = transactions.execute(status -> runOf(failed.botId())
                .filter(recording -> recording.getBackupTranscriptAt() == null)
                .map(recording -> {
                    log.warn("Notetaker run {}: the backup's transcript failed", recording.getId());
                    recording.backupTranscribed(clock.instant());
                    return recording;
                }));
        deleteMediaQuietly(failed.botId());
        run.ifPresent(recording -> notesWorker.workOn(recording.getId()));
    }

    private Optional<MeetingRecording> runOf(String botId) {
        return recordings.findByRecorderAndExternalId(MeetingRecorderKind.RECALL, botId);
    }

    static TranscribedPiece pieceOf(List<TranscriptLine> lines) {
        Instant start = lines.stream().map(TranscriptLine::at).min(Instant::compareTo).orElseThrow();
        return new TranscribedPiece(BACKUP_SEQUENCE, start, lines.stream()
                .map(line -> new HeardLine(Duration.between(start, line.at()).toMillis(), line.speaker(), line.text(),
                        line.named()))
                .toList());
    }

    private void deleteMediaQuietly(String botId) {
        try {
            recall.deleteMedia(botId);
        } catch (RuntimeException failed) {
            log.warn("Recall bot {}: its recording could not be deleted: {}", botId, failed.toString());
        }
    }
}
