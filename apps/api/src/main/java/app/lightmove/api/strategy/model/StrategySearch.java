package app.lightmove.api.strategy.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.strategy.constant.SearchVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A saved filter, held by value so a chip click never rewrites it. On a {@code PRIVATE} search
 * {@code createdBy} is a fence: to anyone but the author the row does not exist.
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

    @Enumerated(EnumType.STRING)
    @Column(name = "visibility", nullable = false, length = 16)
    private SearchVisibility visibility;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    public static StrategySearch of(UUID projectId, String name, StrategyFilter filter,
                                    SearchVisibility visibility, UUID createdBy) {
        StrategySearch search = new StrategySearch();
        search.projectId = projectId;
        search.name = name;
        search.filter = filter;
        search.visibility = visibility;
        search.createdBy = createdBy;
        return search;
    }

    public void rename(String newName) {
        this.name = newName;
    }

    public void replaceFilter(StrategyFilter newFilter) {
        this.filter = newFilter;
    }

    public void changeVisibility(SearchVisibility newVisibility) {
        this.visibility = newVisibility;
    }

    public boolean isHiddenFrom(UUID userId) {
        return visibility == SearchVisibility.PRIVATE && !createdBy.equals(userId);
    }
}
