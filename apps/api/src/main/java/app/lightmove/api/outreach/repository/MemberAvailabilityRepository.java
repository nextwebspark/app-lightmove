package app.lightmove.api.outreach.repository;

import app.lightmove.api.outreach.model.MemberAvailability;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Consultants' own working weeks and email caps. Every read carries the workspace and the consultant. */
public interface MemberAvailabilityRepository extends JpaRepository<MemberAvailability, UUID> {

    Optional<MemberAvailability> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
