package app.lightmove.api.subprocessor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.email.model.EmailMessage;
import app.lightmove.api.positiontemplate.PositionTemplateFlowSupport;
import app.lightmove.api.subprocessor.service.SubprocessorNoticeMailer;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * #1491: a sub-processor change is published from the console at least 30 days ahead, every workspace's admins are
 * emailed once each (a failed email retried until the change takes effect), and an admin may object until then.
 */
@IntegrationTest
class SubprocessorNoticeIntegrationTest extends PositionTemplateFlowSupport {

    private static final String PLATFORM = "/api/v1/platform/subprocessor-notices";
    private static final String WORKSPACE = "/api/v1/workspace/subprocessor-notices";
    private static final String NOTICE = """
            {"provider":"%s","receives":"Call recordings","purpose":"Writing call transcripts",
             "region":"European Union","effectiveOn":"%s","listUrl":"%s"}
            """;
    private static final String LIST = "https://uncava.com/legal/subprocessors";

    @Autowired SubprocessorNoticeMailer mailer;

    private record Admin(String email, String token, String workspaceId) {
    }

    @Test
    @DisplayName("a change takes effect at least 30 days from today, and its list is an https link, each on its field")
    void theNoticePeriodAndTheLinkAreChecked() throws Exception {
        String platform = superAdmin();

        JsonNode early = expect(400, postJson(platform, PLATFORM, notice("Early Mail", today().plusDays(29), LIST)));
        assertThat(early.get("code").asString()).isEqualTo("VALIDATION_FAILED");
        assertThat(early.get("fieldErrors").get("effectiveOn").asString())
                .isEqualTo("Takes effect must be at least 30 days from today.");

        for (String link : List.of("http://uncava.com/legal/subprocessors", "uncava.com/legal", "https://",
                "https://uncava .com", "ftp://uncava.com")) {
            JsonNode refused = expect(400, postJson(platform, PLATFORM, notice("Linked Mail", today().plusDays(40),
                    link)));
            assertThat(refused.get("fieldErrors").get("listUrl").asString())
                    .as(link).isEqualTo("Enter a link that starts with https://");
        }

        JsonNode blank = expect(400, postJson(platform, PLATFORM, notice(" ", today().plusDays(40), LIST)));
        assertThat(blank.get("fieldErrors").has("provider")).isTrue();
        assertThat(count("SELECT count(*) FROM app_lm_subprocessor_notice WHERE provider IN ('Early Mail', "
                + "'Linked Mail')")).isZero();

        JsonNode published = expect(201, postJson(platform, PLATFORM, notice("On Time Mail", today().plusDays(30),
                LIST)));
        assertThat(published.get("effectiveOn").asString()).isEqualTo(today().plusDays(30).toString());
        assertThat(published.get("inEffect").asBoolean()).isFalse();
        assertThat(published.get("publishedBy").asString()).isEqualTo("Alok Kumar");
        assertThat(count("SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'SUBPROCESSOR_NOTICE_PUBLISHED' "
                + "AND target_id = '" + published.get("id").asString() + "'")).isEqualTo(1);
    }

    @Test
    @DisplayName("publishing only commits the notice: the request emails nobody, and the scheduled pass sends it")
    void publishingLeavesTheSendingToTheScheduledPass() throws Exception {
        String platform = superAdmin();
        Admin alpha = admin("Huda Saeed", "huda@" + domain, "Quiet Request Partners");

        String noticeId = publish(platform, "Tailspin Notes", today().plusDays(35));

        assertThat(noticeEmailsTo(alpha.email(), "Tailspin Notes")).isEmpty();
        assertThat(count("SELECT count(*) FROM app_lm_subprocessor_notice_email WHERE notice_id = '" + noticeId + "'"))
                .isZero();
        assertThat(count("SELECT count(*) FROM app_lm_subprocessor_notice WHERE id = '" + noticeId
                + "' AND announced_at IS NULL")).isEqualTo(1);

        mailer.announceAt(Instant.now());

        assertThat(noticeEmailsTo(alpha.email(), "Tailspin Notes")).hasSize(1);
        assertThat(count("SELECT count(*) FROM app_lm_subprocessor_notice WHERE id = '" + noticeId
                + "' AND announced_at IS NOT NULL")).isEqualTo(1);

        mailer.announceAt(Instant.now());
        assertThat(noticeEmailsTo(alpha.email(), "Tailspin Notes")).as("announced, so never walked again").hasSize(1);
    }

