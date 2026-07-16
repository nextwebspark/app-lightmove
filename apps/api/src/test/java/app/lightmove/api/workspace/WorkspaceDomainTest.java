package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.RoleScope;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The state machines governing membership, deletion and invitations — the invariants every service
 * above them assumes.
 */
class WorkspaceDomainTest {

    private final UUID someone = UUID.randomUUID();
    private final Role member = Role.of(RoleScope.WORKSPACE, WorkspaceRole.MEMBER.name(), null);
    private final Role admin = Role.of(RoleScope.WORKSPACE, WorkspaceRole.ADMIN.name(), null);

    @Test
    @DisplayName("a removed membership stays removed — no role change resurrects it")
    void removedMembershipIsFinal() {
        WorkspaceMember membership = WorkspaceMember.invite(
                UUID.randomUUID(), someone, Set.of(member), someone);
        membership.remove();

        assertThat(membership.getStatus()).isEqualTo(MemberStatus.REMOVED);
        assertThatIllegalStateException()
                .isThrownBy(() -> membership.changeRoles(Set.of(admin)));
        assertThatIllegalStateException().isThrownBy(membership::remove);
    }

    @Test
    @DisplayName("changing roles replaces the whole set — the caller states what the member holds now")
    void changeRolesReplacesTheSet() {
        WorkspaceMember membership = WorkspaceMember.invite(
                UUID.randomUUID(), someone, Set.of(member), someone);

        membership.changeRoles(Set.of(admin));

        assertThat(membership.getRoles()).containsExactly(admin);
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
                member, "hash", someone, now.plusSeconds(3600));

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
                member, "hash", someone, now.plusSeconds(3600));
        invitation.accept(someone, now);

        assertThatIllegalStateException().isThrownBy(invitation::revoke);
    }
}
