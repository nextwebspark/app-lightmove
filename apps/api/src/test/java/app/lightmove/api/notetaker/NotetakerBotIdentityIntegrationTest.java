package app.lightmove.api.notetaker;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.RecordingMeetingRecorder;
import app.lightmove.api.RecordingRecallBotApi;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.notetaker.controller.NotetakerCanaryController;
import app.lightmove.api.notetaker.controller.NotetakerRunController;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
import app.lightmove.api.notetaker.service.BotIdentityVerifier;
import app.lightmove.api.notetaker.service.NotetakerBotEnvironment;
import app.lightmove.api.notetaker.service.NotetakerScheduler;
import app.lightmove.api.outreach.model.CalendarEvent;
import app.lightmove.api.outreach.model.CalendarEventChanged;
import app.lightmove.api.outreach.model.GrantedMailbox;
import jakarta.servlet.http.Cookie;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * #956: a Cloud Run bot is started without its run token and takes it once, for its own service account's ID token —
 * never another account's, never twice, and never for a run that has ended; and the daily canary, reporting with the
 * same identity, steers a failing platform's calls to the backup recorder where a workspace has it on, and (#978) has
 * its bot's pieces heard by the notes' transcriber.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.notetaker.bot-service-account=" + NotetakerBotIdentityIntegrationTest.BOT,
        "lightmove.notetaker.canary-service-account=" + NotetakerBotIdentityIntegrationTest.CANARY})
@Import(NotetakerBotIdentityIntegrationTest.Identities.class)
class NotetakerBotIdentityIntegrationTest extends FlowTestSupport {

    static final String BOT = "meetingbot@uncava-prod.iam.gserviceaccount.com";
    static final String CANARY = "notetaker-canary@uncava-prod.iam.gserviceaccount.com";
    private static final String MAILBOX = "consultant@firm.example";

    @Autowired private RecordingMailboxGateway gateway;
    @Autowired private RecordingMeetingRecorder recorder;
    @Autowired private RecordingRecallBotApi recall;
    @Autowired private NotetakerScheduler scheduler;
    @Autowired private LightMoveProperties properties;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private StubChatModel chat;

    private String grantId;
    private String consultant;
    private String projectId;

    @BeforeEach
    void setUp() throws Exception {
        gateway.clear();
        recorder.clear();
        grantId = "grant-" + domain;
        gateway.grant(new GrantedMailbox(grantId, MAILBOX, "google"));
        String email = "yara@" + domain;
        createWorkspace(verifiedUser("Yara Haddad", email), "Token Exchange " + domain);
        consultant = login(email);
        projectId = mandate();
        connectMailbox();
    }

