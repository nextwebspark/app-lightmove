package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.constant.PrivacyPolicy;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * #928's re-acceptance: once the terms version a person accepted is no longer the current one, {@code /me} says so, and
 * only a tick naming the current version clears it.
 */
@IntegrationTest
class TermsAcceptanceIntegrationTest extends FlowTestSupport {

    @Autowired @FixtureJdbc JdbcTemplate fixtures;

    @Test
    @DisplayName("an account that accepted the current terms owes nothing, and /me names the version in force")
    void aNewAccountIsUpToDate() throws Exception {
        String token = verifiedUser("Yara Haddad", "yara@" + domain);

        JsonNode me = me(token);
        assertThat(me.get("termsUpdateRequired").asBoolean()).isFalse();
        assertThat(me.get("termsVersion").asText()).isEqualTo(PrivacyPolicy.CURRENT_VERSION);
    }

    @Test
    @DisplayName("terms changed since the last tick ask again; only the current version clears it, and it is audited")
    void changedTermsAreAcceptedAgain() throws Exception {
        String address = "omar@" + domain;
        String token = verifiedUser("Omar Khalil", address);
        fixtures.update("UPDATE app_lm_user SET privacy_policy_version = '2020-01-01' WHERE email = ?", address);

        assertThat(me(token).get("termsUpdateRequired").asBoolean()).isTrue();

        MvcResult stale = accept(token, "2020-01-01");
        assertThat(stale.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(stale)).isEqualTo("TERMS_VERSION_OUTDATED");
        assertThat(me(token).get("termsUpdateRequired").asBoolean()).isTrue();

        MvcResult accepted = accept(token, PrivacyPolicy.CURRENT_VERSION);
        assertThat(accepted.getResponse().getStatus()).as(accepted.getResponse().getContentAsString()).isEqualTo(200);
        assertThat(body(accepted).get("termsUpdateRequired").asBoolean()).isFalse();
        assertThat(me(token).get("termsUpdateRequired").asBoolean()).isFalse();

        assertThat(fixtures.queryForObject("""
                SELECT metadata ->> 'version' FROM app_lm_audit_event event
                JOIN app_lm_user actor ON actor.id = event.actor_user_id
                WHERE event.event_type = 'USER_TERMS_ACCEPTED' AND actor.email = ?
                """, String.class, address)).isEqualTo(PrivacyPolicy.CURRENT_VERSION);

        // A second tick of the same version records nothing new.
        accept(token, PrivacyPolicy.CURRENT_VERSION);
        assertThat(fixtures.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event event
                JOIN app_lm_user actor ON actor.id = event.actor_user_id
                WHERE event.event_type = 'USER_TERMS_ACCEPTED' AND actor.email = ?
                """, Integer.class, address)).isEqualTo(1);
    }

    @Test
    @DisplayName("an account with no recorded acceptance at all is asked too")
    void noRecordIsAsked() throws Exception {
        String address = "nadia@" + domain;
        String token = verifiedUser("Nadia Saleh", address);
        fixtures.update("UPDATE app_lm_user SET privacy_policy_version = NULL, terms_accepted_at = NULL WHERE email = ?",
                address);

        assertThat(me(token).get("termsUpdateRequired").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a deployment naming no data processing agreement asks a founder for none, and records none")
    void noAgreementIsAskedUntilOneIsNamed() throws Exception {
        String token = verifiedUser("Rami Aziz", "rami@" + domain);

        JsonNode legal = body(mvc.perform(get("/api/v1/onboarding/legal").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
        assertThat(legal.get("dpaVersion").isNull()).isTrue();

        String workspaceId = createWorkspace(token, "No Agreement Firm");
        assertThat(fixtures.queryForObject("SELECT dpa_version FROM app_lm_workspace WHERE id = ?::uuid",
                String.class, workspaceId)).isNull();
    }

    private JsonNode me(String token) throws Exception {
        return body(mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
    }

    private MvcResult accept(String token, String version) throws Exception {
        return mvc.perform(post("/api/v1/auth/me/terms-acceptance")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("version", version))))
                .andReturn();
    }
}
