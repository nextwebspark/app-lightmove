package app.lightmove.api.outreach.model;

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
 * A workspace's default working week and daily email cap per mailbox (V155). A null week is the one its firm's
 * country keeps; a null cap is the deployment's.
 */
@Entity
@Table(name = "app_lm_workspace_availability")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceAvailability extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "weekly_hours")
    private WeeklyHours weeklyHours;

    @Column(name = "daily_email_cap")
    private Integer dailyEmailCap;

    public void change(WeeklyHours weeklyHours, Integer dailyEmailCap) {
        this.weeklyHours = weeklyHours;
        this.dailyEmailCap = dailyEmailCap;
    }
}
