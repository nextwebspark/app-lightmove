package app.lightmove.api.strategy.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A named filter a mandate saved so it could come back to it. The toolbar's "Save Search".
 *
 * <p>Holds the same {@link StrategyFilter} document the live strategy does, deliberately by value:
 * loading a saved search copies its filter onto the strategy, and the two then move independently.
 * A reference would make every subsequent chip click silently rewrite the saved search — which is
 * exactly the thing it exists not to do.
 *
 * <p>Searches belong to the mandate, not to the person who saved one. {@code createdBy} is provenance
 * for the list rather than a fence: a LEAD reworking a RESEARCHER's search is ordinary collaboration,
 * and who may see the mandate at all is already settled by {@code WORK_VIEW}.
 */
@Entity
@Table(name = "app_lm_strategy_search")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StrategySearch extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter", nullable = false)
    private StrategyFilter filter;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    public static StrategySearch of(UUID projectId, String name, StrategyFilter filter, UUID createdBy) {
        StrategySearch search = new StrategySearch();
        search.projectId = projectId;
        search.name = name;
        search.filter = filter;
        search.createdBy = createdBy;
        return search;
    }

    public void rename(String newName) {
        this.name = newName;
    }
}
