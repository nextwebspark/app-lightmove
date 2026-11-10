package app.lightmove.api.report.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A position's one shared ranking scenario (V203): the technical share and competency weights the team is trying
 * out against the brief's, and why. It never writes the brief, and no row is the brief's own weighting.
 */
@Entity
@Table(name = "app_lm_ranking_scenario")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RankingScenario extends BaseEntity {

    public static final int MAX_RATIONALE = 1000;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "technical_share", nullable = false)
    private int technicalShare;

    /** Competency match key → weight, for every competency of the brief at the save. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weights", nullable = false)
    private Map<String, Integer> weights = new LinkedHashMap<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "saved_against", nullable = false)
    private BriefWeighting savedAgainst;

    @Column(name = "rationale", length = MAX_RATIONALE)
    private String rationale;

    @Column(name = "updated_by")
    private UUID updatedBy;

    public static RankingScenario startFor(UUID workspaceId, UUID projectId) {
        RankingScenario scenario = new RankingScenario();
        scenario.workspaceId = workspaceId;
        scenario.projectId = projectId;
        return scenario;
    }

    public void record(int newTechnicalShare, Map<String, Integer> newWeights, String newRationale,
                       BriefWeighting against, UUID by) {
        this.technicalShare = newTechnicalShare;
        this.weights = new LinkedHashMap<>(newWeights);
        this.rationale = newRationale;
        this.savedAgainst = against;
        this.updatedBy = by;
    }
}
