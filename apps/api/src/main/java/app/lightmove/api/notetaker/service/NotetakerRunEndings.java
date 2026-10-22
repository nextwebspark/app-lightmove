package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.model.BackupBotNeeded;
import app.lightmove.api.notetaker.model.MeetingRecording;
import app.lightmove.api.notetaker.model.MeetingRecordingCompleted;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * How a run ends when its bot fails. Where the backup recorder covers the failure (#955, {@link FallbackPolicy}) the
 * same run is handed to it while the call goes, and its bot sent once that commits. Otherwise a bot that dies mid-call
 * has still recorded what it heard until then, so it goes to be noted as a partial call rather than failing; one that
 * never got in, or captured nothing, fails.
 */
@Component
@RequiredArgsConstructor
class NotetakerRunEndings {

    private static final Set<NotetakerEndReason> STOPPED_MID_CALL = EnumSet.of(NotetakerEndReason.CRASHED,
            NotetakerEndReason.HEARTBEAT_LOST, NotetakerEndReason.BACKUP_FAILED,
            NotetakerEndReason.RECORDING_NOT_ALLOWED);

    private final MeetingMediaPieces pieces;
    private final ChainedMeetingRecorder recorders;
    private final ApplicationEventPublisher events;

    @Transactional(propagation = Propagation.MANDATORY)
    void fail(MeetingRecording recording, NotetakerEndReason reason, Instant now) {
        boolean coverable = recording.getRecorder() == MeetingRecorderKind.OWN && recording.getStatus().isInCall()
                && !recording.isToLeave() && now.isBefore(recording.getEndsAt())
                && recorders.fallsBack(reason, recording.getWorkspaceId());
        if (!coverable) {
            failWithoutBackup(recording, reason, now);
            return;
        }
        Instant ownStoppedAt = recording.getJoinedAt() != null && pieces.holdsSpeech(recording.getId())
                ? pieces.lastReceivedOf(recording.getId()).orElse(now) : null;
        recording.handToBackup(reason, ownStoppedAt, now);
        events.publishEvent(new BackupBotNeeded(recording.getId()));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void failWithoutBackup(MeetingRecording recording, NotetakerEndReason reason, Instant now) {
        if (STOPPED_MID_CALL.contains(reason) && recording.getJoinedAt() != null && !recording.isToLeave()
                && pieces.holdsSpeech(recording.getId())) {
            recording.stopEarly(recording.getRecordedUntil() != null ? recording.getRecordedUntil()
                    : pieces.lastReceivedOf(recording.getId()).orElse(now), now);
            events.publishEvent(new MeetingRecordingCompleted(recording.getWorkspaceId(), recording.getId()));
            return;
        }
        recording.fail(reason, now);
    }
}
