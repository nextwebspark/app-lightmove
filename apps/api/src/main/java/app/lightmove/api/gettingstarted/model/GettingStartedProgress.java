package app.lightmove.api.gettingstarted.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.gettingstarted.constant.GettingStartedStep;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** One person's checklist in one workspace: what they dismissed or skipped, and when each step was first done. */
@Entity
@Table(name = "app_lm_getting_started")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GettingStartedProgress extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "dismissed_at")
    private Instant dismissedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skipped_steps", nullable = false)
    private List<GettingStartedStep> skippedSteps = new ArrayList<>();

    // Written only by GettingStartedProgressRepository.stampCompleted, which keeps a first stamp against a racing
    // read; mapped read-only so a dismiss or skip saving this row can never write an older copy over it.
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "completed_steps", nullable = false, insertable = false, updatable = false)
    private Map<GettingStartedStep, Instant> completedSteps = Map.of();

    public boolean isDismissed() {
        return dismissedAt != null;
    }

    public void dismiss(Instant at) {
        if (dismissedAt == null) {
            dismissedAt = at;
        }
    }

    public void restore() {
        dismissedAt = null;
    }

    public boolean hasSkipped(GettingStartedStep step) {
        return skippedSteps.contains(step);
    }

    public void skip(GettingStartedStep step) {
        if (!skippedSteps.contains(step)) {
            skippedSteps = new ArrayList<>(skippedSteps);
            skippedSteps.add(step);
        }
    }

    public void unskip(GettingStartedStep step) {
        if (skippedSteps.contains(step)) {
            skippedSteps = new ArrayList<>(skippedSteps);
            skippedSteps.remove(step);
        }
    }
}
