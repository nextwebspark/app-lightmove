package app.lightmove.api.outreach;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.List;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import tools.jackson.databind.JsonNode;

/**
 * Email templates (docs/email-templates.md, phase 1): a personal template is its owner's alone, a shared one every staff
 * member's to apply and only its owner's or an admin's to change — audited — and every template holds only the
 * variables an email can fill.
 */
@IntegrationTest
class EmailTemplateIntegrationTest extends FlowTestSupport {

    private static final String TEMPLATES = "/api/v1/me/email-templates";

    @Autowired @FixtureJdbc private JdbcTemplate jdbc;

    private String yara;
    private String omar;
    private String workspaceId;

    @BeforeEach
    void setUp() throws Exception {
        String yaraEmail = "yara@" + domain;
        workspaceId = createWorkspace(verifiedUser("Yara Haddad", yaraEmail), "Al-Futtaim");
        yara = login(yaraEmail);
        inviteAndAccept(yara, "Omar Saleh", "omar@" + domain, "MEMBER");
        omar = login("omar@" + domain);
    }

    @Test
    @DisplayName("a personal template is its owner's alone; a colleague, even an admin, reads it as missing")
    void personalIsPrivate() throws Exception {
        JsonNode created = create(omar, template("Follow-up", "Quick follow-up", "Hi {{firstName}},", false));
        String id = created.get("id").asText();
        assertThat(created.get("mine").asBoolean()).isTrue();
        assertThat(created.get("canEdit").asBoolean()).isTrue();
        assertThat(created.get("owner").get("fullName").asText()).isEqualTo("Omar Saleh");

        assertThat(namesOf(list(yara))).isEmpty();
        expect(yara, put(TEMPLATES + "/" + id), edit("Mine now", 0), status().isNotFound(), "EMAIL_TEMPLATE_NOT_FOUND");
        expect(yara, post(TEMPLATES + "/" + id + "/duplicate"), null, status().isNotFound(), "EMAIL_TEMPLATE_NOT_FOUND");
        mvc.perform(delete(TEMPLATES + "/" + id).param("version", "0")
                        .header("Authorization", "Bearer " + yara).with(csrf()))
                .andExpect(status().isNotFound());

        mvc.perform(put(TEMPLATES + "/" + id).header("Authorization", "Bearer " + omar).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(edit("Renamed", 0)))
                .andExpect(status().isOk());
        assertThat(auditedTemplateEvents()).isEmpty();
    }

    @Test
    @DisplayName("a shared template is everyone's to apply and duplicate; a colleague's change is refused, an admin's audited")
    void sharedIsHeldToOwnerAndAdmin() throws Exception {
        JsonNode shared = create(omar, template("Standard reply", null, "Thanks {{firstName|there}},", true));
        String id = shared.get("id").asText();
        assertThat(auditedTemplateEvents()).containsExactly("EMAIL_TEMPLATE_SHARED");

        String layla = colleague("Layla Aziz", "layla@" + domain);
        JsonNode seen = only(list(layla));
        assertThat(seen.get("shared").asBoolean()).isTrue();
        assertThat(seen.get("mine").asBoolean()).isFalse();
        assertThat(seen.get("canEdit").asBoolean()).isFalse();
        assertThat(seen.get("plainBody").asText()).isEqualTo("Thanks {{firstName|there}},");
        expect(layla, put(TEMPLATES + "/" + id), edit("Hijacked", 0), status().isForbidden(),
                "EMAIL_TEMPLATE_NOT_YOURS");
        expect(layla, put(TEMPLATES + "/" + id + "/sharing"), """
                {"shared":false,"version":0}""", status().isForbidden(), "EMAIL_TEMPLATE_NOT_YOURS");

        JsonNode copy = body(mvc.perform(post(TEMPLATES + "/" + id + "/duplicate")
                        .header("Authorization", "Bearer " + layla).with(csrf()))
                .andExpect(status().isCreated()).andReturn());
        assertThat(copy.get("name").asText()).isEqualTo("Copy of Standard reply");
        assertThat(copy.get("shared").asBoolean()).isFalse();
        assertThat(copy.get("owner").get("fullName").asText()).isEqualTo("Layla Aziz");

        assertThat(only(list(yara)).get("canEdit").asBoolean()).isTrue();
        JsonNode edited = body(mvc.perform(put(TEMPLATES + "/" + id).header("Authorization", "Bearer " + yara)
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(edit("Standard reply v2", 0)))
                .andExpect(status().isOk()).andReturn());
        assertThat(edited.get("owner").get("fullName").asText()).isEqualTo("Omar Saleh");
        assertThat(jdbc.queryForObject("select updated_by from app_lm_email_template where id = ?::uuid",
                String.class, id)).isNotNull();

        mvc.perform(put(TEMPLATES + "/" + id + "/sharing").header("Authorization", "Bearer " + omar).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"shared\":false,\"version\":"
                                + edited.get("version").asLong() + "}"))
                .andExpect(status().isOk());
        assertThat(namesOf(list(layla))).containsExactly("Copy of Standard reply");
        assertThat(auditedTemplateEvents())
                .containsExactly("EMAIL_TEMPLATE_SHARED", "EMAIL_TEMPLATE_UPDATED", "EMAIL_TEMPLATE_UNSHARED");

