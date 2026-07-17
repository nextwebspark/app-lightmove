package app.lightmove.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The signup-to-workspace plumbing shared by every flow suite: each test works in its own email
 * domain (nothing rolls back — audit writes commit on another thread), and helpers speak real HTTP.
 * Subclasses declare {@code @IntegrationTest @Import(RecordingEmailSender.Config.class)} themselves.
 */
public abstract class FlowTestSupport {

    protected static final String PASSWORD = "secret123";
    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected RecordingEmailSender email;

    protected String domain;

    @BeforeEach
    void resetNamespace() {
        email.clear();
        domain = "firm%d-%s.example".formatted(RUN.incrementAndGet(),
                getClass().getSimpleName().toLowerCase());
    }

    protected JsonNode signup(String name, String emailAddress) throws Exception {
        return body(mvc.perform(post("/api/v1/auth/signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","email":"%s","password":"%s","termsAccepted":true}
                                """.formatted(name, emailAddress, PASSWORD)))
                .andReturn());
    }

    /** Signs up, clicks the emailed link, and returns a bearer token that says "verified". */
    protected String verifiedUser(String name, String emailAddress) throws Exception {
        signup(name, emailAddress);
        mvc.perform(post("/api/v1/auth/verify").param("token", email.latestTokenFor(emailAddress)))
                .andExpect(status().isOk());
        return login(emailAddress);
    }

    protected String login(String emailAddress) throws Exception {
        JsonNode body = body(mvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(emailAddress, PASSWORD)))
                .andReturn());
        assertThat(body.get("accessToken")).as("login for %s failed: %s", emailAddress, body).isNotNull();
        return body.get("accessToken").asText();
    }

    /** @return the new workspace's id. The caller's token stays stale; re-login for tenant claims. */
    protected String createWorkspace(String bearerToken, String name) throws Exception {
        MvcResult result = mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","companySize":"11-50 people","primaryRegion":"GCC",
                                 "teamFocus":"Executive search"}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return body(result).at("/workspace/id").asText();
    }

    /** Admin invites, invitee signs up verified and accepts — the fast path to a second member. */
    protected void inviteAndAccept(String adminToken, String name, String inviteeEmail, String role)
            throws Exception {
        mvc.perform(post("/api/v1/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                [{"email":"%s","role":"%s"}]
                                """.formatted(inviteeEmail, role)))
                .andExpect(status().isOk());

        String token = email.latestTokenFor(inviteeEmail);
        String invitee = verifiedUser(name, inviteeEmail);
        mvc.perform(post("/api/v1/onboarding/invitations/accept")
                        .header("Authorization", "Bearer " + invitee)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s"}
                                """.formatted(token)))
                .andExpect(status().isOk());
    }

    /** The member id of the given email on the roster, read as the given caller. */
    protected String memberIdOf(String callerToken, String memberEmail) throws Exception {
        JsonNode roster = body(mvc.perform(get("/api/v1/members")
                        .header("Authorization", "Bearer " + callerToken))
                .andExpect(status().isOk())
                .andReturn());
        for (JsonNode member : roster) {
            if (member.get("email").asText().equals(memberEmail)) {
                return member.get("memberId").asText();
            }
        }
        throw new AssertionError(memberEmail + " not on the roster: " + roster);
    }

    protected JsonNode body(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    protected String codeOf(MvcResult result) throws Exception {
        JsonNode node = body(result).get("code");
        return node == null ? null : node.asText();
    }
}
