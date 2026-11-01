package app.lightmove.api.core.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.workspace.repository.InvitationRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * V171's doors: a key names its row's workspace and nothing more, a key nobody holds names nothing, and the row itself
 * stays behind the fence until the workspace it was resolved to is the scope.
 */
@IntegrationTest
class TenantResolversIntegrationTest extends FlowTestSupport {

    @Autowired TenantResolvers resolvers;
    @Autowired InvitationRepository invitations;
    @Autowired JdbcTemplate application;
    @Autowired TransactionTemplate transactions;
    @Autowired @FixtureJdbc JdbcTemplate fixture;

    @Test
    @DisplayName("an invitation's token names its workspace; the row is read only once that is the scope")
    void aTokenNamesItsWorkspace() throws Exception {
        String founder = "founder@" + domain;
        UUID workspaceId = UUID.fromString(createWorkspace(verifiedUser("Rana Haddad", founder), "Resolver Firm"));
        String invitee = "invitee@" + domain;
        mvc.perform(post("/api/v1/invitations").header("Authorization", "Bearer " + login(founder))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("[{\"email\":\"%s\",\"role\":\"MEMBER\"}]".formatted(invitee)))
                .andExpect(status().isOk());
        String hash = Tokens.hash(email.latestTokenFor(invitee));

        ResolvedRow named = resolvers.invitation(hash).orElseThrow();

        assertThat(named.workspaceId()).isEqualTo(workspaceId);
        assertThat(invitations.findByTokenHash(hash)).as("no workspace named, the row stays fenced").isEmpty();
        assertThat(WorkspaceScope.call(workspaceId, () -> invitations.findByTokenHash(hash)))
                .hasValueSatisfying(invitation -> assertThat(invitation.getId()).isEqualTo(named.id()));
        assertThat(WorkspaceScope.call(UUID.randomUUID(), () -> invitations.findByTokenHash(hash))).isEmpty();
    }

    @Test
    @DisplayName("a key nobody holds names nothing")
    void anUnknownKeyNamesNothing() {
        assertThat(resolvers.invitation(Tokens.hash(Tokens.generate()))).isEmpty();
        assertThat(resolvers.apiKey(Tokens.hash(Tokens.generate()))).isEmpty();
        assertThat(resolvers.mailboxByBookingSlug("nobody-holds-this")).isEmpty();
        assertThat(resolvers.oauthAuthorizationsByHash(Tokens.hash(Tokens.generate()))).isEmpty();
    }

    @Test
    @DisplayName("every resolver answers ids and a workspace, never a column of the row")
    void resolversAnswerIdsOnly() {
        List<String> shapes = fixture.queryForList("""
                SELECT pg_get_function_result(oid) FROM pg_proc
                WHERE proname LIKE 'app\\_lm\\_resolve\\_%' AND pronamespace = 'public'::regnamespace""",
                String.class);

        assertThat(shapes).isNotEmpty().allSatisfy(shape ->
                assertThat(shape).isEqualTo("TABLE(id uuid, workspace_id uuid)"));
    }

    @Test
    @DisplayName("a resolver puts the maintenance flag back down, even inside its caller's transaction")
    void theFlagGoesBackDown() {
        Boolean stillRaised = transactions.execute(status -> {
            application.queryForList("SELECT id FROM app_lm_resolve_mailbox_by_booking_slug('nobody-holds-this')");
            return application.queryForObject("SELECT app_lm_rls_maintenance()", Boolean.class);
        });

        assertThat(stillRaised).isFalse();
    }
}
