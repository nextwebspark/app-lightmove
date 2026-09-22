package app.lightmove.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The signup-to-workspace plumbing shared by every flow suite: each test works in its own email
 * domain (nothing rolls back — audit writes commit on another thread), and helpers speak real HTTP.
 * Subclasses declare {@code @IntegrationTest} themselves; the recording doubles come with it.
 */
public abstract class FlowTestSupport {

    protected static final String PASSWORD = "secret123";

    /**
     * How long a stream assertion waits. Generous because the work happens on another thread: the
     * LISTEN thread hands an event over and the response fills in shortly after.
     */
    protected static final long STREAM_WAIT_MS = 10_000;

    private static final AtomicInteger RUN = new AtomicInteger();

    @Autowired protected MockMvc mvc;
    @Autowired protected ObjectMapper json;
    @Autowired protected RecordingEmailSender email;
    @Autowired private RecordingProfileEnricher profileEnricher;
    @Autowired private RecordingCompanyEnricher companyEnricher;
    @Autowired private StubGeocoder geocoder;
    @Autowired protected RecordingAssistantTurnRunner assistantRunner;
    @Autowired protected RecordingCompanyDiscovery discovery;
    @Autowired private JdbcTemplate vendorCache;

    protected String domain;

    @BeforeEach
    void resetNamespace() {
        email.clear();
        // The enrichers are shared with every other suite in the context: an answer one class scripted
        // must not be what the next class's capture comes back with.
        profileEnricher.clear();
        companyEnricher.clear();
        geocoder.clear();
        assistantRunner.clear();
        discovery.clear();
        // The vendor company cache is global by design (V64), so a slug one class's capture
        // remembered would answer the next class's — and its enricher would never be asked.
        vendorCache.update("DELETE FROM app_lm_vendor_company");
        // The daily spend counter commits on its own (REQUIRES_NEW) and nothing rolls it back, so
        // one class's searches would otherwise eat the next class's day — and its assertions would
        // fail on a cap it never touched.
        vendorCache.update("DELETE FROM app_lm_workspace_daily_spend");
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

    /**
     * Signs up, clicks the emailed link, and returns the bearer token that redeeming it minted — no
     * second login, because verifying is one.
     */
    protected String verifiedUser(String name, String emailAddress) throws Exception {
        signup(name, emailAddress);
        MvcResult verified = mvc.perform(post("/api/v1/auth/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("token", email.latestTokenFor(emailAddress)))))
                .andExpect(status().isOk())
                .andReturn();
        return body(verified).get("accessToken").asText();
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

    /**
     * Waits until a turn has left RUNNING, then hands back its settled body.
     *
     * <p>The accept endpoint answers 202, so every assertion about an answer, a status or a
     * {@code finished_at} has to wait for the worker. Polling the caller's own read rather than the
     * table keeps the wait inside what the API actually exposes.
     */
    protected JsonNode awaitTurnSettled(String bearerToken, String threadId, String turnId) {
        return Awaitility.await()
                .atMost(Duration.ofMillis(STREAM_WAIT_MS))
                .pollInterval(Duration.ofMillis(50))
                .until(() -> settledTurn(bearerToken, threadId, turnId), Objects::nonNull);
    }

    private JsonNode settledTurn(String bearerToken, String threadId, String turnId)
            throws Exception {
        JsonNode thread = body(mvc.perform(get("/api/v1/assistant/threads/" + threadId)
                        .header("Authorization", "Bearer " + bearerToken))
                .andReturn());
        for (JsonNode turn : thread.get("turns")) {
            if (turn.get("id").asText().equals(turnId)
                    && !turn.get("status").asText().equals("RUNNING")) {
                return turn;
            }
        }
        return null;
    }

    /**
     * Opens an SSE stream and hands back the in-flight result.
     *
     * <p>{@code request().asyncStarted()} is what makes an SSE test possible at all: MockMvc begins
     * async processing and returns the {@code MvcResult} while the emitter stays open, and
     * {@code MockHttpServletResponse} accumulates bytes as they are written to it from whichever
     * thread is writing. Assert on it with {@link #awaitContent}.
     */
    protected MvcResult openStream(String url, String bearerToken) throws Exception {
        return mvc.perform(get(url).header("Authorization", "Bearer " + bearerToken))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    /**
     * Waits for a stream to contain something, then asserts it.
     *
     * <p>{@code untilAsserted} rather than {@code until}, so a timeout fails on the assertion and the
     * message shows what the stream <i>did</i> say — the difference between a debuggable failure and
     * "condition was not fulfilled".
     */
    protected void awaitContent(MvcResult stream, String expected) {
        Awaitility.await()
                .atMost(Duration.ofMillis(STREAM_WAIT_MS))
                .pollInterval(Duration.ofMillis(100))
                .untilAsserted(() ->
                        assertThat(stream.getResponse().getContentAsString()).contains(expected));
    }
}
