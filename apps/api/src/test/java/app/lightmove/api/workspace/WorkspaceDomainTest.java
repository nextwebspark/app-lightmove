package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.constant.WorkspaceRole;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The state machines governing membership, deletion and invitations — the invariants every service
 * above them assumes.
 */
class WorkspaceDomainTest {

    private final UUID someone = UUID.randomUUID();

    @Test
    @DisplayName("a pending membership cannot change role or be removed — it was never in")
    void pendingMembershipRejectsRosterMutations() {
        WorkspaceMember pending = WorkspaceMember.requestToJoin(
                UUID.randomUUID(), someone, WorkspaceRole.CONSULTANT);

        assertThatIllegalStateException()
                .isThrownBy(() -> pending.changeRole(WorkspaceRole.ADMIN));
        assertThatIllegalStateException().isThrownBy(pending::remove);
    }

    @Test
    @DisplayName("a removed membership stays removed — no role change resurrects it")
    void removedMembershipIsFinal() {
        WorkspaceMember member = WorkspaceMember.invite(
                UUID.randomUUID(), someone, WorkspaceRole.CONSULTANT, someone);
        member.remove();

        assertThat(member.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThatIllegalStateException()
                .isThrownBy(() -> member.changeRole(WorkspaceRole.ADMIN));
        assertThatIllegalStateException().isThrownBy(member::remove);
    }

    @Test
    @DisplayName("deleting a workspace is final")
    void workspaceDeleteIsFinal() {
        Workspace workspace = Workspace.create(
                "Acme Search", "acme-search", "acme.example", someone, null, null, null);

        workspace.delete();

        assertThatIllegalStateException().isThrownBy(workspace::delete);
    }

    @Test
    @DisplayName("only a pending invitation can be revoked, and a revoked one is not redeemable")
    void invitationRevocation() {
        Instant now = Instant.now();
        Invitation invitation = Invitation.create(UUID.randomUUID(), "sara@acme.example",
                WorkspaceRole.CONSULTANT, "hash", someone, now.plusSeconds(3600));

        invitation.revoke();

        assertThat(invitation.getStatus()).isEqualTo(InvitationStatus.REVOKED);
        assertThat(invitation.isRedeemable(now)).isFalse();
        assertThatIllegalStateException().isThrownBy(invitation::revoke);
    }

    @Test
    @DisplayName("an accepted invitation cannot be revoked — the member is already in")
    void acceptedInvitationCannotBeRevoked() {
        Instant now = Instant.now();
        Invitation invitation = Invitation.create(UUID.randomUUID(), "sara@acme.example",
                WorkspaceRole.CONSULTANT, "hash", someone, now.plusSeconds(3600));
        invitation.accept(someone, now);

        assertThatIllegalStateException().isThrownBy(invitation::revoke);
    }
}
