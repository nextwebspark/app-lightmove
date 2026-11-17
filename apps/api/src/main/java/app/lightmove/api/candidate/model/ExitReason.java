package app.lightmove.api.candidate.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Why someone left a position's process, as the workspace words it (V227), and the out-of-process statuses that
 * offer it. Retired, never deleted: whoever was given it keeps it.
 */
@Entity
@Table(name = "app_lm_workspace_exit_reason")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ExitReason extends BaseEntity {

    public static final int MAX_LABEL = 60;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "key", nullable = false, updatable = false, length = 40)
    private String reasonKey;

    @Column(name = "label", nullable = false, length = MAX_LABEL)
    private String label;

    @Column(name = "requires_note", nullable = false)
    private boolean requiresNote;

    @Column(name = "position", nullable = false)
    private int position;

    @Column(name = "retired_at")
    private Instant retiredAt;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    @Getter(AccessLevel.NONE)
    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_workspace_status_exit_reason", joinColumns = @JoinColumn(name = "reason_id"))
    @Column(name = "status_id", nullable = false)
    private Set<UUID> statusIds = new HashSet<>();

    public static ExitReason added(UUID workspaceId, String reasonKey, String label, boolean requiresNote,
                                   int position, UUID createdBy) {
        ExitReason reason = new ExitReason();
        reason.workspaceId = workspaceId;
        reason.reasonKey = reasonKey;
        reason.label = label;
        reason.requiresNote = requiresNote;
        reason.position = position;
        reason.createdBy = createdBy;
        return reason;
    }

    /** Read-only: {@link #offerAt} is the one way to change which statuses offer it. */
    public Set<UUID> getStatusIds() {
        return Collections.unmodifiableSet(statusIds);
    }

    public void rename(String newLabel) {
        this.label = newLabel;
    }

    public void askForNote(boolean required) {
        this.requiresNote = required;
    }

    public void placeAt(int newPosition) {
        this.position = newPosition;
    }

    public void offerAt(Collection<UUID> statuses) {
        statusIds.clear();
        statusIds.addAll(statuses);
    }

    public boolean isOfferedAt(UUID statusId) {
        return statusIds.contains(statusId);
    }

    public void retire() {
        if (retiredAt == null) {
            this.retiredAt = Instant.now();
        }
    }

    public void restore() {
        this.retiredAt = null;
    }

    public boolean isRetired() {
        return retiredAt != null;
    }
}
