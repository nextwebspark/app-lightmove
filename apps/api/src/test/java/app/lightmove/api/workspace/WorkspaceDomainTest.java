package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.common.persona.model.HiringPersona;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.rbac.Role;
import app.lightmove.api.core.security.rbac.RoleScope;
import app.lightmove.api.core.security.rbac.WorkspaceRole;
import app.lightmove.api.workspace.constant.InvitationStatus;
import app.lightmove.api.workspace.constant.MemberStatus;
import app.lightmove.api.workspace.constant.WorkspaceMode;
import app.lightmove.api.workspace.model.Invitation;
import app.lightmove.api.workspace.model.Workspace;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
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
        assertThatThrownBy(() -> membership.changeRoles(Set.of(admin)))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT);
        assertThatThrownBy(membership::remove)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT);
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
    @DisplayName("a workspace is deleted once, purged after its grace period, and restorable until then")
    void workspaceDeleteStartsTheGracePeriod() {
        Workspace workspace = Workspace.create(
                "Acme Search", "acme-search", "acme.example", someone, WorkspaceMode.COMPANY, null);
        Instant now = Instant.parse("2026-11-02T10:00:00Z");

        workspace.delete(someone, now, Duration.ofDays(30), "alok@acme.example");

        assertThat(workspace.isDeleted()).isTrue();
        assertThat(workspace.getPurgeAfter()).isEqualTo(now.plus(Duration.ofDays(30)));
        assertThat(workspace.getPurgeNoticeEmail()).isEqualTo("alok@acme.example");
        assertThatThrownBy(() -> workspace.delete(someone, now, Duration.ofDays(30), null))
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT);

        workspace.restore();
        assertThat(workspace.isDeleted()).isFalse();
        assertThat(workspace.getPurgeAfter()).isNull();
        assertThat(workspace.getPurgeNoticeEmail()).isNull();
        assertThatThrownBy(workspace::restore)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.WORKSPACE_NOT_RESTORABLE);
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
        assertThatThrownBy(invitation::revoke)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("an accepted invitation cannot be revoked — the member is already in")
    void acceptedInvitationCannotBeRevoked() {
        Instant now = Instant.now();
        Invitation invitation = Invitation.create(UUID.randomUUID(), "sara@acme.example",
                member, "hash", someone, now.plusSeconds(3600));
        invitation.accept(someone, now);

        assertThatThrownBy(invitation::revoke)
                .isInstanceOf(ApiException.class)
                .extracting(ex -> ((ApiException) ex).getCode())
                .isEqualTo(ErrorCode.CONFLICT);
    }

    @Test
    @DisplayName("a persona is trimmed, blanks dropped, and repeated list entries kept once")
    void personaIsTidied() {
        HiringPersona persona = new HiringPersona("  Search  ", java.util.Arrays.asList("Energy", " ENERGY", null, " "),
                null, List.of("GCC"), "");

        assertThat(persona.summary()).isEqualTo("Search");
        assertThat(persona.sectors()).containsExactly("Energy");
        assertThat(persona.competitors()).isEmpty();
        assertThat(persona.geographies()).containsExactly("GCC");
        assertThat(persona.notes()).isNull();
    }
}
