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
 * A named filter a mandate saved so it could come back to it.
 *
 * <p>Holds the same {@link StrategyFilter} document the live strategy does, deliberately by value: a
 * reference would make every subsequent chip click silently rewrite the saved search. Re-capturing
 * the current filter is therefore an explicit act ({@link #replaceFilter}).
 *
 * <p>What {@code createdBy} means depends on {@link #visibility}. On a {@code SHARED} search it is
 * provenance; on a {@code PRIVATE} one it is a fence — the author is the only person who may read,
 * rename or delete it, and to everyone else the row does not exist.
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

    /** A private search does not exist as far as anyone but its author is concerned. */
    public boolean isHiddenFrom(UUID userId) {
        return visibility == SearchVisibility.PRIVATE && !createdBy.equals(userId);
    }
}
