package app.lightmove.api.customcolumn.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.constant.CustomColumnType;
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
 * A column a mandate added to its grid — a <b>definition, not DDL</b>, which would need the
 * {@code CREATE} privilege {@code harden.sql} revokes. {@link #fieldKey} is slugged once and never
 * rewritten, since stored values point at it; {@link #label} is what renames.
 */
@Entity
@Table(name = "app_lm_project_custom_column")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProjectCustomColumn extends BaseEntity {

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Enumerated(EnumType.STRING)
    @Column(name = "target", nullable = false, length = 16, updatable = false)
    private CustomColumnTarget target;

    /** The jsonb key this column's values are written under. Immutable — see the class doc. */
    @Column(name = "field_key", nullable = false, updatable = false)
    private String fieldKey;

    @Column(name = "label", nullable = false)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 16)
    private CustomColumnType dataType;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "hidden", nullable = false)
    private boolean hidden;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    public static ProjectCustomColumn defined(UUID projectId, UUID createdBy, CustomColumnTarget target,
                                              String fieldKey, String label, CustomColumnType dataType,
                                              int displayOrder) {
        ProjectCustomColumn column = new ProjectCustomColumn();
        column.projectId = projectId;
        column.createdBy = createdBy;
        column.target = target;
        column.fieldKey = fieldKey;
        column.label = label;
        column.dataType = dataType;
        column.displayOrder = displayOrder;
        return column;
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }

    /**
     * Changing the type touches no stored value: they are kept as the strings they were entered as,
     * so a column corrected from TEXT to NUMBER simply starts refusing new non-numeric entries.
     */
    public void retype(CustomColumnType newType) {
        this.dataType = newType;
    }

    public void moveTo(int newDisplayOrder) {
        this.displayOrder = newDisplayOrder;
    }

    public void show() {
        this.hidden = false;
    }

    public void hide() {
        this.hidden = true;
    }
}
