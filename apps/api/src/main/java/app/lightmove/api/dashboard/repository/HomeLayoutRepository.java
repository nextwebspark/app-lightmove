package app.lightmove.api.dashboard.repository;

import app.lightmove.api.dashboard.model.HomeLayout;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HomeLayoutRepository extends JpaRepository<HomeLayout, UUID> {

    Optional<HomeLayout> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
