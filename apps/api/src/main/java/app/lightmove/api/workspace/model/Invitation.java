package app.lightmove.api.workspace.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.workspace.constant.InvitationStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An offer to join a workspace, sent to an email address that may not have an account yet. With the
 * join-request path gone, this is the <b>only</b> way anyone becomes a member of an existing workspace.
 *
 * <p>Addressed to an email rather than a user id precisely because the invitee usually does not
 * exist as a user at the time of sending — that is the whole point of an invitation.
 *
 * <p>The token is stored as a SHA-256 hash, like every other token here: possession of the emailed
 * link is the credential, and the database holds only proof, not the credential itself.
 */
@Entity
@Table(name = "app_lm_invitation")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Invitation extends BaseEntity {

    @Column(name = "workspace_id", nullable = false)
    private UUID workspaceId;

    @Column(nullable = false)
    private String email;

    /** The workspace role the acceptor lands with. A catalog row, so the grant survives role edits. */
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    /**
     * The client whose portal this invitation admits a representative to. Null for a staff invitation.
     * The database CHECK ties this to the CLIENT role: a CLIENT invite names a client, a staff invite
     * names none.
     */
    @Column(name = "client_id")
    private UUID clientId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "invited_by", nullable = false)
    private UUID invitedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private InvitationStatus status = InvitationStatus.PENDING;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "accepted_by_user_id")
    private UUID acceptedByUserId;

    public static Invitation create(UUID workspaceId, String email, Role role,
                                    String tokenHash, UUID invitedBy, Instant expiresAt) {
        return build(workspaceId, null, email, role, tokenHash, invitedBy, expiresAt);
    }

    /**
     * A client-portal invitation: same token machinery as a staff invite, but it names the client its
     * acceptor represents. The CLIENT role and a non-null client id satisfy the {@code client_id} CHECK.
     */
    public static Invitation createForClient(UUID workspaceId, UUID clientId, String email, Role role,
                                             String tokenHash, UUID invitedBy, Instant expiresAt) {
        return build(workspaceId, clientId, email, role, tokenHash, invitedBy, expiresAt);
    }

    private static Invitation build(UUID workspaceId, UUID clientId, String email, Role role,
                                    String tokenHash, UUID invitedBy, Instant expiresAt) {
        Invitation invitation = new Invitation();
        invitation.workspaceId = workspaceId;
        invitation.clientId = clientId;
        invitation.email = email;
        invitation.role = role;
        invitation.tokenHash = tokenHash;
        invitation.invitedBy = invitedBy;
        invitation.expiresAt = expiresAt;
        invitation.status = InvitationStatus.PENDING;
        return invitation;
    }

    public boolean isRedeemable(Instant now) {
        return status == InvitationStatus.PENDING && expiresAt.isAfter(now);
    }

    public void accept(UUID userId, Instant now) {
        if (!isRedeemable(now)) {
            throw new IllegalStateException("Invitation is not redeemable");
        }
        this.status = InvitationStatus.ACCEPTED;
        this.acceptedAt = now;
        this.acceptedByUserId = userId;
    }

    public void revoke() {
        if (status != InvitationStatus.PENDING) {
            throw new IllegalStateException("Only a pending invitation can be revoked, was " + status);
        }
        this.status = InvitationStatus.REVOKED;
    }

    /**
     * Re-issues the token on a resend, so the previously emailed link stops working. Without this,
     * every resend would leave another live credential in another inbox.
     */
    public void refresh(String newTokenHash, Instant newExpiry) {
        this.tokenHash = newTokenHash;
        this.expiresAt = newExpiry;
        this.status = InvitationStatus.PENDING;
    }
}
