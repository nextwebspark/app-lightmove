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
 * Strategy screen's autosave. Today it owns only the sector scope; company-size, ownership, location
 * and the seed/off-limits lists join this aggregate in later sessions.
 *
 * <p>The sectors are an owned ordered list (replace-list writes), not entities — the whole scope is
 * one snapshot the screen holds and PUTs back. All three kinds share the list; the service splits
 * them by {@link StrategySector#getKind()}. The company-size bands are a second such list, split by
 * {@link StrategySizeBand#getAxis()}; each section saves its own snapshot independently.
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
}
