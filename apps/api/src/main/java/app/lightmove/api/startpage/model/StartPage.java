package app.lightmove.api.startpage.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.startpage.constant.PositionSection;
import app.lightmove.api.startpage.constant.StartPageKind;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.jspecify.annotations.Nullable;

/** A member's start page in one workspace (V237) when it is not Home. */
@Entity
@Table(name = "app_lm_start_page")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class StartPage extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, length = 16)
    private StartPageKind kind;

    @Column(name = "dashboard_id")
    private @Nullable UUID dashboardId;

    @Column(name = "project_id")
    private @Nullable UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "section", length = 16)
    private @Nullable PositionSection section;

    public static StartPage startFor(UUID workspaceId, UUID userId) {
        StartPage page = new StartPage();
        page.workspaceId = workspaceId;
        page.userId = userId;
        return page;
    }

    public void pointAt(StartPageTarget target) {
        this.kind = target.kind();
        this.dashboardId = target.dashboardId();
        this.projectId = target.projectId();
        this.section = target.section();
    }

    public StartPageTarget target() {
        return new StartPageTarget(kind, dashboardId, projectId, section);
    }
}
