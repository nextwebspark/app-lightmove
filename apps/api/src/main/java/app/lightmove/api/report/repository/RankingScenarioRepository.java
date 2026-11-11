package app.lightmove.api.report.repository;

import app.lightmove.api.report.model.RankingScenario;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RankingScenarioRepository extends JpaRepository<RankingScenario, UUID> {

    Optional<RankingScenario> findByProjectIdAndWorkspaceId(UUID projectId, UUID workspaceId);
}
