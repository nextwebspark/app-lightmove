package app.lightmove.api.dashboard.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A member's own Home in one workspace (V233): its tiles and what Needs you shows. */
@Entity
@Table(name = "app_lm_home_layout")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HomeLayout extends BaseEntity {

    public static final int MAX_CHARTS = 12;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tiles", nullable = false)
    private List<HomeTile> tiles = new ArrayList<>();

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "needs", nullable = false)
    private Map<String, Boolean> needs = new LinkedHashMap<>();

    public static HomeLayout startFor(UUID workspaceId, UUID userId) {
        HomeLayout layout = new HomeLayout();
        layout.workspaceId = workspaceId;
        layout.userId = userId;
        return layout;
    }

    public void record(List<HomeTile> newTiles, Map<String, Boolean> newNeeds) {
        this.tiles = new ArrayList<>(newTiles);
        this.needs = new LinkedHashMap<>(newNeeds);
    }

    public List<DashboardWidget> charts() {
        return tiles.stream().filter(tile -> tile.kind().isChart()).map(HomeTile::asWidget).toList();
    }
}
