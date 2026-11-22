package app.lightmove.api.startpage.repository;

import app.lightmove.api.startpage.model.StartPage;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StartPageRepository extends JpaRepository<StartPage, UUID> {

    Optional<StartPage> findByWorkspaceIdAndUserId(UUID workspaceId, UUID userId);
}
