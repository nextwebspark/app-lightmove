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
import java.time.Duration;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/**
 * #1490 (B2, B6): once counsel's notice to executives is published, every email a candidate receives links it, the
 * public privacy page links it, and the version a person was sent is recorded once the email is delivered.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.legal.candidate-notice-version=2026-12",
        "lightmove.legal.candidate-notice-url=https://uncava.com/executive-privacy"})
class UncavaCandidateNoticeIntegrationTest extends FlowTestSupport {

    private static final String NOTICE = "https://uncava.com/executive-privacy";
    private static final Pattern PAGE_LINK = Pattern.compile("/privacy/([A-Za-z0-9_-]+)");

    @Autowired @FixtureJdbc JdbcTemplate db;

    private String admin;

    @Test
    @DisplayName("a privacy notice links Uncava's notice, records its version once delivered, and the page links it")
    void theNoticeIsLinkedAndRecorded() throws Exception {
        String position = firmWithPosition("notice-firm", "Notice Firm");
        String person = candidate(position, "Noor Haddad", "noor-haddad", "noor@example.com");

        call(post("/api/v1/candidates/" + person + "/privacy-notice"), null).andExpect(status().isOk());

        EmailMessage notice = Awaitility.await().atMost(Duration.ofSeconds(10))
                .until(() -> email.sent().reversed().stream()
                        .filter(message -> "noor@example.com".equalsIgnoreCase(message.to())).findFirst(),
                        Optional::isPresent)
                .orElseThrow();
        assertThat(notice.textBody()).contains("How Uncava handles your details: " + NOTICE);
        assertThat(notice.htmlBody()).contains("href=\"" + NOTICE + "\"");
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(db.queryForMap(
                "select uncava_notice_version, uncava_notice_sent_at is not null as sent from app_lm_person"
                        + " where id = ?::uuid", person))
                .containsEntry("uncava_notice_version", "2026-12").containsEntry("sent", true));

        Matcher token = PAGE_LINK.matcher(notice.textBody());
        assertThat(token.find()).isTrue();
        JsonNode page = body(mvc.perform(get("/api/v1/privacy/links/" + token.group(1)))
                .andExpect(status().isOk()).andReturn());
        assertThat(page.get("uncavaNoticeUrl").asText()).isEqualTo(NOTICE);
        assertThat(body(call(get("/api/v1/data-protection/config"), null).andReturn())
                .get("candidateNoticeUrl").asText()).isEqualTo(NOTICE);
    }

    @Test
    @DisplayName("in one send, a refused notice is undone and a delivered one records its version, each on its own")
    void refusedAndDeliveredAreRecordedApart() throws Exception {
        String position = firmWithPosition("split-firm", "Split Firm");
        String delivered = candidate(position, "Dana Delivered", "dana-delivered", "dana@example.com");
        String refused = candidate(position, "Rami Refused", "rami-refused", "rami@example.com");
        email.refuseDeliveryTo("rami@example.com");

        call(post("/api/v1/candidates/bulk/privacy-notice"), """
                {"personIds":["%s","%s"]}""".formatted(delivered, refused)).andExpect(status().isOk());

        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> {
            assertThat(db.queryForObject("select uncava_notice_version from app_lm_person where id = ?::uuid",
                    String.class, delivered)).isEqualTo("2026-12");
            assertThat(db.queryForObject("select count(*) from app_lm_audit_event where event_type ="
                    + " 'PERSON_PRIVACY_NOTICE_FAILED' and target_id = ?", Long.class, refused)).isEqualTo(1);
        });
        assertThat(db.queryForMap("select uncava_notice_version, privacy_notified_at from app_lm_person"
                + " where id = ?::uuid", refused)).containsEntry("uncava_notice_version", null)
                .containsEntry("privacy_notified_at", null);
    }

    private String firmWithPosition(String mailbox, String firmName) throws Exception {
        String address = mailbox + "@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), firmName);
        admin = login(address);
        call(put("/api/v1/workspace/candidate-privacy"), """
                {"controllerName":"Meridian Search Partners FZ-LLC","privacyContactEmail":"privacy@meridian.example",
                 "noticeUrl":"https://meridian.example/privacy","defaultLawfulBasis":"legitimate_interest"}""")
                .andExpect(status().isOk());
        String clientId = body(call(post("/api/v1/clients"), "{\"customName\":\"Finance Unit\"}").andReturn())
                .get("id").asText();
        return body(call(post("/api/v1/projects"), """
                {"clientId":"%s","positionTitle":"Chief Financial Officer"}""".formatted(clientId))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
    }

    private String candidate(String position, String fullName, String slug, String address) throws Exception {
        return body(call(post("/api/v1/projects/" + position + "/candidates"), """
                {"fullName":"%s","linkedinUrl":"https://www.linkedin.com/in/%s/",
                 "emails":[{"value":"%s"}]}""".formatted(fullName, slug, address))
                .andExpect(status().isCreated()).andReturn()).get("personId").asText();
    }

    private ResultActions call(MockHttpServletRequestBuilder request, String content) throws Exception {
        request.header("Authorization", "Bearer " + admin);
        if (content != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(content);
        }
        return mvc.perform(request);
    }
}
