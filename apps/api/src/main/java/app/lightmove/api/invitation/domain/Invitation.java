package app.lightmove.api.invitation.domain;

import app.lightmove.api.common.persistence.BaseEntity;
import app.lightmove.api.workspace.domain.WorkspaceRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * An offer to join a workspace, sent to an email address that may not have an account yet.
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

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private WorkspaceRole role;

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

    public static Invitation create(UUID workspaceId, String email, WorkspaceRole role,
                                    String tokenHash, UUID invitedBy, Instant expiresAt) {
        Invitation invitation = new Invitation();
        invitation.workspaceId = workspaceId;
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