        JsonNode log = body(mvc.perform(get("/api/v1/workspace/audit-events").param("group", "outreach")
                        .header("Authorization", "Bearer " + yara))
                .andExpect(status().isOk()).andReturn()).get("entries");
        assertThat(StreamSupport.stream(log.spliterator(), false).map(entry -> entry.get("summary").asText()))
                .contains("Edited a shared email template", "Stopped sharing an email template");
    }

    @Test
    @DisplayName("deleting a shared template is audited and names it nowhere once gone; a personal one is not audited")
    void deleteAudit() throws Exception {
        JsonNode shared = create(omar, template("Firm intro", "Hello from {{senderFirstName}}", "Hi {{firstName}}", true));
        JsonNode personal = create(omar, template("Draft", null, "Just me", false));
        JsonNode log = body(mvc.perform(get("/api/v1/workspace/audit-events")
                        .header("Authorization", "Bearer " + yara))
                .andExpect(status().isOk()).andReturn()).get("entries");
        assertThat(log.toString()).contains("Shared the email template \\\"Firm intro\\\"");

        expect(omar, delete(TEMPLATES + "/" + shared.get("id").asText()).param("version", "7"), null,
                status().isConflict(), "EMAIL_TEMPLATE_STALE");
        mvc.perform(delete(TEMPLATES + "/" + shared.get("id").asText()).param("version", "0")
                        .header("Authorization", "Bearer " + omar).with(csrf()))
                .andExpect(status().isNoContent());
        mvc.perform(delete(TEMPLATES + "/" + personal.get("id").asText()).param("version", "0")
                        .header("Authorization", "Bearer " + omar).with(csrf()))
                .andExpect(status().isNoContent());
        assertThat(auditedTemplateEvents()).containsExactly("EMAIL_TEMPLATE_SHARED", "EMAIL_TEMPLATE_DELETED");
        assertThat(namesOf(list(omar))).isEmpty();
    }

    @Test
    @DisplayName("a template holds only the variables an email fills: no opener, no signature, nothing unknown")
    void tokenRules() throws Exception {
        assertFieldRefused(template("Opener", null, "Hi {{firstName}}, {{opener}}", false), "body");
        assertFieldRefused(template("Signed", null, "Thanks,\n\n{{signature}}", false), "body");
        assertFieldRefused(template("Typo", "For {{curentTitle}}", "Hi", false), "subject");
        assertFieldRefused(template("Two lines", "One\\nTwo", "Hi", false), "subject");

        JsonNode saved = create(omar, template("All six", "{{positionTitle}} in {{location}}",
                "Hi {{firstName|there}}, {{currentTitle}} at {{currentCompany}}. {{senderFirstName}}", false));
        assertThat(saved.get("subject").asText()).isEqualTo("{{positionTitle}} in {{location}}");
        MvcResult escapedPipe = mvc.perform(post(TEMPLATES).header("Authorization", "Bearer " + omar).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(template("Escaped", null, "Hi {{firstName\\\\|there}}", false)))
                .andExpect(status().isCreated()).andReturn();
        assertThat(body(escapedPipe).get("plainBody").asText()).isEqualTo("Hi {{firstName|there}}");
    }

    @Test
    @DisplayName("a stale version is refused, and a person keeps at most 100, by create or by duplicate")
    void staleAndLimit() throws Exception {
        JsonNode first = create(omar, template("First", null, "Hi", false));
        String id = first.get("id").asText();
        mvc.perform(put(TEMPLATES + "/" + id).header("Authorization", "Bearer " + omar).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(edit("Second edit", 0)))
                .andExpect(status().isOk());
        expect(omar, put(TEMPLATES + "/" + id), edit("Lost update", 0), status().isConflict(), "EMAIL_TEMPLATE_STALE");

        jdbc.update("""
                insert into app_lm_email_template (workspace_id, owner_user_id, name, body)
                select t.workspace_id, t.owner_user_id, 'Filler ' || n, 'Hi' from app_lm_email_template t,
                       generate_series(1, 99) n where t.id = ?::uuid""", id);
        expect(omar, post(TEMPLATES), template("One too many", null, "Hi", false), status().isConflict(),
                "EMAIL_TEMPLATE_LIMIT_REACHED");
        expect(omar, post(TEMPLATES + "/" + id + "/duplicate"), null, status().isConflict(),
                "EMAIL_TEMPLATE_LIMIT_REACHED");
        assertThat(list(omar).get("limit").asInt()).isEqualTo(100);
        create(yara, template("Someone else's count", null, "Hi", false));
    }

    @Test
    @DisplayName("a pure client reaches none of it, and another workspace's shared templates never show")
    void clientAndTenancy() throws Exception {
        create(omar, template("Shared here", null, "Hi", true));
        String client = clientRepresentative(yara, "Nadia Saleh", "nadia@client-" + domain);
        mvc.perform(get(TEMPLATES).header("Authorization", "Bearer " + client)).andExpect(status().isNotFound());
        mvc.perform(post(TEMPLATES).header("Authorization", "Bearer " + client).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(template("Nope", null, "Hi", false)))
                .andExpect(status().isNotFound());

        String ritaEmail = "rita@" + domain;
        createWorkspace(verifiedUser("Rita Rival", ritaEmail), "Rival Partners");
        String rita = login(ritaEmail);
        assertThat(namesOf(list(rita))).isEmpty();
        assertThat(namesOf(list(omar))).containsExactly("Shared here");
    }

    private String colleague(String name, String email) throws Exception {
        inviteAndAccept(yara, name, email, "MEMBER");
        return login(email);
    }

    private void assertFieldRefused(String json, String field) throws Exception {
        MvcResult refused = mvc.perform(post(TEMPLATES).header("Authorization", "Bearer " + omar).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isBadRequest()).andReturn();
        assertThat(codeOf(refused)).isEqualTo("VALIDATION_FAILED");
        assertThat(body(refused).get("fieldErrors").has(field)).isTrue();
    }

    private void expect(String token, org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                        String json, ResultMatcher expected, String code) throws Exception {
        request.header("Authorization", "Bearer " + token).with(csrf());
        if (json != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json);
        }
        MvcResult result = mvc.perform(request).andExpect(expected).andReturn();
        assertThat(codeOf(result)).isEqualTo(code);
    }

    private List<String> auditedTemplateEvents() {
        return jdbc.queryForList("""
                select event_type from app_lm_audit_event
                 where workspace_id = ?::uuid and event_type like 'EMAIL_TEMPLATE_%' order by id""",
                String.class, workspaceId);
    }

    private JsonNode create(String token, String json) throws Exception {
        return body(mvc.perform(post(TEMPLATES).header("Authorization", "Bearer " + token).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isCreated()).andReturn());
    }

    private JsonNode list(String token) throws Exception {
        return body(mvc.perform(get(TEMPLATES).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
    }

    private static JsonNode only(JsonNode listed) {
        assertThat(listed.get("templates")).hasSize(1);
        return listed.get("templates").get(0);
    }

    private static List<String> namesOf(JsonNode listed) {
        return StreamSupport.stream(listed.get("templates").spliterator(), false)
                .map(template -> template.get("name").asText()).toList();
    }

    private static String template(String name, String subject, String body, boolean shared) {
        return "{\"name\":\"" + name + "\"," + (subject == null ? "" : "\"subject\":\"" + subject + "\",")
                + "\"body\":\"" + body.replace("\n", "\\n") + "\",\"shared\":" + shared + "}";
    }

    private static String edit(String name, long version) {
        return "{\"name\":\"" + name + "\",\"body\":\"Hello {{firstName}}\",\"version\":" + version + "}";
    }
}
