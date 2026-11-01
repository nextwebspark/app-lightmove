package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.SuppressionKind;
import app.lightmove.api.candidate.constant.SuppressionReason;
import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One key no door may file again in this workspace (V173). It holds a hash, never the slug or address itself:
 * the list exists to recognise someone, not to remember them.
 */
@Entity
@Table(name = "app_lm_workspace_suppression")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkspaceSuppression extends BaseEntity {

    public static final int MAX_NOTE = 500;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false, updatable = false, length = 16)
    private SuppressionKind kind;

    @Column(name = "value_hash", nullable = false, updatable = false, length = 64)
    private String valueHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, length = 16)
    private SuppressionReason reason;

    @Column(name = "note", length = MAX_NOTE)
    private String note;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    public static WorkspaceSuppression of(UUID workspaceId, SuppressionKey key, SuppressionReason reason,
                                          String note, UUID createdBy) {
        WorkspaceSuppression suppression = new WorkspaceSuppression();
        suppression.workspaceId = workspaceId;
        suppression.kind = key.kind();
        suppression.valueHash = key.hash();
        suppression.reason = reason;
        suppression.note = note;
        suppression.createdBy = createdBy;
        return suppression;
    }
}
