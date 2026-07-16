package app.lightmove.api.workspace.repository;

import app.lightmove.api.workspace.model.PendingOnboarding;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PendingOnboardingRepository extends JpaRepository<PendingOnboarding, UUID> {

    Optional<PendingOnboarding> findByUserId(UUID userId);

    void deleteByUserId(UUID userId);
}
