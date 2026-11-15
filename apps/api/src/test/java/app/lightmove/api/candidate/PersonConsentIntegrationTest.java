package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.candidate.service.ConsentExpiryScanner;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.retention.service.CandidateRetentionService;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

/** #1267 phase 2: consent requests per purpose, the answers on the person's page, and what lapses. */
@IntegrationTest
class PersonConsentIntegrationTest extends FlowTestSupport {

    private static final Pattern PAGE_LINK = Pattern.compile("/privacy/([A-Za-z0-9_-]+)");
    private static final String FIRM = """
            "controllerName":"Meridian Search Partners FZ-LLC","privacyContactEmail":"privacy@meridian.example",
            "noticeUrl":"https://meridian.example/privacy","defaultLawfulBasis":"legitimate_interest\"""";
    private static final String CONSENT_MODE = "{" + FIRM + """
            ,"privacyMode":"consent","keepOnFile":true,"keepOnFileMonths":24,"marketing":true}""";

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired ConsentExpiryScanner scanner;
    @Autowired CandidateRetentionService retention;

    private String admin;
    private String adminId;
    private String workspace;
    private String position;
    private String person;

    @BeforeEach
    void setUp() throws Exception {
        String address = "leila@" + domain;
        workspace = createWorkspace(verifiedUser("Leila Haddad", address), "Consenting Firm");
        admin = login(address);
        adminId = db.queryForObject("select id::text from app_lm_user where email = ?", String.class, address);
        position = mandate("Chief Financial Officer");
        person = filed("Noor Haddad", "noor-haddad", "noor@example.com");
    }

    @Test
    @DisplayName("consent mode needs the firm's details, takes 12, 24 or 36 months, and is audited by field name")
    void settingsGateConsentMode() throws Exception {
        assertThat(codeOf(call(put("/api/v1/workspace/candidate-privacy"), """
                {"defaultLawfulBasis":"legitimate_interest","privacyMode":"consent"}""")
                .andExpect(status().isConflict()).andReturn())).isEqualTo("CANDIDATE_PRIVACY_NOT_SET_UP");
        assertThat(body(call(put("/api/v1/workspace/candidate-privacy"), "{" + FIRM + ",\"keepOnFileMonths\":18}")
                .andExpect(status().isBadRequest()).andReturn()).get("fieldErrors").has("keepOnFileMonths")).isTrue();

        JsonNode saved = body(call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE)
                .andExpect(status().isOk()).andReturn());
        assertThat(saved.get("privacyMode").asText()).isEqualTo("consent");
        assertThat(saved.get("readyForConsent").asBoolean()).isTrue();
        assertThat(saved.get("marketing").asBoolean()).isTrue();
        assertThat(saved.get("renewalDaysBefore").asInt()).isEqualTo(30);

