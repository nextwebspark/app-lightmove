package app.lightmove.api.strategy.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * The search behind a project, 1:1. The filter (jsonb, V30) and the off-limits list are saved by two
 * PUTs, so a chip click never rewrites the exclusion list.
 */
@Entity
@Table(name = "app_lm_strategy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Strategy extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "filter", nullable = false)
    private StrategyFilter filter = StrategyFilter.empty();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_strategy_off_limits_company",
            joinColumns = @JoinColumn(name = "strategy_id"))
    @OrderColumn(name = "sort_order")
    private List<StrategyCompanyRef> offLimitsCompanies = new ArrayList<>();

    public static Strategy forProject(UUID projectId) {
        Strategy strategy = new Strategy();
        strategy.projectId = projectId;
        return strategy;
    }

    public void replaceFilter(StrategyFilter newFilter) {
        this.filter = newFilter;
    }

    public void replaceOffLimitsCompanies(List<StrategyCompanyRef> newOffLimitsCompanies) {
        this.offLimitsCompanies.clear();
        this.offLimitsCompanies.addAll(newOffLimitsCompanies);
    }

    public List<String> offLimitsAccountIds() {
        return offLimitsCompanies.stream().map(StrategyCompanyRef::getApolloAccountId).toList();
    }
}
