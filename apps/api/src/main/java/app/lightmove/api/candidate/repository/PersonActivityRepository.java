package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.model.PersonActivity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** A person's history. Every finder carries the workspace, the tenant a person belongs to. */
public interface PersonActivityRepository extends JpaRepository<PersonActivity, Long> {

    List<PersonActivity> findByWorkspaceIdAndPersonIdOrderByIdDesc(UUID workspaceId, UUID personId);
}
