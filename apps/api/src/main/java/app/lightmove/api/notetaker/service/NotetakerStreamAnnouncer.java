package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.service.CandidateMeetingNotes;
import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.notetaker.model.MeetingRecording;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Tells every live position mapping the call's executive that its bot moved, so an open drawer's chip follows it. Only
 * a change of status is announced: a heartbeat writes the row every few seconds and moves nothing anyone sees.
 */
@Component
@RequiredArgsConstructor
public class NotetakerStreamAnnouncer {

    // Providers, not beans: Hibernate asks for this listener while the entity manager is still being built.
    private final ObjectProvider<ProjectStreamPublisher> stream;
    private final ObjectProvider<CandidateMeetingNotes> people;

    @PostPersist
    @PostUpdate
    void announce(MeetingRecording recording) {
        if (!recording.takeStatusMove() || recording.getPersonId() == null) {
            return;
        }
        people.getObject().livePositionsMapping(recording.getWorkspaceId(), recording.getPersonId())
                .forEach(projectId -> stream.getObject().publish(projectId, ProjectStreamKind.NOTETAKER));
    }
}