    @Test
    @DisplayName("every admin of every live workspace is emailed once, across two passes; members, deleted workspaces "
            + "and deleted accounts are not")
    void eachAdminIsEmailedOnce() throws Exception {
        String platform = superAdmin();
        Admin alpha = admin("Huda Saeed", "huda@" + domain, "Gulf Talent Partners");
        inviteAndAccept(alpha.token(), "Rami Second", "rami@" + domain, "ADMIN");
        inviteAndAccept(alpha.token(), "Mira Member", "mira@" + domain, "MEMBER");
        Admin gone = admin("Gina Gone", "gina@gone-" + domain, "Gone Firm");
        db.update("""
                UPDATE app_lm_workspace SET status = 'DELETED', deleted_at = now(), purge_after = now() + interval '30 days'
                WHERE id = ?::uuid""", gone.workspaceId());
        Admin erased = admin("Erin Erased", "erin@erased-" + domain, "Erased Firm");
        db.update("UPDATE app_lm_user SET status = 'DELETED' WHERE email = ?", erased.email());

        String noticeId = publish(platform, "Contoso Transcribe", today().plusDays(45));
        mailer.announceAt(Instant.now());
        // A pass cut short leaves the notice unannounced, and the next walks every workspace again.
        db.update("UPDATE app_lm_subprocessor_notice SET announced_at = NULL WHERE id = ?::uuid", noticeId);
        mailer.announceAt(Instant.now());

        assertThat(noticeEmailsTo(alpha.email(), "Contoso Transcribe")).hasSize(1);
        assertThat(noticeEmailsTo("rami@" + domain, "Contoso Transcribe")).hasSize(1);
        assertThat(noticeEmailsTo("mira@" + domain, "Contoso Transcribe")).isEmpty();
        assertThat(noticeEmailsTo(gone.email(), "Contoso Transcribe")).isEmpty();
        assertThat(noticeEmailsTo(erased.email(), "Contoso Transcribe")).isEmpty();
        assertThat(count("SELECT count(*) FROM app_lm_subprocessor_notice_email WHERE notice_id = '" + noticeId
                + "' AND workspace_id = '" + alpha.workspaceId() + "'")).isEqualTo(2);

        EmailMessage sent = noticeEmailsTo(alpha.email(), "Contoso Transcribe").getFirst();
        assertThat(sent.textBody()).contains("Contoso Transcribe", "Gulf Talent Partners", "Call recordings",
                "European Union", "/settings/data?object=" + noticeId, LIST);
    }

    @Test
    @DisplayName("a failed email is retried with backoff until the change takes effect, then given up")
    void aFailedEmailIsRetriedUntilTheChangeTakesEffect() throws Exception {
        String platform = superAdmin();
        Admin bounced = admin("Bader Bounce", "bader@" + domain, "Bounce Firm");
        email.refuseDeliveryTo(bounced.email());
        LocalDate effectiveOn = today().plusDays(31);
        Instant published = Instant.now();
        Admin leaving = admin("Lara Leaving", "lara@leaving-" + domain, "Leaving Firm");
        email.refuseDeliveryTo(leaving.email());
        String noticeId = publish(platform, "Fabrikam Search", effectiveOn);
        mailer.announceAt(published);
        assertThat(attemptsTo(noticeId, bounced)).isEqualTo(1);
        assertThat(noticeEmailsTo(bounced.email(), "Fabrikam Search")).isEmpty();
        assertThat(owedOf(platform, noticeId)).as("both refused, both owed").isEqualTo(2);

        mailer.retryAt(published.plus(Duration.ofMinutes(30)));
        assertThat(attemptsTo(noticeId, bounced)).as("not due again yet").isEqualTo(1);

        db.update("""
                UPDATE app_lm_workspace SET status = 'DELETED', deleted_at = now(), purge_after = now() + interval '30 days'
                WHERE id = ?::uuid""", leaving.workspaceId());
        mailer.retryAt(published.plus(Duration.ofHours(2)));
        assertThat(attemptsTo(noticeId, bounced)).isEqualTo(2);
        assertThat(noticeEmailsTo(bounced.email(), "Fabrikam Search")).isEmpty();
        assertThat(owedOf(platform, noticeId)).as("given up on the deleted workspace, so no longer owed").isEqualTo(1);

        email.clear();
        mailer.retryAt(published.plus(Duration.ofHours(5)));
        assertThat(noticeEmailsTo(bounced.email(), "Fabrikam Search")).hasSize(1);
        assertThat(owedOf(platform, noticeId)).isZero();
        mailer.retryAt(published.plus(Duration.ofHours(30)));
        assertThat(noticeEmailsTo(bounced.email(), "Fabrikam Search")).as("sent, so never again").hasSize(1);

        Admin late = admin("Lina Late", "lina@" + domain, "Late Firm");
        email.refuseDeliveryTo(late.email());
        db.update("UPDATE app_lm_subprocessor_notice SET announced_at = NULL WHERE id = ?::uuid", noticeId);
        mailer.announceAt(Instant.now());
        assertThat(attemptsTo(noticeId, late)).isEqualTo(1);
        email.clear();

        mailer.retryAt(effectiveOn.atStartOfDay(ZoneOffset.UTC).toInstant().plus(Duration.ofHours(1)));

        assertThat(noticeEmailsTo(late.email(), "Fabrikam Search")).as("the change has taken effect").isEmpty();
        assertThat(attemptsTo(noticeId, late)).isEqualTo(1);
    }

