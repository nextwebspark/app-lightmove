package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.NotetakerEndReason;
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
 * How a run ends when its bot fails. A bot that dies mid-call has still recorded what it heard until then, so it
 * goes to be noted as a partial call rather than failing; one that never got in, or captured nothing, fails.
 */
@Component
@RequiredArgsConstructor
class NotetakerRunEndings {

    private static final Set<NotetakerEndReason> STOPPED_MID_CALL = EnumSet.of(NotetakerEndReason.CRASHED,
            NotetakerEndReason.HEARTBEAT_LOST);

    private final MeetingMediaPieces pieces;
    private final ApplicationEventPublisher events;

    @Transactional(propagation = Propagation.MANDATORY)
    void fail(MeetingRecording recording, NotetakerEndReason reason, Instant now) {
        if (STOPPED_MID_CALL.contains(reason) && recording.getJoinedAt() != null && !recording.isToLeave()
                && pieces.holdsSpeech(recording.getId())) {
            recording.stopEarly(pieces.lastReceivedOf(recording.getId()).orElse(now), now);
            events.publishEvent(new MeetingRecordingCompleted(recording.getWorkspaceId(), recording.getId()));
            return;
        }
        recording.fail(reason, now);
    }
}
