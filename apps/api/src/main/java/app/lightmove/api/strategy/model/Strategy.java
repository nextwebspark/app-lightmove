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
 * The search behind a project, 1:1 with it. Seeded empty on first read and edited by the Strategy
 * screen's autosave.
 *
 * <p>Two pieces, saved by two different PUTs, because they change for different reasons. The
 * {@link StrategyFilter} is the sidebar — industries, countries, size bands, the off-limits toggle —
 * and it moves constantly while a consultant is exploring. The off-limits list is a standing decision
 * about particular companies, edited rarely and from a different panel. One shared write would make
 * every chip click rewrite the exclusion list.
 *
 * <p>The filter is a jsonb document rather than four child tables. It is read whole, written whole,
 * and never queried by axis — see V30's header for the full argument.
 *
 * <p>The off-limits list stays an owned ordered collection (replace-list writes) rather than a
 * document, because it is the one part of the strategy that holds <i>references</i>: each entry
 * carries an {@code apollo_account_id} plus a write-time snapshot, and a query that asks "which
 * mandates bar this company" is one worth being able to write.
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

    /** The barred companies' ids, which is all the query side ever needs of the list. */
    public List<String> offLimitsAccountIds() {
        return offLimitsCompanies.stream().map(StrategyCompanyRef::getApolloAccountId).toList();
    }
}
