package app.lightmove.api.project.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.project.constant.ClientRepStatus;
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
 * A client-side contact invited to the portal. Belongs to the client record, not a mandate — the
 * Clients drawer invites representatives before any project exists.
 *
 * <p>A lifecycle row: it is born INVITED against an outstanding {@link app.lightmove.api.workspace.model.Invitation},
 * gains a {@code userId} and turns ACTIVE when that invitation is accepted, and carries a denormalised
 * {@code workspaceId} so the portal can resolve "which client does this signed-in user represent?"
 * from {@code (workspaceId, userId)} alone.
 */
@Entity
@Table(name = "app_lm_client_representative")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientRepresentative extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(name = "client_id", nullable = false)
    private UUID clientId;

    @Column(name = "full_name", nullable = false, length = 160)
    private String fullName;

    @Column(length = 160)
    private String position;

    @Column(nullable = false, length = 320)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ClientRepStatus status = ClientRepStatus.INVITED;

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "invitation_id")
    private UUID invitationId;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    public static ClientRepresentative invited(UUID workspaceId, UUID clientId, String fullName,
                                               String position, String email, UUID createdBy) {
        ClientRepresentative representative = new ClientRepresentative();
        representative.workspaceId = workspaceId;
        representative.clientId = clientId;
        representative.fullName = fullName.trim();
        representative.position = position;
        representative.email = email;
        representative.status = ClientRepStatus.INVITED;
        representative.createdBy = createdBy;
        return representative;
    }

    /** Re-issue an invite to a previously revoked row, rather than leaving a second live representative. */
    public void reinvite(String fullName, String position, UUID invitationId) {
        this.fullName = fullName.trim();
        this.position = position;
        this.status = ClientRepStatus.INVITED;
        this.userId = null;
        this.invitationId = invitationId;
    }

    public void linkInvitation(UUID invitationId) {
        this.invitationId = invitationId;
    }

    /** They accepted: an account now exists, and the portal opens to them. */
    public void activate(UUID userId) {
        this.userId = userId;
        this.status = ClientRepStatus.ACTIVE;
    }

    public void revoke() {
        this.status = ClientRepStatus.REVOKED;
    }
}
