package app.lightmove.api.position.service;

import app.lightmove.api.project.model.ProjectCreated;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** A new position gets its brief drafted in the transaction that creates it, so a failed draft refuses the position. */
@Component
@RequiredArgsConstructor
class PositionSeeding {

    private final PositionService positions;

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void onCreated(ProjectCreated created) {
        positions.seedFor(created.workspaceId(), created.projectId(), created.positionTitle(), created.hqCountry());
    }
}
