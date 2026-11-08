package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.model.PersonProcessingRestricted;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * A restricted person is recorded no further. Heard inside the restriction's transaction, so their bots are called off
 * and queued with it, never on a promise kept after the commit.
 */
@Component
@RequiredArgsConstructor
class ProcessingRestrictionNotetaker {

    private final NotetakerService notetaker;

    @EventListener
    void onRestricted(PersonProcessingRestricted restricted) {
        notetaker.callBackForRestriction(restricted.workspaceId(), restricted.personId(), restricted.restrictedBy());
    }
}
