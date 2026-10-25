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

/** A consultant's own working week and daily email cap in one workspace (V155); a null field is the workspace's. */
@Entity
@Table(name = "app_lm_member_availability")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberAvailability extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

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
