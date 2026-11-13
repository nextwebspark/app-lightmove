package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.notification.service.NotificationEmails;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** #923: the firm's privacy details, a person's lawful basis, privacy notices and the public page they link to. */
@IntegrationTest
class PersonPrivacyNoticeIntegrationTest extends FlowTestSupport {

    private static final Pattern PAGE_LINK = Pattern.compile("/privacy/([A-Za-z0-9_-]+)");
    private static final String SETTINGS = """
            {"controllerName":"Meridian Search Partners FZ-LLC","privacyContactEmail":"privacy@meridian.example",
             "noticeUrl":"https://meridian.example/privacy","defaultLawfulBasis":"legitimate_interest"}""";

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired NotificationEmails notificationEmails;

    private String admin;
    private String workspace;
    private String position;
    private String person;

    @BeforeEach
    void setUp() throws Exception {
        String address = "alok@" + domain;
        workspace = createWorkspace(verifiedUser("Alok Kumar", address), "Notifying Firm");
        admin = login(address);
        position = mandate("Chief Financial Officer");
        person = filed("Noor Haddad", "noor-haddad", "noor@example.com");
    }

    @Test
    @DisplayName("the firm's details are an admin's, audited by field name, and a notice waits until they are set")
    void settingsGateTheNotice() throws Exception {
        assertThat(codeOf(send(person).andExpect(status().isConflict()).andReturn()))
                .isEqualTo("CANDIDATE_PRIVACY_NOT_SET_UP");
        assertThat(body(call(put("/api/v1/workspace/candidate-privacy"), """
                {"noticeUrl":"http://meridian.example/privacy","defaultLawfulBasis":"consent"}""")
                .andExpect(status().isBadRequest()).andReturn()).get("fieldErrors").has("noticeUrl")).isTrue();

        JsonNode saved = body(call(put("/api/v1/workspace/candidate-privacy"), SETTINGS)
                .andExpect(status().isOk()).andReturn());
        assertThat(saved.get("readyForNotices").asBoolean()).isTrue();
        call(put("/api/v1/workspace/candidate-privacy"), SETTINGS).andExpect(status().isOk());
        assertThat(db.queryForList("select metadata ->> 'changed' from app_lm_audit_event where workspace_id = ?::uuid"
                + " and metadata ->> 'section' = 'candidatePrivacy'", String.class, workspace))
                .containsExactly("controllerName,privacyContactEmail,noticeUrl");

        assertThat(body(call(get("/api/v1/workspace/candidate-privacy"), null).andExpect(status().isOk()).andReturn())
                .get("controllerName").asText()).isEqualTo("Meridian Search Partners FZ-LLC");
    }

