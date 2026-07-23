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
 * A client-side contact who represents a client record. Belongs to the client, not a mandate — a
 * representative exists before any project, and is later attached to specific mandates as a read-only
 * CLIENT project seat.
 *
 * <p>A lifecycle row. An external contact is born INVITED against an outstanding
 * {@link app.lightmove.api.workspace.model.Invitation}, then gains a {@code userId} and turns ACTIVE when
 * that invitation is accepted. A contact who is already a workspace member skips the invite entirely and
 * is born ACTIVE (see {@link #active}).
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
                                               String position, String email, UUID invitationId,
                                               UUID createdBy) {
        ClientRepresentative representative = new ClientRepresentative();
        representative.workspaceId = workspaceId;
        representative.clientId = clientId;
        representative.fullName = fullName.trim();
        representative.position = position;
        representative.email = email;
        representative.status = ClientRepStatus.INVITED;
        representative.invitationId = invitationId;
        representative.createdBy = createdBy;
        return representative;
    }

    /**
     * A representative who is already a workspace member: no invitation, ACTIVE from birth, bound to
     * their existing account. The CLIENT role is added to that membership separately.
     */
    public static ClientRepresentative active(UUID workspaceId, UUID clientId, String fullName,
                                              String position, String email, UUID userId, UUID createdBy) {
        ClientRepresentative representative = new ClientRepresentative();
        representative.workspaceId = workspaceId;
        representative.clientId = clientId;
        representative.fullName = fullName.trim();
        representative.position = position;
        representative.email = email;
        representative.status = ClientRepStatus.ACTIVE;
        representative.userId = userId;
        representative.createdBy = createdBy;
        return representative;
    }

    /** Re-issue an invite to an existing row, rather than leaving a second live representative. */
    public void reinvite(String fullName, String position, UUID invitationId) {
        this.fullName = fullName.trim();
        this.position = position;
        this.status = ClientRepStatus.INVITED;
        this.userId = null;
        this.invitationId = invitationId;
    }

    /** The registry details as last typed by staff — refreshed on a re-invite, whichever path it takes. */
    public void refreshDetails(String fullName, String position) {
        this.fullName = fullName.trim();
        this.position = position;
    }

    /** They accepted: an account now exists, so the representative is live and can be attached to mandates. */
    public void activate(UUID userId) {
        this.userId = userId;
        this.status = ClientRepStatus.ACTIVE;
    }
}
