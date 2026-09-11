package app.lightmove.api.position.repository;

import app.lightmove.api.position.model.HiddenLibraryTemplate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface HiddenLibraryTemplateRepository extends JpaRepository<HiddenLibraryTemplate, UUID> {

    List<HiddenLibraryTemplate> findByWorkspaceId(UUID workspaceId);

    Optional<HiddenLibraryTemplate> findByWorkspaceIdAndCode(UUID workspaceId, String code);
}
