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
 * One extra column a mandate has added to its Companies grid — what it is called, what it holds, and
 * where it sits.
 *
 * <p>This is a <b>definition, not a schema change</b>. The values live in a {@code custom_fields}
 * jsonb bag on the triage-company and candidate rows (V46), keyed by {@link #fieldKey}. A column per
 * tenant in real DDL would be unmigratable, unindexable, and would need the runtime role to hold the
 * {@code CREATE} privilege {@code harden.sql} exists to take away.
 *
 * <p>{@link #fieldKey} and {@link #label} are two fields on purpose and only one of them moves. The
 * key is slugged once from the label the column was created with and is never rewritten, because every
 * value already stored points at it; renaming is a change to the header a user reads and to nothing
 * else. A key that could be renamed would orphan a mandate's data the first time somebody fixed a typo.
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
     * Changing the type is allowed and does not touch a single stored value. Values are kept as the
     * strings they were entered as, so a column corrected from TEXT to NUMBER simply starts refusing
     * new non-numeric entries — the alternative, discarding what no longer parses, would lose data to
     * fix a label.
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
