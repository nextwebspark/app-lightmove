package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.service.CandidateMeetingNotes;
import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.notetaker.model.MeetingRecording;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Tells every live position mapping the call's executive that its bot moved, so an open drawer's chip follows it. Only
 * a change of status is announced: a heartbeat writes the row every few seconds and moves nothing anyone sees. A
 * claim inserts its row by plain SQL, where no entity callback runs, so it announces itself through {@link #announce}.
 */
@Component
@RequiredArgsConstructor
public class NotetakerStreamAnnouncer {

    // Providers, not beans: Hibernate asks for this listener while the entity manager is still being built.
    private final ObjectProvider<ProjectStreamPublisher> stream;
    private final ObjectProvider<CandidateMeetingNotes> people;

    @PostPersist
    @PostUpdate
    void announceMove(MeetingRecording recording) {
        if (recording.takeStatusMove()) {
            announce(recording.getWorkspaceId(), recording.getPersonId());
        }
    }

    /** Inside the writing transaction, which a claim's plain insert opens for itself. */
    void announce(UUID workspaceId, UUID personId) {
        if (personId == null) {
            return;
        }
        people.getObject().livePositionsMapping(workspaceId, personId)
                .forEach(projectId -> stream.getObject().publish(projectId, ProjectStreamKind.NOTETAKER));
    }
}