    @Test
    @DisplayName("an admin objects, changes it before the change takes effect, and is refused once it has")
    void anAdminObjectsUntilTheChangeTakesEffect() throws Exception {
        String platform = superAdmin();
        Admin alpha = admin("Huda Saeed", "huda@" + domain, "Meridian Search Partners");
        String noticeId = publish(platform, "Acme Mail", today().plusDays(38));

        JsonNode objected = expect(200, putJson(alpha.token(), objection(noticeId),
                reason("Our clients name every provider that may receive staff email addresses.")));
        assertThat(objected.get("objection").get("reason").asString())
                .isEqualTo("Our clients name every provider that may receive staff email addresses.");
        assertThat(objected.get("objection").get("objectedBy").asString()).isEqualTo("Huda Saeed");

        JsonNode changed = expect(200, putJson(alpha.token(), objection(noticeId), reason("  We need 60 days.  ")));
        assertThat(changed.get("objection").get("reason").asString()).isEqualTo("We need 60 days.");
        assertThat(noticeIn(getJson(alpha.token(), WORKSPACE), noticeId).get("objection").get("reason").asString())
                .isEqualTo("We need 60 days.");
        assertThat(count("SELECT count(*) FROM app_lm_subprocessor_objection WHERE notice_id = '" + noticeId + "'"))
                .isEqualTo(1);
        assertThat(count("SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'SUBPROCESSOR_OBJECTION_RECORDED'"
                + " AND target_id = '" + noticeId + "' AND metadata->>'action' = 'changed'")).isEqualTo(1);
        assertThat(count("SELECT count(*) FROM app_lm_audit_event WHERE event_type = 'SUBPROCESSOR_OBJECTION_RECORDED'"
                + " AND target_id = '" + noticeId + "' AND metadata->>'action' = 'objected'")).isEqualTo(1);

        expect(400, putJson(alpha.token(), objection(noticeId), reason("   ")));
        expect(400, putJson(alpha.token(), objection(noticeId), reason("x".repeat(2001))));
        expectRefused(404, "NOT_FOUND", putJson(alpha.token(), objection(UUID.randomUUID().toString()),
                reason("Nothing to object to.")));

        String inEffect = db.queryForObject("""
                INSERT INTO app_lm_subprocessor_notice (provider, receives, purpose, region, effective_on, list_url,
                                                        published_at)
                VALUES ('Northwind Maps', 'Place names', 'Maps', 'United States', current_date, ?,
                        now() - interval '40 days')
                RETURNING id::text""", String.class, LIST);
        expectRefused(409, "SUBPROCESSOR_NOTICE_CLOSED", putJson(alpha.token(), objection(inEffect),
                reason("Too late.")));
        assertThat(noticeIn(getJson(alpha.token(), WORKSPACE), inEffect).get("inEffect").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a member can neither read nor object, and one workspace never sees another's objection")
    void objectionsAreTheWorkspaceAdminsOwn() throws Exception {
        String platform = superAdmin();
        Admin alpha = admin("Huda Saeed", "huda@" + domain, "Alpha Search");
        Admin beta = admin("Yara Haddad", "yara@beta-" + domain, "Beta Search");
        inviteAndAccept(alpha.token(), "Mira Member", "mira@" + domain, "MEMBER");
        String member = login("mira@" + domain);
        String noticeId = publish(platform, "Northwind Storage", today().plusDays(33));

        expectRefused(403, "FORBIDDEN", mvc.perform(get(WORKSPACE).header("Authorization", bearer(member)))
                .andReturn());
        expectRefused(403, "FORBIDDEN", putJson(member, objection(noticeId), reason("I object.")));

        expect(200, putJson(alpha.token(), objection(noticeId), reason("Alpha's reason.")));

        assertThat(noticeIn(getJson(beta.token(), WORKSPACE), noticeId).get("objection").isNull()).isTrue();
        expect(200, putJson(beta.token(), objection(noticeId), reason("Beta's reason.")));
        assertThat(noticeIn(getJson(alpha.token(), WORKSPACE), noticeId).get("objection").get("reason").asString())
                .isEqualTo("Alpha's reason.");
        assertThat(noticeIn(getJson(beta.token(), WORKSPACE), noticeId).get("objection").get("reason").asString())
                .isEqualTo("Beta's reason.");

        JsonNode objections = getJson(platform, PLATFORM + "/" + noticeId + "/objections");
        assertThat(objections).hasSize(2);
        assertThat(objections.findValuesAsString("workspaceName"))
                .containsExactlyInAnyOrder("Alpha Search", "Beta Search");
        assertThat(objections.findValuesAsString("objectedBy")).containsExactlyInAnyOrder("Huda Saeed", "Yara Haddad");
        assertThat(objections.findValuesAsString("reason"))
                .containsExactlyInAnyOrder("Alpha's reason.", "Beta's reason.");

        mailer.announceAt(Instant.now());
        JsonNode listed = noticeIn(getJson(platform, PLATFORM), noticeId);
        assertThat(listed.get("objections").asLong()).isEqualTo(2);
        assertThat(listed.get("emailed").asLong()).isPositive();

        expectRefused(404, "NOT_FOUND", mvc.perform(get(PLATFORM + "/" + UUID.randomUUID() + "/objections")
                .header("Authorization", bearer(platform))).andReturn());
        expectRefused(404, "NOT_FOUND", mvc.perform(get(PLATFORM).header("Authorization", bearer(alpha.token())))
                .andReturn());
    }

    private Admin admin(String name, String address, String workspaceName) throws Exception {
        String workspaceId = createWorkspace(verifiedUser(name, address), workspaceName);
        return new Admin(address, login(address), workspaceId);
    }

    private String publish(String platform, String provider, LocalDate effectiveOn) throws Exception {
        return expect(201, postJson(platform, PLATFORM, notice(provider, effectiveOn, LIST))).get("id").asString();
    }

    private long owedOf(String platform, String noticeId) throws Exception {
        return noticeIn(getJson(platform, PLATFORM), noticeId).get("owed").asLong();
    }

    private List<EmailMessage> noticeEmailsTo(String address, String provider) {
        return email.sent().stream()
                .filter(message -> address.equalsIgnoreCase(message.to()))
                .filter(message -> message.subject().endsWith(": " + provider))
                .toList();
    }

    private Integer attemptsTo(String noticeId, Admin admin) {
        List<Integer> attempts = db.queryForList("""
                SELECT e.attempts FROM app_lm_subprocessor_notice_email e JOIN app_lm_user u ON u.id = e.user_id
                WHERE e.notice_id = ?::uuid AND u.email = ?""", Integer.class, noticeId, admin.email());
        return attempts.isEmpty() ? null : attempts.getFirst();
    }

    private int count(String sql) {
        return db.queryForObject(sql, Integer.class);
    }

    private static JsonNode noticeIn(JsonNode notices, String noticeId) {
        for (JsonNode notice : notices) {
            if (noticeId.equals(notice.get("id").asString())) {
                return notice;
            }
        }
        throw new AssertionError("No notice " + noticeId + " in " + notices);
    }

    private static String notice(String provider, LocalDate effectiveOn, String link) {
        return NOTICE.formatted(provider, effectiveOn, link);
    }

    private static String objection(String noticeId) {
        return WORKSPACE + "/" + noticeId + "/objection";
    }

    private static String reason(String text) {
        return "{\"reason\":\"" + text + "\"}";
    }

    private static LocalDate today() {
        return LocalDate.now(ZoneOffset.UTC);
    }
}
