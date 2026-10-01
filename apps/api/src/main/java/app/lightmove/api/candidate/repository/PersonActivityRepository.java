package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.constant.PersonActivityKind;
import app.lightmove.api.candidate.model.PersonActivity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/**
 * A person's history, written by {@code PersonActivityRecorder}. Reads page newest first on the id, the
 * cursor {@code ProjectActivityService} uses, and every finder carries the workspace id.
 */
public interface PersonActivityRepository extends JpaRepository<PersonActivity, Long> {

    @Query("""
            select a from PersonActivity a
            where a.workspaceId = :workspaceId and a.personId = :personId and a.id < :before and a.kind in :kinds
            order by a.id desc
            """)
    List<PersonActivity> findPersonTimeline(UUID workspaceId, UUID personId, long before,
                                            Collection<PersonActivityKind> kinds, Limit limit);

    /**
     * The workspace's feed. A filter left open is a flag rather than a null parameter, because Postgres
     * cannot type a bare null bind and an open filter would otherwise fail rather than match everything.
     */
    @Query("""
            select a from PersonActivity a
            where a.workspaceId = :workspaceId and a.id < :before and a.kind in :kinds
              and (:anyActor = true or a.actorUserId = :actor)
              and (:anyProject = true or a.projectId = :projectId)
              and a.occurredAt >= :from and a.occurredAt < :to
            order by a.id desc
            """)
    List<PersonActivity> findWorkspaceFeed(UUID workspaceId, long before, Collection<PersonActivityKind> kinds,
                                           boolean anyActor, UUID actor, boolean anyProject, UUID projectId,
                                           Instant from, Instant to, Limit limit);
}