        JsonNode olderClient = body(call(put("/api/v1/workspace/candidate-privacy"), "{" + FIRM + "}")
                .andExpect(status().isOk()).andReturn());
        assertThat(olderClient.get("privacyMode").asText()).as("a field left out keeps what is held")
                .isEqualTo("consent");
        assertThat(db.queryForList("select metadata ->> 'changed' from app_lm_audit_event where workspace_id = ?::uuid"
                + " and metadata ->> 'section' = 'candidatePrivacy' order by id", String.class, workspace))
                .containsExactly("controllerName,privacyContactEmail,noticeUrl,privacyMode,marketing");
    }

    @Test
    @DisplayName("a request goes as the firm, tells the person, and refuses a second ask within 14 days")
    void aRequestIsSent() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), "{" + FIRM + "}").andExpect(status().isOk());
        assertThat(codeOf(ask(person, "keep_on_file").andExpect(status().isConflict()).andReturn()))
                .isEqualTo("CONSENT_REQUESTS_OFF");
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        assertThat(body(ask(person, "share_with_client").andExpect(status().isBadRequest()).andReturn())
                .get("fieldErrors").has("projectId")).as("a share names its position").isTrue();

        JsonNode record = body(ask(person, "keep_on_file", "marketing").andExpect(status().isOk()).andReturn());
        assertThat(record.at("/privacy/noticeChannel").asText()).isEqualTo("privacy_email");
        assertThat(record.at("/privacy/consents").size()).isEqualTo(2);
        assertThat(record.at("/privacy/consents/0/status").asText()).isEqualTo("requested");
        assertThat(record.at("/privacy/consents/0/requestedByName").asText()).isEqualTo("Leila Haddad");
        assertThat(record.at("/privacy/consents/0/requestOpenUntil").isNull()).isFalse();

        EmailMessage request = emailTo("noor@example.com");
        assertThat(request.senderName()).isEqualTo("Meridian Search Partners FZ-LLC");
        assertThat(request.replyTo()).isEqualTo("privacy@meridian.example");
        assertThat(request.subject()).isEqualTo("Meridian Search Partners FZ-LLC would like your permission");
        assertThat(request.textBody()).contains("Dear Noor,")
                .contains("keep your details on file for future roles, for 24 months")
                .contains("send you career and market updates")
                .contains("If you don't answer, nothing changes.");

        assertThat(codeOf(ask(person, "keep_on_file").andExpect(status().isConflict()).andReturn()))
                .isEqualTo("CONSENT_RECENTLY_REQUESTED");
        assertThat(kinds()).containsOnlyOnce("CONSENT_REQUESTED");
        assertThat(db.queryForList("select action from app_lm_person_consent_event where person_id = ?::uuid",
                String.class, person)).containsOnly("REQUESTED").hasSize(2);

        JsonNode pool = body(call(get("/api/v1/candidates?view=consent_asked"), null).andExpect(status().isOk())
                .andReturn());
        assertThat(pool.at("/viewCounts/consentAsked").asInt()).isEqualTo(1);
        assertThat(pool.get("totalCount").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("the person answers each purpose once; agreeing earns a confirmation with a link to withdraw")
    void answersOnThePage() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        ask(person, "keep_on_file", "marketing").andExpect(status().isOk());
        String token = tokenOf(emailTo("noor@example.com"));

        JsonNode page = body(mvc.perform(get("/api/v1/privacy/links/" + token)).andExpect(status().isOk())
                .andReturn());
        assertThat(page.get("purpose").asText()).isEqualTo("consent");
        assertThat(page.get("consents").size()).isEqualTo(2);
        assertThat(page.toString()).doesNotContain("Noor").doesNotContain("noor@example.com");
        String keep = consentIdOf(page, "keep_on_file");
        String marketing = consentIdOf(page, "marketing");

        answer(token, keep, "given", marketing, "declined").andExpect(status().isNoContent());
        answer(token, keep, "given", marketing, "declined").andExpect(status().isNoContent());
        assertThat(codeOf(answer(token, UUID.randomUUID().toString(), "given", null, null)
                .andExpect(status().isNotFound()).andReturn())).isEqualTo("NOT_FOUND");
        assertThat(body(answer(token, keep, "withdrawn", null, null).andExpect(status().isBadRequest()).andReturn())
                .get("fieldErrors").has("answer")).isTrue();

        assertThat(db.queryForObject("select status from app_lm_person_consent where id = ?::uuid", String.class,
                UUID.fromString(keep))).isEqualTo("GIVEN");
        assertThat(db.queryForObject("select valid_until > now() + interval '23 months' from app_lm_person_consent"
                + " where id = ?::uuid", Boolean.class, UUID.fromString(keep))).isTrue();
        assertThat(db.queryForObject("select status from app_lm_person_consent where id = ?::uuid", String.class,
                UUID.fromString(marketing))).isEqualTo("DECLINED");
        assertThat(db.queryForObject("select count(*) from app_lm_person_consent_event where person_id = ?::uuid"
                + " and action = 'ANSWERED'", Long.class, UUID.fromString(person))).isEqualTo(2);
        assertThat(db.queryForObject("select count(*) from app_lm_audit_event where event_type ="
                + " 'PERSON_CONSENT_ANSWERED' and actor_user_id is null and workspace_id = ?::uuid", Long.class,
                workspace)).isEqualTo(2);
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(db.queryForList(
                "select kind from app_lm_notification where workspace_id = ?::uuid", String.class, workspace))
                .containsExactly("CONSENT_ANSWERED"));

        EmailMessage confirmation = Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> email.sent().stream().filter(message -> message.subject().startsWith("Your answer"))
                        .findFirst(), Optional::isPresent).orElseThrow();
        assertThat(confirmation.subject()).isEqualTo("Your answers to Meridian Search Partners FZ-LLC");
        assertThat(confirmation.textBody()).contains("Keep your details on file for future roles: Yes, until")
                .contains("Career and market updates: No")
                .contains("Manage your consents");
        assertThat(page.at("/consents/0/wording").asText() + page.at("/consents/1/wording").asText())
                .contains("We would keep your details for 24 months");
        JsonNode reopened = body(mvc.perform(get("/api/v1/privacy/links/" + token)).andExpect(status().isOk())
                .andReturn());
        assertThat(reopened.at("/consents/0/open").asBoolean()).isFalse();
        assertThat(answerOn(reopened, "keep_on_file")).as("a reopened link says what was answered").isEqualTo("given");
        assertThat(answerOn(reopened, "marketing")).isEqualTo("declined");
        String manage = tokenOf(confirmation);
        JsonNode managed = body(mvc.perform(get("/api/v1/privacy/links/" + manage)).andExpect(status().isOk())
                .andReturn());
        assertThat(managed.get("purpose").asText()).isEqualTo("manage");
        assertThat(managed.get("consents").size()).isEqualTo(1);

        answer(manage, keep, "withdrawn", null, null).andExpect(status().isNoContent());
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> email.sent().stream()
                .filter(message -> message.subject().startsWith("Your answers")).count() == 2);
        EmailMessage withdrawal = email.sent().stream().filter(message -> message.subject().startsWith("Your answers"))
                .reduce((first, second) -> second).orElseThrow();
        assertThat(withdrawal.textBody()).contains("Keep your details on file for future roles: Withdrawn")
                .as("nothing left in force, so no manage link").doesNotContain("Manage your consents");
        assertThat(db.queryForObject("select status from app_lm_person_consent where id = ?::uuid", String.class,
                UUID.fromString(keep))).isEqualTo("WITHDRAWN");
        assertThat(body(call(get("/api/v1/candidates?view=consent_refused"), null).andReturn())
                .at("/viewCounts/consentRefused").asInt()).isEqualTo(1);
        assertThat(kinds()).contains("CONSENT_ANSWERED");
    }

    @Test
    @DisplayName("a consent with no end date keeps its withdraw link past the longest dated one")
    void manageLinkOutlastsAnUndatedConsent() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        ask(person, "keep_on_file", "marketing").andExpect(status().isOk());
        String token = tokenOf(emailTo("noor@example.com"));
        JsonNode page = body(mvc.perform(get("/api/v1/privacy/links/" + token)).andReturn());

        answer(token, consentIdOf(page, "keep_on_file"), "given", consentIdOf(page, "marketing"), "given")
                .andExpect(status().isNoContent());
        Awaitility.await().atMost(Duration.ofSeconds(10)).until(() -> email.sent().stream()
                .anyMatch(message -> message.subject().startsWith("Your answers")));
        assertThat(db.queryForObject("select expires_at > now() + interval '35 months' from app_lm_person_privacy_link"
                + " where person_id = ?::uuid and purpose = 'MANAGE'", Boolean.class, UUID.fromString(person)))
                .as("market updates last until withdrawn, so 24 months of keeping them on file is not enough")
                .isTrue();
    }

    @Test
    @DisplayName("a yes lasts as long as the request said, whatever the settings say by the time it is answered")
    void aYesLastsWhatTheRequestSaid() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        ask(person, "keep_on_file").andExpect(status().isOk());
        String token = tokenOf(emailTo("noor@example.com"));
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE.replace("\"keepOnFileMonths\":24",
                "\"keepOnFileMonths\":12")).andExpect(status().isOk());

        JsonNode page = body(mvc.perform(get("/api/v1/privacy/links/" + token)).andReturn());
        assertThat(page.at("/consents/0/keepOnFileMonths").asInt()).isEqualTo(24);
        answer(token, consentIdOf(page, "keep_on_file"), "given", null, null).andExpect(status().isNoContent());
        assertThat(db.queryForObject("select valid_until > now() + interval '23 months' from app_lm_person_consent"
                + " where person_id = ?::uuid", Boolean.class, UUID.fromString(person))).isTrue();
    }

    @Test
    @DisplayName("asking again never rewrites what a given consent agreed to, refused or declined")
    void askingAgainKeepsTheWordsAgreedTo() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        ask(person, "keep_on_file").andExpect(status().isOk());
        String token = tokenOf(emailTo("noor@example.com"));
        String keep = consentIdOf(body(mvc.perform(get("/api/v1/privacy/links/" + token)).andReturn()),
                "keep_on_file");
        answer(token, keep, "given", null, null).andExpect(status().isNoContent());
        String agreedTo = wordingOf(keep);
        assertThat(agreedTo).contains("We would keep your details for 24 months");
        db.update("update app_lm_person_consent set valid_until = now() + interval '10 days' where id = ?::uuid",
                UUID.fromString(keep));

        email.clear();
        email.refuseDeliveryTo("noor@example.com");
        scanner.scanAt(Instant.now());
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(kinds()).contains("CONSENT_REQUEST_FAILED"));
        assertThat(wordingOf(keep)).isEqualTo(agreedTo);
        assertThat(db.queryForObject("select request_wording from app_lm_person_consent where id = ?::uuid",
                String.class, UUID.fromString(keep))).as("the refused renewal's question is put back")
                .contains("We would keep your details for 24 months");

        email.clear();
        scanner.scanAt(Instant.now());
        String renewal = tokenOf(emailTo("noor@example.com"));
        assertThat(wordingOf(keep)).as("a renewal waiting leaves the consent's own words").isEqualTo(agreedTo);
        answer(renewal, keep, "declined", null, null).andExpect(status().isNoContent());
        assertThat(wordingOf(keep)).as("a no to the renewal leaves them too").isEqualTo(agreedTo);
        assertThat(db.queryForObject("select wording from app_lm_person_consent_event where consent_id = ?::uuid"
                + " and action = 'ANSWERED' and status = 'DECLINED'", String.class, UUID.fromString(keep)))
                .as("the history keeps the renewal question that was declined").startsWith("You agreed on ");
    }

    @Test
    @DisplayName("an answer recorded by hand after a request is renewed like any other")
    void recordedAfterARequestIsRenewed() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        ask(person, "keep_on_file").andExpect(status().isOk());
        emailTo("noor@example.com");
        call(post("/api/v1/candidates/" + person + "/consents/recorded"), """
                {"purpose":"keep_on_file","answer":"given","answeredOn":"%s"}""".formatted(
                java.time.LocalDate.now(java.time.ZoneOffset.UTC).minusDays(1)))
                .andExpect(status().isOk());
        db.update("update app_lm_person_consent set valid_until = now() + interval '10 days' where person_id = ?::uuid",
                UUID.fromString(person));

        email.clear();
        scanner.scanAt(Instant.now());
        assertThat(emailTo("noor@example.com").subject()).startsWith("Your consent to keep your details ends on");
    }

    @Test
    @DisplayName("someone who just said no is not asked again within 14 days")
    void aNoIsNotAskedAgainAtOnce() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        ask(person, "marketing").andExpect(status().isOk());
        String token = tokenOf(emailTo("noor@example.com"));
        answer(token, consentIdOf(body(mvc.perform(get("/api/v1/privacy/links/" + token)).andReturn()), "marketing"),
                "declined", null, null).andExpect(status().isNoContent());

        assertThat(codeOf(ask(person, "marketing").andExpect(status().isConflict()).andReturn()))
                .isEqualTo("CONSENT_RECENTLY_REQUESTED");
        db.update("update app_lm_person_consent set requested_at = now() - interval '20 days',"
                + " answered_at = now() - interval '15 days' where person_id = ?::uuid", UUID.fromString(person));
        ask(person, "marketing").andExpect(status().isOk());
    }

    @Test
    @DisplayName("a request the provider refuses is undone: the purposes it opened go, and the person is not told")
    void refusedRequest() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        email.refuseDeliveryTo("noor@example.com");

        ask(person, "keep_on_file").andExpect(status().isOk());
        Awaitility.await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(kinds()).contains("CONSENT_REQUEST_FAILED"));
        assertThat(db.queryForObject("select count(*) from app_lm_person_consent where person_id = ?::uuid",
                Long.class, UUID.fromString(person))).isZero();
        assertThat(db.queryForObject("select privacy_notified_at is null from app_lm_person where id = ?::uuid",
                Boolean.class, UUID.fromString(person))).isTrue();
        assertThat(db.queryForObject("select count(*) from app_lm_audit_event where event_type ="
                + " 'PERSON_CONSENT_REQUEST_FAILED' and workspace_id = ?::uuid", Long.class, workspace)).isEqualTo(1);

        email.clear();
        ask(person, "keep_on_file").andExpect(status().isOk());
        assertThat(emailTo("noor@example.com").to()).isEqualTo("noor@example.com");
    }

    @Test
    @DisplayName("a selection skips and names who it cannot ask, and asks the rest once each")
    void bulkRequest() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        String noEmail = filed("Omar Farouk", "omar-farouk", null);
        String agreed = filed("Priya Raman", "priya-raman", "priya@example.com");
        call(post("/api/v1/candidates/" + agreed + "/consents/recorded"), """
                {"purpose":"keep_on_file","answer":"given","answeredOn":"%s"}""".formatted(today()))
                .andExpect(status().isOk());

        JsonNode result = body(call(post("/api/v1/candidates/bulk/consent-requests"), """
                {"personIds":["%s","%s","%s"],"purposes":["keep_on_file"]}""".formatted(person, noEmail, agreed))
                .andExpect(status().isOk()).andReturn());
        assertThat(result.get("sent").asInt()).isEqualTo(1);
        assertThat(result.get("skipped").toString()).contains("\"" + noEmail + "\"", "no_email",
                "\"" + agreed + "\"", "already_given");
    }

    @Test
    @DisplayName("an answer recorded by hand keeps the day and the period, and never a future day")
    void recordedByHand() throws Exception {
        assertThat(body(call(post("/api/v1/candidates/" + person + "/consents/recorded"), """
                {"purpose":"keep_on_file","answer":"given","answeredOn":"2999-01-01"}""")
                .andExpect(status().isBadRequest()).andReturn()).get("fieldErrors").has("answeredOn")).isTrue();
        JsonNode record = body(call(post("/api/v1/candidates/" + person + "/consents/recorded"), """
                {"purpose":"keep_on_file","answer":"given","answeredOn":"%s"}""".formatted(today()))
                .andExpect(status().isOk()).andReturn());
        assertThat(record.at("/privacy/consents/0/status").asText()).isEqualTo("given");
        assertThat(record.at("/privacy/consents/0/answeredVia").asText()).isEqualTo("by_hand");
        assertThat(record.at("/privacy/consentWarning").asBoolean()).isFalse();

        JsonNode consentBasis = body(call(put("/api/v1/candidates/" + person + "/lawful-basis"), """
                {"lawfulBasis":"consent"}""").andExpect(status().isOk()).andReturn());
        assertThat(consentBasis.at("/privacy/consentWarning").asBoolean()).isFalse();
        call(post("/api/v1/candidates/" + person + "/consents/recorded"), """
                {"purpose":"keep_on_file","answer":"withdrawn","answeredOn":"%s"}""".formatted(today()))
                .andExpect(status().isOk());
        assertThat(body(call(get("/api/v1/candidates/" + person), null).andReturn())
                .at("/privacy/consentWarning").asBoolean()).as("warned, never blocked").isTrue();
        assertThat(db.queryForList("select action || ':' || status from app_lm_person_consent_event"
                + " where person_id = ?::uuid order by occurred_at", String.class, UUID.fromString(person)))
                .containsExactly("RECORDED:GIVEN", "RECORDED:WITHDRAWN");
    }

    @Test
    @DisplayName("silence changes nothing: an unanswered request expires, and only the request")
    void unansweredRequestExpires() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        ask(person, "keep_on_file").andExpect(status().isOk());
        emailTo("noor@example.com");
        String basisBefore = db.queryForObject("select coalesce(lawful_basis, 'default') from app_lm_person"
                + " where id = ?::uuid", String.class, UUID.fromString(person));

        scanner.scanAt(Instant.now().plus(31, ChronoUnit.DAYS));
        assertThat(db.queryForObject("select status from app_lm_person_consent where person_id = ?::uuid",
                String.class, UUID.fromString(person))).isEqualTo("EXPIRED");
        assertThat(db.queryForObject("select coalesce(lawful_basis, 'default') from app_lm_person where id = ?::uuid",
                String.class, UUID.fromString(person))).isEqualTo(basisBefore);
        scanner.scanAt(Instant.now().plus(32, ChronoUnit.DAYS));
        assertThat(kinds()).as("closed once").containsOnlyOnce("CONSENT_EXPIRED");
    }

    @Test
    @DisplayName("a keep-on-file consent near its end is asked about once more, then lapses if nobody answers")
    void renewalThenLapse() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        call(post("/api/v1/candidates/" + person + "/consents/recorded"), """
                {"purpose":"keep_on_file","answer":"given","answeredOn":"%s"}""".formatted(today()))
                .andExpect(status().isOk());
        db.update("update app_lm_person_consent set valid_until = now() + interval '10 days',"
                + " answered_at = now() - interval '2 years' where person_id = ?::uuid", UUID.fromString(person));

        scanner.scanAt(Instant.now());
        EmailMessage renewal = emailTo("noor@example.com");
        assertThat(renewal.subject()).startsWith("Your consent to keep your details ends on");
        assertThat(renewal.textBody()).contains("Would you like us to keep them for another 24 months?");
        scanner.scanAt(Instant.now());
        assertThat(kinds()).as("asked once").containsOnlyOnce("CONSENT_REQUESTED");
        assertThat(db.queryForObject("select status from app_lm_person_consent where person_id = ?::uuid",
                String.class, UUID.fromString(person))).as("still given while the renewal waits").isEqualTo("GIVEN");

        scanner.scanAt(Instant.now().plus(31, ChronoUnit.DAYS));
        assertThat(db.queryForObject("select status from app_lm_person_consent where person_id = ?::uuid",
                String.class, UUID.fromString(person))).isEqualTo("EXPIRED");
    }

    @Test
    @DisplayName("a yes to a renewal extends from the day the consent ends; a no leaves it to run to that day")
    void renewalAnswers() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        call(post("/api/v1/candidates/" + person + "/consents/recorded"), """
                {"purpose":"keep_on_file","answer":"given","answeredOn":"%s"}""".formatted(today()))
                .andExpect(status().isOk());
        db.update("update app_lm_person_consent set valid_until = date_trunc('day', now()) + interval '10 days',"
                + " answered_at = now() - interval '2 years' where person_id = ?::uuid", UUID.fromString(person));
        Instant endsAt = db.queryForObject("select valid_until from app_lm_person_consent where person_id = ?::uuid",
                java.sql.Timestamp.class, UUID.fromString(person)).toInstant();

        scanner.scanAt(Instant.now());
        String token = tokenOf(emailTo("noor@example.com"));
        JsonNode page = body(mvc.perform(get("/api/v1/privacy/links/" + token)).andExpect(status().isOk())
                .andReturn());
        assertThat(page.at("/consents/0/status").asText()).isEqualTo("given");
        assertThat(page.at("/consents/0/open").asBoolean()).isTrue();
        assertThat(page.at("/consents/0/keepOnFileMonths").asInt()).isEqualTo(24);
        assertThat(page.at("/consents/0/wording").asText()).startsWith("You agreed on ");
        String keep = consentIdOf(page, "keep_on_file");

        answer(token, keep, "declined", null, null).andExpect(status().isNoContent());
        assertThat(db.queryForObject("select status = 'GIVEN' and valid_until = ? and request_expires_at is null"
                        + " from app_lm_person_consent where id = ?::uuid", Boolean.class,
                java.sql.Timestamp.from(endsAt), UUID.fromString(keep))).as("a no keeps it to its date").isTrue();

        db.update("update app_lm_person_consent set request_expires_at = now() + interval '30 days',"
                + " request_link_id = (select id from app_lm_person_privacy_link where purpose = 'CONSENT'"
                + " and person_id = ?::uuid) where id = ?::uuid", UUID.fromString(person), UUID.fromString(keep));
        answer(token, keep, "given", null, null).andExpect(status().isNoContent());
        assertThat(db.queryForObject("select valid_until = ? from app_lm_person_consent where id = ?::uuid",
                Boolean.class, java.sql.Timestamp.from(endsAt.atZone(java.time.ZoneOffset.UTC).plusMonths(24)
                        .toInstant()), UUID.fromString(keep))).as("a yes extends from the end").isTrue();
    }

    @Test
    @DisplayName("a keep-on-file consent in force holds someone out of the retention review; asking does not")
    void retentionHonoursConsent() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), CONSENT_MODE).andExpect(status().isOk());
        db.update("update app_lm_project set archived_at = now(), archived_by = ?::uuid where id = ?::uuid",
                UUID.fromString(adminId), UUID.fromString(position));
        db.update("update app_lm_person set created_at = now() - interval '3 years' where id = ?::uuid",
                UUID.fromString(person));
        db.update("update app_lm_person_activity set occurred_at = now() - interval '3 years'"
                + " where person_id = ?::uuid", UUID.fromString(person));
        assertThat(dueCount()).isEqualTo(1);

        ask(person, "keep_on_file").andExpect(status().isOk());
        emailTo("noor@example.com");
        assertThat(dueCount()).as("a request is no activity").isEqualTo(1);

        call(post("/api/v1/candidates/" + person + "/consents/recorded"), """
                {"purpose":"keep_on_file","answer":"given","answeredOn":"%s"}""".formatted(today()))
                .andExpect(status().isOk());
        assertThat(dueCount()).isZero();

        db.update("update app_lm_person_consent set valid_until = now() - interval '1 day' where person_id = ?::uuid",
                UUID.fromString(person));
        assertThat(dueCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the history is append-only, goes with the person, and no client or public read carries a consent")
    void historyIsAppendOnlyAndStaffOnly() throws Exception {
        call(post("/api/v1/candidates/" + person + "/consents/recorded"), """
                {"purpose":"marketing","answer":"declined","answeredOn":"%s"}""".formatted(today()))
                .andExpect(status().isOk());
        assertThatThrownBy(() -> db.update("update app_lm_person_consent_event set status = 'GIVEN'"
                + " where person_id = ?::uuid", UUID.fromString(person))).hasMessageContaining("append-only");

        String candidates = call(get("/api/v1/projects/" + position + "/candidates"), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(candidates).doesNotContainIgnoringCase("consent");

        db.update("delete from app_lm_person where id = ?::uuid", UUID.fromString(person));
        assertThat(db.queryForObject("select count(*) from app_lm_person_consent_event where person_id = ?::uuid",
                Long.class, UUID.fromString(person))).isZero();
    }

    private long dueCount() {
        return WorkspaceScope.call(UUID.fromString(workspace),
                () -> retention.due(UUID.fromString(workspace), 10, 0).total());
    }

    private String filed(String fullName, String slug, String address) throws Exception {
        String emails = address == null ? "" : """
                ,"emails":[{"value":"%s"}]""".formatted(address);
        return body(call(post("/api/v1/projects/" + position + "/candidates"), """
                {"fullName":"%s","linkedinUrl":"https://www.linkedin.com/in/%s/"%s}""".formatted(fullName, slug, emails))
                .andExpect(status().isCreated()).andReturn()).get("personId").asText();
    }

    private ResultActions ask(String personId, String... purposes) throws Exception {
        String list = String.join("\",\"", purposes);
        return call(post("/api/v1/candidates/" + personId + "/consent-requests"), """
                {"purposes":["%s"]}""".formatted(list));
    }

    private ResultActions answer(String token, String firstId, String firstAnswer, String secondId,
                                 String secondAnswer) throws Exception {
        String second = secondId == null ? "" : """
                ,{"consentId":"%s","answer":"%s"}""".formatted(secondId, secondAnswer);
        return mvc.perform(post("/api/v1/privacy/links/" + token + "/consents")
                .contentType(MediaType.APPLICATION_JSON).content("""
                        {"answers":[{"consentId":"%s","answer":"%s"}%s]}""".formatted(firstId, firstAnswer, second)));
    }

    private String wordingOf(String consentId) {
        return db.queryForObject("select wording from app_lm_person_consent where id = ?::uuid", String.class,
                UUID.fromString(consentId));
    }

    private static String answerOn(JsonNode page, String purpose) {
        for (JsonNode consent : page.get("consents")) {
            if (consent.get("purpose").asText().equals(purpose)) {
                return consent.get("answer").asText();
            }
        }
        throw new AssertionError("no " + purpose + " on the page");
    }

    private static String consentIdOf(JsonNode page, String purpose) {
        for (JsonNode consent : page.get("consents")) {
            if (consent.get("purpose").asText().equals(purpose)) {
                return consent.get("id").asText();
            }
        }
        throw new AssertionError("no " + purpose + " on the page");
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String content) throws Exception {
        request.header("Authorization", "Bearer " + admin);
        if (content != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(content);
        }
        return mvc.perform(request);
    }

    private List<String> kinds() {
        return db.queryForList("select kind from app_lm_person_activity where person_id = ?::uuid", String.class,
                UUID.fromString(person));
    }

    private static String today() {
        return java.time.LocalDate.now(java.time.ZoneOffset.UTC).toString();
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

    private String mandate(String positionTitle) throws Exception {
        String clientId = body(call(post("/api/v1/clients"), """
                {"customName":"%s Unit"}""".formatted(positionTitle)).andReturn()).get("id").asText();
        return body(call(post("/api/v1/projects"), """
                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, positionTitle))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }
}
