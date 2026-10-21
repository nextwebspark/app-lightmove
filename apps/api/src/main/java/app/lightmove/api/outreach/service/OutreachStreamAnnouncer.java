package app.lightmove.api.outreach.service;

import app.lightmove.api.core.stream.ProjectStreamKind;
import app.lightmove.api.core.stream.ProjectStreamPublisher;
import app.lightmove.api.outreach.model.OutreachEnrollment;
import app.lightmove.api.outreach.model.OutreachMessage;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostRemove;
import jakarta.persistence.PostUpdate;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Tells the position's open Outreach screens that an email, a reply or a run moved — on every write of either row,
 * whichever service made it, so no path that sends, records, stops or cancels can forget to. It fires during the
 * flush, inside the writing transaction, so Postgres delivers it at that commit (and a rollback drops it); one
 * transaction's repeats of the same payload are one notification.
 */
@Component
@RequiredArgsConstructor
public class OutreachStreamAnnouncer {

    // A provider, not the bean: Hibernate asks for this listener while the entity manager is still being built.
    private final ObjectProvider<ProjectStreamPublisher> stream;

    @PostPersist
    @PostUpdate
    @PostRemove
    void announce(Object changed) {
        UUID projectId = switch (changed) {
            case OutreachMessage message -> message.getProjectId();
            case OutreachEnrollment enrollment -> enrollment.getProjectId();
            default -> null;
        };
        if (projectId != null) {
            stream.getObject().publish(projectId, ProjectStreamKind.OUTREACH);
        }
    }
}
