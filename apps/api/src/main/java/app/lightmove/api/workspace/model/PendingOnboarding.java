package app.lightmove.api.workspace.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A signup wizard that has been filled in but not yet earned.
 *
 * <p>The user completed step 2 (and maybe step 3) without having verified their email. Nothing has been
 * created: no workspace, no membership, no invitation sent. This is the record of what they asked for,
 * and it becomes real the moment they click the link in their inbox.
 *
 * <p>That split is the whole point. The email domain is LightMove's only evidence that someone works at
 * a firm, so a workspace bound to {@code goldmansachs.com} must not exist because somebody typed that
 * address into a form. Holding the intent here means there is nothing on the domain until the mailbox
 * is proved.
 *
 * <p>Since membership is invitation-only, the only intent a wizard can hold is <i>create</i> — there is
 * no join request to hold anymore.
 */
@Entity
@Table(name = "app_lm_pending_onboarding")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PendingOnboarding extends BaseEntity {

    @Column(name = "user_id", nullable = false, updatable = false, unique = true)
    private UUID userId;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "company_size", length = 32)
    private String companySize;

    @Column(name = "primary_region", length = 32)
    private String primaryRegion;

    @Column(name = "team_focus", length = 32)
    private String teamFocus;

    /** Step 3, held rather than sent. An unverified user must not make us email strangers. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private List<PendingInvite> invitations = List.of();

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public static PendingOnboarding toCreate(UUID userId, String name, String companySize,
                                             String primaryRegion, String teamFocus,
                                             Instant expiresAt) {
        PendingOnboarding pending = new PendingOnboarding();
        pending.userId = userId;
        pending.describe(name, companySize, primaryRegion, teamFocus);
        pending.expiresAt = expiresAt;
        return pending;
    }

    /** Re-submitting step 2 — the wizard's Back button — edits the draft rather than adding another. */
    public void describe(String name, String companySize, String primaryRegion, String teamFocus) {
        this.name = name;
        this.companySize = companySize;
        this.primaryRegion = primaryRegion;
        this.teamFocus = teamFocus;
    }

    public void holdInvitations(List<PendingInvite> invitations) {
        this.invitations = invitations == null ? List.of() : List.copyOf(invitations);
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** One row per invitee from step 3. A record, so the jsonb has a shape rather than being a bag. */
    public record PendingInvite(String email, WorkspaceRole role) {
    }
}
