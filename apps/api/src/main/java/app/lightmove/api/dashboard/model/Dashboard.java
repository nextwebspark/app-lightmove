package app.lightmove.api.dashboard.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** A member's custom dashboard (V231), on one position or, with no project, across the workspace. */
@Entity
@Table(name = "app_lm_dashboard")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Dashboard extends BaseEntity {

    public static final int MAX_NAME = 80;
    public static final int MAX_WIDGETS = 30;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", updatable = false)
    private UUID projectId;

    @Column(name = "owner_user_id", nullable = false, updatable = false)
    private UUID ownerUserId;

    @Column(name = "name", nullable = false, length = MAX_NAME)
    private String name;

    @Column(name = "shared", nullable = false)
    private boolean shared;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "widgets", nullable = false)
    private List<DashboardWidget> widgets = new ArrayList<>();

    public static Dashboard startFor(UUID workspaceId, UUID projectId, UUID ownerUserId) {
        Dashboard dashboard = new Dashboard();
        dashboard.workspaceId = workspaceId;
        dashboard.projectId = projectId;
        dashboard.ownerUserId = ownerUserId;
        return dashboard;
    }

    public void record(String newName, boolean newShared, List<DashboardWidget> newWidgets) {
        this.name = newName;
        this.shared = newShared;
        this.widgets = new ArrayList<>(newWidgets);
    }

    public boolean isOwnedBy(UUID userId) {
        return ownerUserId.equals(userId);
    }
}