    @Test
    @DisplayName("a notice goes in the firm's name with its reply-to, marks the person told, and opens their page")
    void aNoticeIsSentAndOpens() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), SETTINGS).andExpect(status().isOk());

        JsonNode record = body(send(person).andExpect(status().isOk()).andReturn());
        assertThat(record.at("/privacy/noticeChannel").asText()).isEqualTo("privacy_email");
        assertThat(record.at("/privacy/lawfulBasis").asText()).isEqualTo("legitimate_interest");
        assertThat(record.at("/privacy/basisIsDefault").asBoolean()).isTrue();

        EmailMessage notice = email.sent().getLast();
        assertThat(notice.to()).isEqualTo("noor@example.com");
        assertThat(notice.senderName()).isEqualTo("Meridian Search Partners FZ-LLC");
        assertThat(notice.replyTo()).isEqualTo("privacy@meridian.example");
        assertThat(notice.textBody()).contains("https://meridian.example/privacy").doesNotContain("Uncava ·");
        String token = tokenOf(notice);
        assertThat(db.queryForObject("select count(*) from app_lm_person_privacy_link where token_hash = ?",
                Long.class, token)).isZero();

        assertThat(codeOf(send(person).andExpect(status().isConflict()).andReturn()))
                .isEqualTo("PRIVACY_NOTICE_RECENTLY_SENT");
        assertThat(kinds()).containsOnlyOnce("PRIVACY_NOTICE_SENT");

        JsonNode page = body(mvc.perform(get("/api/v1/privacy/links/" + token)).andExpect(status().isOk()).andReturn());
        assertThat(page.get("expired").asBoolean()).isFalse();
        assertThat(page.get("controllerName").asText()).isEqualTo("Meridian Search Partners FZ-LLC");
        assertThat(page.get("source").asText()).isEqualTo("manual");
        assertThat(page.get("categories").toString()).contains("name_and_role", "work_email", "linkedin")
                .doesNotContain("noor@example.com");
        assertThat(page.toString()).doesNotContain("Noor").doesNotContain("noor@example.com");

        assertThat(codeOf(mvc.perform(get("/api/v1/privacy/links/not-a-real-token"))
                .andExpect(status().isNotFound()).andReturn())).isEqualTo("NOT_FOUND");
    }

    @Test
    @DisplayName("an objection from the page sets do not contact, suppresses them and tells the admins, once")
    void objectionFromThePage() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), SETTINGS).andExpect(status().isOk());
        send(person).andExpect(status().isOk());
        String token = tokenOf(email.sent().getLast());

        mvc.perform(post("/api/v1/privacy/links/" + token + "/objection")).andExpect(status().isNoContent());
        mvc.perform(post("/api/v1/privacy/links/" + token + "/objection")).andExpect(status().isNoContent());

        assertThat(db.queryForObject("select do_not_contact from app_lm_person where id = ?::uuid", Boolean.class,
                person)).isTrue();
        assertThat(db.queryForList("select reason from app_lm_workspace_suppression where workspace_id = ?::uuid",
                String.class, workspace)).containsOnly("OBJECTED").hasSize(2);
        assertThat(kinds()).containsOnlyOnce("PRIVACY_OBJECTED");
        assertThat(db.queryForList("select kind from app_lm_notification where workspace_id = ?::uuid", String.class,
                workspace)).containsExactly("PERSON_OBJECTED");
        notificationEmails.emailUnreadAt(Instant.now().plus(1, ChronoUnit.HOURS));
        assertThat(email.subjectsFor("alok@" + domain)).contains("A candidate objected to being contacted");
    }

    @Test
    @DisplayName("a copy or a deletion asked for is a timeline line and a bell for admins, not repeated within a day")
    void requestsFromThePage() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), SETTINGS).andExpect(status().isOk());
        send(person).andExpect(status().isOk());
        String token = tokenOf(email.sent().getLast());

        for (String kind : List.of("copy", "copy", "deletion")) {
            mvc.perform(post("/api/v1/privacy/links/" + token + "/requests")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"kind":"%s"}""".formatted(kind)))
                    .andExpect(status().isNoContent());
        }
        assertThat(db.queryForList("select details ->> 'kind' from app_lm_person_activity where person_id = ?::uuid"
                + " and kind = 'PRIVACY_REQUEST_RECEIVED' order by occurred_at", String.class, person))
                .containsExactly("copy", "deletion");
        assertThat(db.queryForList("select kind from app_lm_notification where workspace_id = ?::uuid", String.class,
                workspace)).containsExactlyInAnyOrder("COPY_REQUESTED", "DELETION_REQUESTED");
        assertThat(db.queryForObject("select do_not_contact from app_lm_person where id = ?::uuid", Boolean.class,
                person)).isFalse();
    }

    @Test
    @DisplayName("an expired link names the firm and nothing else, and refuses every request")
    void expiredLink() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), SETTINGS).andExpect(status().isOk());
        send(person).andExpect(status().isOk());
        String token = tokenOf(email.sent().getLast());
        db.update("update app_lm_person_privacy_link set expires_at = now() - interval '1 minute'"
                + " where person_id = ?::uuid", java.util.UUID.fromString(person));

        JsonNode page = body(mvc.perform(get("/api/v1/privacy/links/" + token)).andExpect(status().isOk()).andReturn());
        assertThat(page.get("expired").asBoolean()).isTrue();
        assertThat(page.get("privacyContactEmail").asText()).isEqualTo("privacy@meridian.example");
        assertThat(page.get("categories")).isEmpty();
        assertThat(codeOf(mvc.perform(post("/api/v1/privacy/links/" + token + "/objection"))
                .andExpect(status().isGone()).andReturn())).isEqualTo("PRIVACY_LINK_EXPIRED");
    }

    @Test
    @DisplayName("a bulk send skips and names who it cannot reach, and the rest are sent one each")
    void bulkSend() throws Exception {
        call(put("/api/v1/workspace/candidate-privacy"), SETTINGS).andExpect(status().isOk());
        String noEmail = filed("Omar Farouk", "omar-farouk", null);
        String barred = filed("James Whitfield", "james-whitfield", "james@example.com");
        call(put("/api/v1/candidates/" + barred + "/do-not-contact"), """
                {"doNotContact":true,"reason":"Asked not to be approached"}""").andExpect(status().isOk());

        JsonNode result = body(call(post("/api/v1/candidates/bulk/privacy-notice"), """
                {"personIds":["%s","%s","%s"]}""".formatted(person, noEmail, barred))
                .andExpect(status().isOk()).andReturn());
        assertThat(result.get("sent").asInt()).isEqualTo(1);
        assertThat(result.get("skipped").toString())
                .contains("\"" + noEmail + "\"", "no_email", "\"" + barred + "\"", "do_not_contact");
    }

    @Test
    @DisplayName("a basis recorded on the person reads back as theirs; clearing it returns to the default")
    void lawfulBasisAndMarkedByHand() throws Exception {
        JsonNode recorded = body(call(put("/api/v1/candidates/" + person + "/lawful-basis"), """
                {"lawfulBasis":"consent"}""").andExpect(status().isOk()).andReturn());
        assertThat(recorded.at("/privacy/lawfulBasis").asText()).isEqualTo("consent");
        assertThat(recorded.at("/privacy/basisIsDefault").asBoolean()).isFalse();
        assertThat(recorded.at("/privacy/basisSetByName").asText()).isEqualTo("Alok Kumar");
        JsonNode cleared = body(call(put("/api/v1/candidates/" + person + "/lawful-basis"), """
                {"lawfulBasis":null}""").andExpect(status().isOk()).andReturn());
        assertThat(cleared.at("/privacy/basisIsDefault").asBoolean()).isTrue();

        assertThat(body(call(post("/api/v1/candidates/" + person + "/privacy-notice/marked"), """
                {"notifiedOn":"2999-01-01"}""").andExpect(status().isBadRequest()).andReturn())
                .get("fieldErrors").has("notifiedOn")).isTrue();
        JsonNode marked = body(call(post("/api/v1/candidates/" + person + "/privacy-notice/marked"), """
                {"notifiedOn":"2026-03-03"}""").andExpect(status().isOk()).andReturn());
        assertThat(marked.at("/privacy/noticeChannel").asText()).isEqualTo("marked_by_hand");
        assertThat(kinds()).contains("LAWFUL_BASIS_CHANGED", "PRIVACY_NOTICE_MARKED");
    }

    @Test
    @DisplayName("the Not notified view counts people held over a month and never told")
    void notNotifiedView() throws Exception {
        db.update("update app_lm_person set created_at = now() - interval '45 days' where id = ?::uuid",
                java.util.UUID.fromString(person));
        filed("Priya Raman", "priya-raman", "priya@example.com");

        JsonNode pool = body(call(get("/api/v1/candidates?view=not_notified"), null).andExpect(status().isOk())
                .andReturn());
        assertThat(pool.at("/viewCounts/notNotified").asInt()).isEqualTo(1);
        assertThat(pool.get("totalCount").asInt()).isEqualTo(1);

        call(post("/api/v1/candidates/" + person + "/privacy-notice/marked"), """
                {"notifiedOn":"2026-03-03"}""").andExpect(status().isOk());
        assertThat(body(call(get("/api/v1/candidates?view=not_notified"), null).andReturn())
                .at("/viewCounts/notNotified").asInt()).isZero();
    }

    private String filed(String fullName, String slug, String address) throws Exception {
        String emails = address == null ? "" : """
                ,"emails":[{"value":"%s"}]""".formatted(address);
        return body(call(post("/api/v1/projects/" + position + "/candidates"), """
                {"fullName":"%s","linkedinUrl":"https://www.linkedin.com/in/%s/"%s}""".formatted(fullName, slug, emails))
                .andExpect(status().isCreated()).andReturn()).get("personId").asText();
    }

    private ResultActions send(String personId) throws Exception {
        return call(post("/api/v1/candidates/" + personId + "/privacy-notice"), null);
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String content) throws Exception {
        request.header("Authorization", "Bearer " + admin);
        if (content != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(content);
        }
        return mvc.perform(request);
    }

    private List<String> kinds() {
        return db.queryForList("select kind from app_lm_person_activity where person_id = ?::uuid", String.class,
                person);
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
