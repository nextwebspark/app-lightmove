package app.lightmove.api.project.model;

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

/**
 * The search strategy behind a project, 1:1 with it. Seeded empty on first read and edited by the
 * Strategy screen's autosave. Owns the sector, company-size, geography and ownership scopes, plus
 * the two company lists: the target seeds and the off-limits set.
 *
 * <p>The sectors are an owned ordered list (replace-list writes), not entities — the whole scope is
 * one snapshot the screen holds and PUTs back. All three kinds share the list; the service splits
 * them by {@link StrategySector#getKind()}. The company-size bands are a second such list, split by
 * {@link StrategySizeBand#getAxis()}; each section saves its own snapshot independently. Geography
 * and ownership are single-valued fixed catalogs, so their lists hold bare enum names (see
 * {@code GeographyMarket} / {@code OwnershipStructure}) with presence meaning selection. The company
 * lists hold {@link StrategyCompanyRef} snapshots keyed to the universe by {@code (source, sourceId)}
 * — two separate collections, because each saves through its own PUT and a shared one would make
 * every save rewrite the other list's rows.
 */
@Entity
@Table(name = "app_lm_strategy")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Strategy extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_strategy_sector",
            joinColumns = @JoinColumn(name = "strategy_id"))
    @OrderColumn(name = "sort_order")
    private List<StrategySector> sectors = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_strategy_company_size",
            joinColumns = @JoinColumn(name = "strategy_id"))
    @OrderColumn(name = "sort_order")
    private List<StrategySizeBand> sizeBands = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_strategy_geography",
            joinColumns = @JoinColumn(name = "strategy_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "market", nullable = false, length = 32)
    private List<String> marketNames = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_strategy_ownership",
            joinColumns = @JoinColumn(name = "strategy_id"))
    @OrderColumn(name = "sort_order")
    @Column(name = "structure", nullable = false, length = 48)
    private List<String> structureNames = new ArrayList<>();

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_strategy_target_company",
            joinColumns = @JoinColumn(name = "strategy_id"))
    @OrderColumn(name = "sort_order")
    private List<StrategyCompanyRef> targetCompanies = new ArrayList<>();

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

    public void replaceSectors(List<StrategySector> newSectors) {
        this.sectors.clear();
        this.sectors.addAll(newSectors);
    }

    public void replaceSizeBands(List<StrategySizeBand> newSizeBands) {
        this.sizeBands.clear();
        this.sizeBands.addAll(newSizeBands);
    }

    public void replaceMarkets(List<String> newMarketNames) {
        this.marketNames.clear();
        this.marketNames.addAll(newMarketNames);
    }

    public void replaceStructures(List<String> newStructureNames) {
        this.structureNames.clear();
        this.structureNames.addAll(newStructureNames);
    }

    public void replaceTargetCompanies(List<StrategyCompanyRef> newTargetCompanies) {
        this.targetCompanies.clear();
        this.targetCompanies.addAll(newTargetCompanies);
    }

    public void replaceOffLimitsCompanies(List<StrategyCompanyRef> newOffLimitsCompanies) {
        this.offLimitsCompanies.clear();
        this.offLimitsCompanies.addAll(newOffLimitsCompanies);
    }
}
