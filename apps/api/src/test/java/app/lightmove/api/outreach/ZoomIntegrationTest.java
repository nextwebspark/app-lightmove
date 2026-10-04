package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.RecordingProviderTokenClient;
import app.lightmove.api.RecordingZoomApi;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.outreach.model.GrantedMailbox;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;
import tools.jackson.databind.JsonNode;

/**
 * A consultant's own Zoom account, against a recording Zoom: connected through the workspace's Zoom app with its
 * token sealed, used to put a Zoom link on a booked call's invite, cleaned up when the invite fails, and marked for
 * reconnecting when Zoom refuses its token.
 */
@IntegrationTest
class ZoomIntegrationTest extends FlowTestSupport {

    private static final String MAILBOX = "consultant@firm.example";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private RecordingZoomApi zoom;
    @Autowired private RecordingProviderTokenClient tokenEndpoint;
    @Autowired private JdbcTemplate jdbc;

    private String consultant;
    private String projectId;
    private String workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        zoom.clear();
        tokenEndpoint.clear();
        gateway.grant(new GrantedMailbox("grant-" + domain, MAILBOX, "google"));
        String email = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", email), "Meridian Search");
        consultant = login(email);
        projectId = mandate();
        workspaceId = jdbc.queryForObject("select workspace_id::text from app_lm_project where id = ?::uuid",
                String.class, projectId);
    }

    @Test
    @DisplayName("Zoom is not offered without a Zoom app; with the workspace's own, connecting seals the token")
    void connectingSealsTheToken() throws Exception {
        JsonNode before = body(as(consultant, get("/api/v1/outreach/zoom")).andExpect(status().isOk()).andReturn());
        assertThat(before.get("offered").asBoolean()).isFalse();
        as(consultant, post("/api/v1/outreach/zoom/connect")).andExpect(status().isConflict());

        ownZoomApp();
        connectZoom("zoom-code-1").andExpect(status().isFound());

        JsonNode after = body(as(consultant, get("/api/v1/outreach/zoom")).andExpect(status().isOk()).andReturn());
        assertThat(after.get("offered").asBoolean()).isTrue();
        assertThat(after.get("status").asText()).isEqualTo("ACTIVE");
        Map<String, Object> row = zoomRow();
        assertThat(row.get("zoom_user_id")).isEqualTo("zoom-user-1");
        assertThat((String) row.get("refresh_token_encrypted")).isNotBlank().doesNotContain("refresh-for-zoom-code-1");
    }

    @Test
    @DisplayName("a Zoom answer that reaches another browser, or is replayed, connects nothing")
    void aStateFromAnotherBrowserConnectsNothing() throws Exception {
        ownZoomApp();
        MvcResult started = as(consultant, post("/api/v1/outreach/zoom/connect"))
                .andExpect(status().isOk())
                .andReturn();
        String state = stateOf(started);

        String landing = mvc.perform(get("/api/v1/outreach/zoom/callback").param("state", state)
                        .param("code", "zoom-code-1"))
                .andExpect(status().isFound())
                .andReturn().getResponse().getRedirectedUrl();

        assertThat(landing).contains("/outreach/mailbox/callback").contains("error=MAILBOX_CONNECT_EXPIRED");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_zoom_connection where workspace_id = ?::uuid",
                Integer.class, workspaceId)).isZero();
    }

    @Test
    @DisplayName("a call booked with Zoom carries the Zoom link on its invite and in the drawer")
    void aZoomCallCarriesItsLink() throws Exception {
        ownZoomApp();
        connectZoom("zoom-code-1");
        connectMailbox();
        String priya = executive("Priya Raman", "priya@" + domain);

        JsonNode slots = slotsOf(priya);
        assertThat(slots.get("zoomOffered").asBoolean()).isTrue();
        book(priya, firstStartIn(slots), "ZOOM").andExpect(status().isNoContent());

        assertThat(zoom.created()).hasSize(1);
        assertThat(zoom.accessTokensUsed()).containsExactly("access-for-refresh-for-zoom-code-1");
        String joinUrl = gateway.created().getFirst().joinUrl();
        assertThat(joinUrl).startsWith("https://us05web.zoom.us/j/" + zoom.created().getFirst());
        JsonNode upcoming = body(as(consultant, get(meetings(priya))).andReturn()).get("upcoming");
        assertThat(upcoming.get(0).get("joinUrl").asText()).isEqualTo(joinUrl);
        assertThat(upcoming.get(0).get("videoProvider").asText()).isEqualTo("Zoom");
    }

    @Test
    @DisplayName("when the invite cannot be made, its Zoom meeting is deleted so no orphan is left at Zoom")
    void aFailedInviteDeletesItsZoomMeeting() throws Exception {
        ownZoomApp();
        connectZoom("zoom-code-1");
        connectMailbox();
        String priya = executive("Priya Raman", "priya@" + domain);
        Instant slot = firstStartIn(slotsOf(priya));
        gateway.failCreatingEvents(new VendorException(VendorCall.of("nylas", "create-event"),
                VendorFailureKind.BAD_REQUEST, null));

        book(priya, slot, "ZOOM").andExpect(status().is5xxServerError());

        assertThat(zoom.created()).hasSize(1);
        assertThat(zoom.deleted()).containsExactlyElementsOf(zoom.created());
        assertThat(meetingRows()).isZero();
    }

    @Test
    @DisplayName("a token Zoom refuses marks the connection for reconnecting, and books nothing")
    void aRefusedTokenNeedsReconnecting() throws Exception {
        ownZoomApp();
        connectZoom("zoom-code-1");
        connectMailbox();
        String priya = executive("Priya Raman", "priya@" + domain);
        Instant slot = firstStartIn(slotsOf(priya));
        tokenEndpoint.refuse("refresh-for-zoom-code-1");

        MvcResult refused = book(priya, slot, "ZOOM").andExpect(status().isConflict()).andReturn();

        assertThat(body(refused).get("code").asText()).isEqualTo("ZOOM_RECONNECT_NEEDED");
        assertThat(zoomRow().get("status")).isEqualTo("ERROR");
        assertThat(slotsOf(priya).get("zoomOffered").asBoolean()).isFalse();
        assertThat(gateway.created()).isEmpty();
        assertThat(zoom.created()).isEmpty();
    }

    @Test
    @DisplayName("disconnecting lets the token go at Zoom and takes Zoom out of Book a call")
    void disconnectRevokes() throws Exception {
        ownZoomApp();
        connectZoom("zoom-code-1");

        as(consultant, delete("/api/v1/outreach/zoom")).andExpect(status().isNoContent());

        assertThat(zoom.revoked()).containsExactly("refresh-for-zoom-code-1");
        assertThat(jdbc.queryForObject("select count(*) from app_lm_zoom_connection where workspace_id = ?::uuid",
                Integer.class, workspaceId)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from app_lm_audit_event where workspace_id = ?::uuid "
                + "and event_type in ('ZOOM_CONNECTED', 'ZOOM_DISCONNECTED')", Integer.class, workspaceId))
                .isEqualTo(2);
    }

    private void ownZoomApp() throws Exception {
        as(consultant, put("/api/v1/workspace/integrations/ZOOM").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mode\":\"OWN\",\"clientId\":\"meridian-zoom\",\"clientSecret\":\"zoom-secret-1\"}"))
                .andExpect(status().isOk());
    }

    private ResultActions connectZoom(String code) throws Exception {
        MvcResult started = as(consultant, post("/api/v1/outreach/zoom/connect"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = started.getResponse().getCookie("lm_zoom_connect");
        return mvc.perform(get("/api/v1/outreach/zoom/callback").param("state", stateOf(started)).param("code", code)
                .cookie(cookie));
    }

    private void connectMailbox() throws Exception {
        MvcResult started = as(consultant, post("/api/v1/outreach/mailbox/connect")
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"google\"}"))
                .andExpect(status().isOk())
                .andReturn();
        Cookie cookie = started.getResponse().getCookie("lm_mailbox_connect");
        mvc.perform(get("/api/v1/outreach/mailbox/callback").param("state", stateOf(started)).param("code", "code-1")
                        .cookie(cookie))
                .andExpect(status().isFound());
    }

    private String stateOf(MvcResult started) throws Exception {
        URI authorization = URI.create(body(started).get("authorizationUrl").asText());
        return UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");
    }

    private JsonNode slotsOf(String candidateId) throws Exception {
        return body(as(consultant, get(meetings(candidateId) + "/slots").param("minutes", "30"))
                .andExpect(status().isOk()).andReturn());
    }

    private static Instant firstStartIn(JsonNode slots) {
        for (JsonNode day : slots.get("days")) {
            if (!day.get("starts").isEmpty()) {
                return Instant.parse(day.get("starts").get(0).asText());
            }
        }
        throw new AssertionError("No free slot offered");
    }

    private ResultActions book(String candidateId, Instant start, String video) throws Exception {
        return as(consultant, post(meetings(candidateId)).contentType(MediaType.APPLICATION_JSON).content("""
                {"startsAt":"%s","minutes":30,"video":"%s","inviteAddress":"priya@%s",
                 "title":"Confidential: first conversation"}
                """.formatted(start, video, domain)));
    }

    private int meetingRows() {
        return jdbc.queryForObject("select count(*) from app_lm_person_meeting where workspace_id = ?::uuid",
                Integer.class, workspaceId);
    }

    private Map<String, Object> zoomRow() {
        return jdbc.queryForMap("select zoom_user_id, refresh_token_encrypted, status from app_lm_zoom_connection "
                + "where workspace_id = ?::uuid", workspaceId);
    }

    private String executive(String fullName, String emailAddress) throws Exception {
        String slug = fullName.toLowerCase().replace(' ', '-') + "-" + System.nanoTime();
        return body(as(consultant, post("/api/v1/projects/" + projectId + "/candidates")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"%s","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/%s","email":"%s"}
                        """.formatted(fullName, slug, emailAddress)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String mandate() throws Exception {
        String clientId = body(as(consultant, post("/api/v1/clients").contentType(MediaType.APPLICATION_JSON)
                .content("{\"customName\":\"Acme Holdings\"}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(as(consultant, post("/api/v1/projects").contentType(MediaType.APPLICATION_JSON)
                .content("{\"clientId\":\"%s\",\"positionTitle\":\"Group CFO\"}".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private String meetings(String candidateId) {
        return "/api/v1/projects/" + projectId + "/outreach/candidates/" + candidateId + "/meetings";
    }
}
