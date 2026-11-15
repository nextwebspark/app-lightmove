package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.email.model.EmailMessage;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** #1267 phase 3: asking to share a person's profile with a position's client, the visibility warning, the register. */
@IntegrationTest
class ShareConsentIntegrationTest extends FlowTestSupport {

    private static final Pattern PAGE_LINK = Pattern.compile("/privacy/([A-Za-z0-9_-]+)");
    private static final String CONSENT_MODE = """
            {"controllerName":"Meridian Search Partners FZ-LLC","privacyContactEmail":"privacy@meridian.example",
             "noticeUrl":"https://meridian.example/privacy","defaultLawfulBasis":"legitimate_interest",
             "privacyMode":"consent","keepOnFile":true,"keepOnFileMonths":24,"shareWithClient":true}""";

    @Autowired @FixtureJdbc JdbcTemplate db;

    private String admin;
    private String workspace;
    private String position;
    private String candidate;
    private String person;

    @BeforeEach
    void setUp() throws Exception {
        String address = "leila@" + domain;
        workspace = createWorkspace(verifiedUser("Leila Haddad", address), "Sharing Firm");
        admin = login(address);
        String clientId = body(call(post("/api/v1/clients"), """
                {"customName":"Meridian Energy Group"}""").andReturn()).get("id").asText();
        position = body(call(post("/api/v1/projects"), """
                {"clientId":"%s","positionTitle":"Chief Financial Officer"}""".formatted(clientId))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        JsonNode filed = body(call(post("/api/v1/projects/" + position + "/candidates"), """
                {"fullName":"Fatima Al Mansoori","linkedinUrl":"https://www.linkedin.com/in/fatima-am/",
                 "emails":[{"value":"fatima@example.com"}]}""").andExpect(status().isCreated()).andReturn());
        candidate = filed.get("id").asText();
        person = filed.get("personId").asText();
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a share request says a confidential client by default, and names the client only when asked to")
    void confidentialByDefault() throws Exception {
        JsonNode row = body(askShare(null).andExpect(status().isOk()).andReturn());
        assertThat(row.get("candidateId").asText()).isEqualTo(candidate);
        assertThat(row.get("status").asText()).isEqualTo("requested");
        assertThat(row.get("clientNamed").asBoolean()).isFalse();

        EmailMessage request = emailTo("fatima@example.com");
        assertThat(request.subject()).isEqualTo("May we share your profile for a Chief Financial Officer role?");
        assertThat(request.textBody())
                .contains("is searching for a Chief Financial Officer for a confidential client")
                .contains("share your profile with the client")
                .doesNotContain("Meridian Energy Group")
                .doesNotContainIgnoringCase("nothing has been shared");

        JsonNode page = body(mvc.perform(get("/api/v1/privacy/links/" + tokenOf(request))).andExpect(status().isOk())
                .andReturn());
        assertThat(page.at("/consents/0/purpose").asText()).isEqualTo("share_with_client");
        assertThat(page.at("/consents/0/roleTitle").asText()).isEqualTo("Chief Financial Officer");
        assertThat(page.at("/consents/0/clientName").isNull()).isTrue();
        assertThat(page.at("/consents/0/wording").asText())
                .isEqualTo("We would like to send your profile to a confidential client for a Chief Financial Officer role.");
        assertThat(db.queryForObject("select metadata ->> 'clientIdentified' from app_lm_audit_event"
                + " where event_type = 'PERSON_CONSENT_REQUESTED' and workspace_id = ?::uuid", String.class,
                UUID.fromString(workspace))).isEqualTo("false");
    }

    @Test
    @DisplayName("naming the client puts it in the email and on the page; a confidential search refuses it")
    void namingTheClient() throws Exception {
        askShare(true).andExpect(status().isOk());
        EmailMessage named = emailTo("fatima@example.com");
        assertThat(named.textBody()).contains("for Meridian Energy Group")
                .contains("share your profile with Meridian Energy Group");
        JsonNode page = body(mvc.perform(get("/api/v1/privacy/links/" + tokenOf(named))).andReturn());
        assertThat(page.at("/consents/0/clientName").asText()).isEqualTo("Meridian Energy Group");

        String confidential = body(call(post("/api/v1/projects"), """
                {"clientId":"%s","positionTitle":"Group Treasurer"}""".formatted(clientOf(position)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        assertThat(db.update("update app_lm_position set confidential = true where project_id = ?::uuid",
                UUID.fromString(confidential))).isEqualTo(1);
        String other = body(call(post("/api/v1/projects/" + confidential + "/candidates"), """
                {"fullName":"Fatima Al Mansoori","linkedinUrl":"https://www.linkedin.com/in/fatima-am/"}""")
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        assertThat(codeOf(call(post("/api/v1/projects/" + confidential + "/candidates/" + other
                + "/share-consent-request"), """
                {"nameClient":true}""").andExpect(status().isConflict()).andReturn()))
                .isEqualTo("SHARE_CLIENT_CONFIDENTIAL");
        JsonNode list = body(call(get("/api/v1/projects/" + confidential + "/share-consents"), null)
                .andExpect(status().isOk()).andReturn());
        assertThat(list.get("clientNamable").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("the position lists who was asked, and a yes on the page shows as given")
    void answeredYes() throws Exception {
        JsonNode before = body(call(get("/api/v1/projects/" + position + "/share-consents"), null)
                .andExpect(status().isOk()).andReturn());
        assertThat(before.get("asksToShare").asBoolean()).isTrue();
        assertThat(before.get("rows").size()).isZero();

        askShare(null).andExpect(status().isOk());
        assertThat(codeOf(askShare(null).andExpect(status().isConflict()).andReturn()))
                .isEqualTo("CONSENT_RECENTLY_REQUESTED");
        String token = tokenOf(emailTo("fatima@example.com"));
        JsonNode page = body(mvc.perform(get("/api/v1/privacy/links/" + token)).andReturn());
        mvc.perform(post("/api/v1/privacy/links/" + token + "/consents").contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"answers":[{"consentId":"%s","answer":"given"}]}""".formatted(page.at("/consents/0/id")
                        .asText()))).andExpect(status().isNoContent());

        JsonNode after = body(call(get("/api/v1/projects/" + position + "/share-consents"), null).andReturn());
        assertThat(after.at("/rows/0/candidateId").asText()).isEqualTo(candidate);
        assertThat(after.at("/rows/0/status").asText()).isEqualTo("given");
        EmailMessage confirmation = Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> email.sent().stream().filter(message -> message.subject().startsWith("Your answers"))
                        .findFirst(), Optional::isPresent).orElseThrow();
        assertThat(confirmation.textBody()).contains(
                "Share your profile with a confidential client for a Chief Financial Officer role: Yes");

        JsonNode record = body(call(get("/api/v1/candidates/" + person), null).andExpect(status().isOk()).andReturn());
        assertThat(record.at("/privacy/consents/0/purpose").asText()).isEqualTo("share_with_client");
        assertThat(record.at("/privacy/consents/0/projectId").asText()).isEqualTo(position);
    }

    @Test
    @DisplayName("showing someone to the client without consent is allowed and recorded as such; with consent it is not flagged")
    void visibilityWarnsNeverBlocks() throws Exception {
        call(put("/api/v1/projects/" + position + "/candidates/" + candidate + "/client-visibility"), """
                {"clientVisible":false}""").andExpect(status().isOk());
        call(put("/api/v1/projects/" + position + "/candidates/" + candidate + "/client-visibility"), """
                {"clientVisible":true}""").andExpect(status().isOk());
        assertThat(db.queryForObject("select details ->> 'withoutShareConsent' from app_lm_person_activity"
                + " where person_id = ?::uuid and kind = 'CLIENT_VISIBILITY_CHANGED' order by occurred_at desc limit 1",
                String.class, UUID.fromString(person))).isEqualTo("true");
        assertThat(db.queryForObject("select metadata ->> 'withoutShareConsent' from app_lm_audit_event"
                + " where event_type = 'CANDIDATE_CLIENT_VISIBILITY_CHANGED' and workspace_id = ?::uuid"
                + " order by id desc limit 1", String.class, UUID.fromString(workspace))).isEqualTo("true");

        call(post("/api/v1/candidates/" + person + "/consents/recorded"), """
                {"purpose":"share_with_client","projectId":"%s","answer":"given","answeredOn":"%s"}"""
                .formatted(position, java.time.LocalDate.now(java.time.ZoneOffset.UTC))).andExpect(status().isOk());
        call(put("/api/v1/projects/" + position + "/candidates/" + candidate + "/client-visibility"), """
                {"clientVisible":false}""").andExpect(status().isOk());
        call(put("/api/v1/projects/" + position + "/candidates/" + candidate + "/client-visibility"), """
                {"clientVisible":true}""").andExpect(status().isOk());
        assertThat(db.queryForObject("select details ->> 'withoutShareConsent' from app_lm_person_activity"
                + " where person_id = ?::uuid and kind = 'CLIENT_VISIBILITY_CHANGED' order by occurred_at desc limit 1",
                String.class, UUID.fromString(person))).isNull();
    }

    @Test
    @DisplayName("a client seat reads neither the share consents, nor asks, nor downloads the position's register")
    void clientSeatIsRefused() throws Exception {
        String clientEmail = "client@share-" + domain;
        call(post("/api/v1/projects/" + position + "/representatives/invitations"), """
                {"fullName":"A Client","position":"Chair","email":"%s"}""".formatted(clientEmail))
                .andExpect(status().isOk());
        String client = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"token":"%s","fullName":"A Client","password":"%s","termsAccepted":true}"""
                        .formatted(email.latestTokenFor(clientEmail), PASSWORD)))
                .andExpect(status().isCreated()).andReturn()).get("accessToken").asText();
        askShare(null).andExpect(status().isOk());

        as(client, get("/api/v1/projects/" + position + "/share-consents")).andExpect(status().isForbidden());
        as(client, post("/api/v1/projects/" + position + "/candidates/" + candidate + "/share-consent-request")
                .contentType(MediaType.APPLICATION_JSON).content("{}")).andExpect(status().isForbidden());
        as(client, get("/api/v1/projects/" + position + "/export/consent-register")).andExpect(status().isForbidden());
        String seen = as(client, get("/api/v1/projects/" + position + "/candidates")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(seen).doesNotContainIgnoringCase("consent");
    }

    @Test
    @DisplayName("from the person's record, a member with no seat on the position can neither ask to share nor record a share")
    void personDoorNeedsTheSeat() throws Exception {
        String address = "omar@" + domain;
        inviteAndAccept(admin, "Omar Saleh", address, "MEMBER");
        String seatless = login(address);

        as(seatless, post("/api/v1/candidates/" + person + "/consent-requests")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"purposes":["share_with_client"],"projectId":"%s","nameClient":true}""".formatted(position)))
                .andExpect(status().isForbidden());
        as(seatless, post("/api/v1/candidates/" + person + "/consents/recorded")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"purpose":"share_with_client","projectId":"%s","answer":"given","answeredOn":"%s"}"""
                        .formatted(position, java.time.LocalDate.now(java.time.ZoneOffset.UTC))))
                .andExpect(status().isForbidden());
        assertThat(db.queryForObject("select count(*) from app_lm_person_consent where person_id = ?::uuid",
                Long.class, UUID.fromString(person))).isZero();
        assertThat(email.sent()).noneMatch(message -> "fatima@example.com".equalsIgnoreCase(message.to()));

        as(seatless, post("/api/v1/candidates/" + person + "/consents/recorded")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"purpose":"keep_on_file","answer":"given","answeredOn":"%s"}"""
                        .formatted(java.time.LocalDate.now(java.time.ZoneOffset.UTC))))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a role title that starts with a vowel sound reads 'an' in the email and on the page")
    void articleFollowsTheTitle() throws Exception {
        String operations = body(call(post("/api/v1/projects"), """
                {"clientId":"%s","positionTitle":"Operations Director"}""".formatted(clientOf(position)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        String filed = body(call(post("/api/v1/projects/" + operations + "/candidates"), """
                {"fullName":"Fatima Al Mansoori","linkedinUrl":"https://www.linkedin.com/in/fatima-am/"}""")
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        call(post("/api/v1/projects/" + operations + "/candidates/" + filed + "/share-consent-request"), "{}")
                .andExpect(status().isOk());

        EmailMessage request = emailTo("fatima@example.com");
        assertThat(request.subject()).isEqualTo("May we share your profile for an Operations Director role?");
        assertThat(request.textBody()).contains("is searching for an Operations Director for a confidential client");
        JsonNode page = body(mvc.perform(get("/api/v1/privacy/links/" + tokenOf(request))).andReturn());
        assertThat(page.at("/consents/0/wording").asText())
                .isEqualTo("We would like to send your profile to a confidential client for an Operations Director role.");
    }

    @Test
    @DisplayName("the register lists every request, defuses formulas, carries no contact detail, and is audited")
    void register() throws Exception {
        String formula = body(call(post("/api/v1/projects/" + position + "/candidates"), """
                {"fullName":"=HYPERLINK(1)","linkedinUrl":"https://www.linkedin.com/in/formula-person/",
                 "emails":[{"value":"formula@example.com"}]}""").andExpect(status().isCreated()).andReturn())
                .get("personId").asText();
        call(post("/api/v1/candidates/" + formula + "/consents/recorded"), """
                {"purpose":"keep_on_file","answer":"declined","answeredOn":"%s"}"""
                .formatted(java.time.LocalDate.now(java.time.ZoneOffset.UTC))).andExpect(status().isOk());
        askShare(null).andExpect(status().isOk());

        String whole = call(get("/api/v1/candidates/export/consent-register"), null)
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(whole).contains("Fatima Al Mansoori").contains("Share with client").contains("Asked")
                .contains("Keep on file").contains("Declined").contains("Recorded by hand")
                .contains("'=HYPERLINK(1)")
                .doesNotContain("fatima@example.com").doesNotContain("formula@example.com");

        String forPosition = call(get("/api/v1/projects/" + position + "/export/consent-register"), null)
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        assertThat(forPosition).contains("Fatima Al Mansoori").doesNotContain("Keep on file");
        assertThat(db.queryForObject("select count(*) from app_lm_audit_event where event_type ="
                + " 'CONSENT_REGISTER_EXPORTED' and workspace_id = ?::uuid", Long.class,
                UUID.fromString(workspace))).isEqualTo(2);
    }

    private String clientOf(String projectId) {
        return db.queryForObject("select client_id::text from app_lm_project where id = ?::uuid", String.class,
                UUID.fromString(projectId));
    }

    private ResultActions askShare(Boolean nameClient) throws Exception {
        return call(post("/api/v1/projects/" + position + "/candidates/" + candidate + "/share-consent-request"),
                nameClient == null ? "{}" : "{\"nameClient\":" + nameClient + "}");
    }

    private ResultActions as(String token, MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token));
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String content) throws Exception {
        request.header("Authorization", "Bearer " + admin);
        if (content != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(content);
        }
        return mvc.perform(request);
    }

    private EmailMessage emailTo(String address) {
        return Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> email.sent().reversed().stream()
                        .filter(message -> address.equalsIgnoreCase(message.to())).findFirst(), Optional::isPresent)
                .orElseThrow();
    }

    private static String tokenOf(EmailMessage message) {
        Matcher matcher = PAGE_LINK.matcher(message.textBody());
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