    @Test
    @DisplayName("the bot's environment carries no token, and its own ID token buys the run's token once")
    void exchangesOnce() throws Exception {
        NotetakerLaunch launch = sendBot();
        assertThat(NotetakerBotEnvironment.forCloudRun(launch, properties))
                .doesNotContainKey("NOTETAKER_RUN_TOKEN")
                .containsKey("NOTETAKER_TOKEN_AUDIENCE");
        String run = launch.recordingId().toString();

        exchange(run, "id-token-for:someone-else@evil.example").andExpect(status().isNotFound());
        exchange(run, null).andExpect(status().isNotFound());
        exchange(run, "id-token-for:" + CANARY).andExpect(status().isNotFound());
        String token = body(exchange(run, "id-token-for:" + BOT).andExpect(status().isOk()).andReturn())
                .get("runToken").asText();

        assertThat(token).isNotBlank().isNotEqualTo(launch.runToken());
        botCall(run, "status", token, "{\"state\":\"WAITING_ROOM\"}").andExpect(status().isNoContent());
        // The token the scheduler minted never left the server, and the exchanged one replaced it.
        botCall(run, "status", launch.runToken(), "{\"state\":\"WAITING_ROOM\"}").andExpect(status().isNotFound());
        // A second exchange, an ID token replayed or a second bot, is refused.
        exchange(run, "id-token-for:" + BOT).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a run that has ended hands out no token")
    void anEndedRunHandsOutNothing() throws Exception {
        NotetakerLaunch launch = sendBot();
        String run = launch.recordingId().toString();
        jdbc.update("update app_lm_meeting_recording set status = 'FAILED', end_reason = 'LAUNCH_TIMEOUT', "
                + "run_token_hash = null where id = ?::uuid", run);

        exchange(run, "id-token-for:" + BOT).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("only the canary's own account reports, and a failure sends the platform's calls to the backup until a pass")
    void aFailedCanaryRoutesAroundOurBot() throws Exception {
        canary("id-token-for:someone-else@evil.example", false).andExpect(status().isNotFound());
        // A meeting bot, on a third party's page, may not say a platform is down.
        canary("id-token-for:" + BOT, false).andExpect(status().isNotFound());
        recall.offer(true);
        as(consultant, put("/api/v1/workspace/notetaker/backup").contentType(MediaType.APPLICATION_JSON)
                .content("{\"enabled\":true}")).andExpect(status().isOk());
        try {
            canary("id-token-for:" + CANARY, false).andExpect(status().isNoContent());
            assertThat(jdbc.queryForMap("select healthy, reason, failing_since from app_lm_notetaker_platform_health "
                    + "where platform = 'GOOGLE_MEET'"))
                    .containsEntry("healthy", false).containsEntry("reason", "JOIN_BLOCKED");

            sendBot();

            assertThat(recorder.launched()).isEmpty();
            assertThat(recall.sent()).hasSize(1);
            assertThat(jdbc.queryForObject("select fallback_reason from app_lm_meeting_recording where id = ?::uuid",
                    String.class, recall.sent().getFirst().metadata().get("recording_id")))
                    .isEqualTo("PLATFORM_UNHEALTHY");
        } finally {
            canary("id-token-for:" + CANARY, true).andExpect(status().isNoContent());
            recall.clear();
        }
        assertThat(jdbc.queryForObject("select failing_since is null from app_lm_notetaker_platform_health "
                + "where platform = 'GOOGLE_MEET'", Boolean.class)).isTrue();
    }

    @Test
    @DisplayName("only the canary's own account has a piece heard, before anything is read or asked, and a model that fails says so")
    void theCanaryHasItsPieceHeard() throws Exception {
        byte[] piece = {0x1a, 0x45, (byte) 0xdf, (byte) 0xa3, 1, 2, 3};
        chat.reset();
        try {
            chat.answerWhenSystemContains("You transcribe one piece", """
                    {"lines":[{"offsetSeconds":0.5,"speaker":"Speaker 1","text":"Purple giraffes read"},
                              {"offsetSeconds":2.0,"speaker":"Speaker 1","text":"quiet library books every morning."}]}""");
            hear("id-token-for:someone-else@evil.example", piece).andExpect(status().isNotFound());
            hear("id-token-for:" + BOT, piece).andExpect(status().isNotFound());
            hear("id-token-for:" + CANARY, new byte[0]).andExpect(status().isBadRequest());
            hear("id-token-for:" + CANARY, new byte[(int) properties.notetaker().maxMediaBytes() + 1])
                    .andExpect(status().isBadRequest());
            assertThat(chat.prompts()).isEmpty();

            hear("id-token-for:" + CANARY, piece).andExpect(status().isOk())
                    .andExpect(jsonPath("$.heard").value("Purple giraffes read quiet library books every morning."));

            chat.answerWhenSystemContains("You transcribe one piece",
                    "{\"lines\":[{\"offsetSeconds\":0,\"speaker\":\"" + BlockedAnswer.MARKER + "\",\"text\":\"\"}]}");
            hear("id-token-for:" + CANARY, piece).andExpect(status().isBadGateway())
                    .andExpect(jsonPath("$.code").value("AI_UNAVAILABLE"));
        } finally {
            chat.reset();
        }
    }

    private ResultActions hear(String identity, byte[] piece) throws Exception {
        return mvc.perform(post(NotetakerCanaryController.HEARING_PATH)
                .header(NotetakerRunController.BOT_IDENTITY_HEADER, identity)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .content(piece));
    }

    private ResultActions canary(String identity, boolean healthy) throws Exception {
        return mvc.perform(post(NotetakerCanaryController.PATH)
                .header(NotetakerRunController.BOT_IDENTITY_HEADER, identity)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"platform\":\"GOOGLE_MEET\",\"healthy\":" + healthy
                        + (healthy ? "" : ",\"reason\":\"JOIN_BLOCKED\"") + "}"));
    }

    private NotetakerLaunch sendBot() throws Exception {
        executive("priya@" + domain);
        Instant start = Instant.now().truncatedTo(ChronoUnit.SECONDS).plus(Duration.ofMinutes(1));
        gateway.deliverNext(List.of(new CalendarEventChanged(grantId, new CalendarEvent("call-1", "Conversation",
                start, start.plus(Duration.ofMinutes(30)), List.of("priya@" + domain, MAILBOX),
                "https://meet.google.com/call-1", "Google Meet"))));
        mvc.perform(post("/api/v1/outreach/webhooks/mailbox")
                        .header("X-Nylas-Signature", RecordingMailboxGateway.VALID_SIGNATURE)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk());
        scheduler.scheduleAt(Instant.now());
        return recorder.launched().isEmpty() ? null : recorder.launched().getLast();
    }

    private ResultActions exchange(String runId, String identity) throws Exception {
        MockHttpServletRequestBuilder request = post(NotetakerRunController.BASE + "/" + runId + "/token");
        if (identity != null) {
            request.header(NotetakerRunController.BOT_IDENTITY_HEADER, identity);
        }
        return mvc.perform(request);
    }

    private ResultActions botCall(String runId, String action, String token, String content) throws Exception {
        return mvc.perform(post(NotetakerRunController.BASE + "/" + runId + "/" + action)
                .header(NotetakerRunController.RUN_TOKEN_HEADER, token)
                .contentType(MediaType.APPLICATION_JSON).content(content));
    }

    private void executive(String emailAddress) throws Exception {
        as(consultant, post("/api/v1/projects/" + projectId + "/candidates").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"fullName":"Priya Raman","title":"Chief Financial Officer","employerName":"Target Group",
                         "linkedinUrl":"https://www.linkedin.com/in/priya-%d","email":"%s"}
                        """.formatted(System.nanoTime(), emailAddress)))
                .andExpect(status().isCreated());
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

    private void connectMailbox() throws Exception {
        MvcResult started = as(consultant, post("/api/v1/outreach/mailbox/connect")
                .contentType(MediaType.APPLICATION_JSON).content("{\"provider\":\"google\"}"))
                .andExpect(status().isOk())
                .andReturn();
        URI authorization = URI.create(body(started).get("authorizationUrl").asText());
        String state = UriComponentsBuilder.fromUri(authorization).build().getQueryParams().getFirst("state");
        Cookie cookie = started.getResponse().getCookie("lm_mailbox_connect");
        mvc.perform(get("/api/v1/outreach/mailbox/callback").param("state", state).param("code", "code-1")
                        .cookie(cookie))
                .andExpect(status().isFound());
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    /** An ID token here is {@code id-token-for:<email>}; Google's signature is {@code GoogleBotIdentityVerifierTest}'s. */
    @TestConfiguration(proxyBeanMethods = false)
    static class Identities {

        @Bean
        @Primary
        BotIdentityVerifier testBotIdentities() {
            return idToken -> idToken.startsWith("id-token-for:")
                    ? Optional.of(idToken.substring("id-token-for:".length())) : Optional.empty();
        }
    }
}
