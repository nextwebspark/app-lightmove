package app.lightmove.api.positiontemplate.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** A library template one workspace has taken out of its picker and its title matching (V52). */
@Entity
@Table(name = "app_lm_position_template_hidden")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class HiddenLibraryTemplate extends BaseEntity {

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "code", nullable = false, updatable = false, length = 64)
    private String code;

    @Column(name = "hidden_by", updatable = false)
    private UUID hiddenBy;

    public static HiddenLibraryTemplate of(UUID workspaceId, String code, UUID hiddenBy) {
        HiddenLibraryTemplate hidden = new HiddenLibraryTemplate();
        hidden.workspaceId = workspaceId;
        hidden.code = code;
        hidden.hiddenBy = hiddenBy;
        return hidden;
    }
}
