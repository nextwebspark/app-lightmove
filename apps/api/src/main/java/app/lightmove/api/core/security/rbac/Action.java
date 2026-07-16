package app.lightmove.api.core.security.rbac;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * One row of the action catalog — a single permission a role can grant.
 *
 * <p>Read-mostly reference data, seeded by migration. Code names actions through
 * {@link WorkspaceAction}/{@link ProjectAction}; authorisation asks whether the union of a
 * membership's roles reaches an action, never which role someone holds.
 */
@Entity
@Table(name = "app_lm_action")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Action extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private RoleScope scope;

    @Column(nullable = false, length = 64)
    private String name;

    @Column
    private String description;
}
