package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** #919: everything held on one person leaves as one zip, to an admin alone, audited by id and counts. */
@IntegrationTest
class PersonSubjectAccessIntegrationTest extends FlowTestSupport {

    private static final String NOOR = """
            {"fullName":"Noor Haddad","linkedinUrl":"https://www.linkedin.com/in/noor-haddad/",
             "emails":[{"value":"noor@example.com"}]}""";

    @Autowired @FixtureJdbc JdbcTemplate db;

    private String admin;
    private String adminId;
    private String workspaceId;

    @Test
    @DisplayName("the zip holds every section, the deleted note, the latest file, the AI logic and names, escaped")
    void exportsEverythingHeld() throws Exception {
        firm("Answering Firm");
        String cfo = mandate("Chief Financial Officer");
        String coo = mandate("Chief Operating Officer");
        String left = mandate("Chief Risk Officer");
        JsonNode first = body(add(cfo, NOOR).andExpect(status().isCreated()).andReturn());
        add(coo, NOOR).andExpect(status().isCreated());
        String personId = first.get("personId").asText();
        String candidateId = first.get("id").asText();

        mvc.perform(post("/api/v1/candidates/" + personId + "/notes")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"body\":\"Asked about <script>alert(1)</script> on the call\"}"))
                .andExpect(status().isCreated());
        db.update("""
                insert into app_lm_person_note (workspace_id, person_id, kind, body, author_user_id, deleted_at,
                    deleted_by)
                values (?::uuid, ?::uuid, 'GENERAL', 'Removed by mistake', ?::uuid, now(), ?::uuid)""",
                workspaceId, personId, adminId, adminId);
        db.update("""
                insert into app_lm_person_note (workspace_id, person_id, project_id, kind, body, author_user_id)
                values (?::uuid, ?::uuid, ?::uuid, 'GENERAL', 'Met while on the risk search', ?::uuid)""",
                workspaceId, personId, left, adminId);
        upload(personId, "%PDF-1.7\nfirst".getBytes(StandardCharsets.US_ASCII));
        upload(personId, "%PDF-1.7\nsecond".getBytes(StandardCharsets.US_ASCII));
        seedOutreachRecordingAndTask(cfo, personId, candidateId);
        db.update("""
                update app_lm_person set ai_inferred_fields = '["gender"]', gender = 'FEMALE',
                    ai_nationality_reading = '{"category":"Unknown","confidence":"low"}'
                where id = ?::uuid""", personId);
        db.update("""
                insert into app_lm_candidate_scorecard (workspace_id, project_id, candidate_id, status, scores, report,
                    updated_by, ai_drafted, ai_drafted_at)
                values (?::uuid, ?::uuid, ?::uuid, 'DRAFT', '[]', '{}', ?::uuid, '["report:summary"]', now())""",
                workspaceId, cfo, candidateId, adminId);

        MvcResult streaming = mvc.perform(get("/api/v1/candidates/" + personId + "/subject-access-export")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult download = mvc.perform(asyncDispatch(streaming)).andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/zip"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        assertThat(download.getResponse().getHeader("Content-Disposition"))
                .contains("uncava-subject-access-noor-haddad-");
        Map<String, byte[]> entries = unzip(download.getResponse().getContentAsByteArray());
        assertThat(entries).containsKeys("README.txt", "person.json", "person.html");
        List<String> documentFiles = entries.keySet().stream().filter(name -> name.startsWith("documents/")).toList();
        assertThat(documentFiles).hasSize(1);
        assertThat(new String(entries.get(documentFiles.getFirst()), StandardCharsets.US_ASCII)).endsWith("second");

        JsonNode person = json.readTree(entries.get("person.json"));
        assertThat(person.at("/export/format").asText()).isEqualTo("uncava-subject-access/1");
        assertThat(person.at("/export/heldBy").asText()).isEqualTo("Answering Firm");
        JsonNode data = person.get("data");
        assertThat(data.at("/person/0/full_name").asText()).isEqualTo("Noor Haddad");
        assertThat(data.at("/person/0/version").isMissingNode()).as("row versions are withheld").isTrue();
        assertThat(data.at("/contacts/0/value").asText()).isEqualTo("noor@example.com");
        assertThat(data.at("/contacts/0/source").asText()).isEqualTo("MANUAL");
        assertThat(data.at("/contacts/0/value_key").isMissingNode()).isTrue();
        assertThat(data.get("positions")).hasSize(2);
        assertThat(data.get("notes")).hasSize(3);
        assertThat(data.findValues("deleted_at").stream().filter(value -> !value.isNull())).hasSize(1);
        assertThat(data.get("documentVersions")).hasSize(2);
        assertThat(data.at("/documentVersions/0/storage_key").isMissingNode()).isTrue();
        assertThat(data.get("emails")).hasSize(2);
        assertThat(data.at("/emailAttachments/0/file_name").asText()).isEqualTo("Offer.pdf");
        assertThat(data.at("/emailAttachments/0/storage_key").isMissingNode()).isTrue();
        assertThat(data.at("/recordings/0/transcript/0/text").asText()).isEqualTo("I would move for the right role");
        assertThat(data.at("/recordings/0/recall_bot_id").isMissingNode()).isTrue();
        assertThat(data.at("/tasks/0/title").asText()).isEqualTo("Call back");
        assertThat(data.get("scorecards")).hasSize(1);
        assertThat(data.get("timeline").size()).isGreaterThan(0);
        assertThat(person.get("aiLogic").valueStream().map(use -> use.get("use").asText()))
                .contains("NATIONALITY", "BACKGROUND", "ASSESSMENT_DRAFT", "CALL_CHANGES");
        assertThat(person.at("/aiLogic/0/logic").asText()).isNotBlank();
        assertThat(person.at("/aboutThisData/automatedDecisions").asText()).contains("No decision about you");
        assertThat(person.at("/aboutThisData/recipients").toString()).contains("Chief Financial Officer Unit")
                .as("a position they are not on reads nothing of them").doesNotContain("Chief Risk Officer Unit");
        assertThat(person.at("/references/positions/" + left + "/title").asText()).isEqualTo("Chief Risk Officer");
        assertThat(person.at("/references/colleagues/" + adminId).asText()).isEqualTo("Alok Kumar");
        assertThat(person.at("/references/positions/" + cfo + "/title").asText()).isEqualTo("Chief Financial Officer");

        String html = new String(entries.get("person.html"), StandardCharsets.UTF_8);
        assertThat(html).contains("Personal data held on Noor Haddad").contains("Alok Kumar")
                .contains("&lt;script&gt;alert(1)&lt;/script&gt;").doesNotContain("<script>");
        assertThat(new String(entries.get("README.txt"), StandardCharsets.UTF_8)).contains("Before you send it");

        String audited = db.queryForObject("select metadata::text from app_lm_audit_event"
                + " where event_type = 'PERSON_SUBJECT_ACCESS_EXPORTED' and target_id = ?", String.class, personId);
        assertThat(audited).doesNotContainIgnoringCase("noor").contains("\"positions\": 2").contains("\"notes\": 3")
                .contains("\"messages\": 2");
    }

    @Test
    @DisplayName("exporting is an admin's alone, and only of the workspace's own people")
    void exportingIsAnAdminsOwn() throws Exception {
        firm("Guarding Firm");
        String cfo = mandate("Chief Risk Officer");
        String personId = body(add(cfo, NOOR).andExpect(status().isCreated()).andReturn()).get("personId").asText();
        String member = "sar-member@" + domain;
        inviteAndAccept(admin, "Mira Member", member, "MEMBER");
        mvc.perform(get("/api/v1/candidates/" + personId + "/subject-access-export")
                        .header("Authorization", "Bearer " + login(member)))
                .andExpect(status().isForbidden());

        firm("Other Guarding Firm");
        assertThat(codeOf(mvc.perform(get("/api/v1/candidates/" + personId + "/subject-access-export")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound()).andReturn())).isEqualTo("NOT_FOUND");
        assertThat(db.queryForObject("select count(*) from app_lm_audit_event"
                + " where event_type = 'PERSON_SUBJECT_ACCESS_EXPORTED' and target_id = ?", Long.class, personId))
                .isZero();
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private void seedOutreachRecordingAndTask(String projectId, String personId, String candidateId) {
        db.update("""
                insert into app_lm_outreach_message (workspace_id, project_id, person_id, candidate_id, direction, kind,
                    status, sender_user_id, body_text)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'INBOUND', 'REPLY', 'RECEIVED', ?::uuid, 'Happy to talk')""",
                workspaceId, projectId, personId, candidateId, adminId);
        String sent = db.queryForObject("""
                insert into app_lm_outreach_message (workspace_id, project_id, person_id, candidate_id, direction, kind,
                    status, sender_user_id, sent_at, subject, body_text)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'OUTBOUND', 'MANUAL', 'SENT', ?::uuid, now(), 'A role',
                    'Hi Noor')
                returning id::text""", String.class, workspaceId, projectId, personId, candidateId, adminId);
        db.update("""
                insert into app_lm_outreach_message_attachment (message_id, workspace_id, position, file_name,
                    content_type, size_bytes, sha256, storage_key, source)
                values (?::uuid, ?::uuid, 0, 'Offer.pdf', 'application/pdf', 10, ?, 'ws/x/outreach/attachments/k',
                    'UPLOAD')""", sent, workspaceId, "0".repeat(64));
        db.update("""
                insert into app_lm_meeting_recording (workspace_id, person_id, project_id, starts_at, ends_at,
                    join_url, requested_by, status, recall_bot_id, transcript, suggestions)
                values (?::uuid, ?::uuid, ?::uuid, now(), now() + interval '30 minutes', 'https://meet.example/sar',
                    ?::uuid, 'DONE', 'bot-sar', '[{"speaker":"Noor","text":"I would move for the right role"}]',
                    '{"notice":null}')""",
                workspaceId, personId, projectId, adminId);
        db.update("""
                insert into app_lm_task (workspace_id, project_id, person_id, title, kind, assignee_user_id, source)
                values (?::uuid, ?::uuid, ?::uuid, 'Call back', 'CALL', ?::uuid, 'MANUAL')""",
                workspaceId, projectId, personId, adminId);
    }

    private void upload(String personId, byte[] bytes) throws Exception {
        mvc.perform(multipart("/api/v1/candidates/" + personId + "/documents")
                        .file(new MockMultipartFile("file", "Noor_Haddad_CV.pdf", "application/pdf", bytes))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated());
    }

    private static Map<String, byte[]> unzip(byte[] archive) throws Exception {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private void firm(String name) throws Exception {
        String address = name.toLowerCase().replaceAll("[^a-z]", "") + "@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), name);
        admin = login(address);
        adminId = db.queryForObject("select id::text from app_lm_user where email = ?", String.class, address);
        workspaceId = db.queryForObject("select w.id::text from app_lm_workspace w where w.name = ?", String.class, name);
    }

    private String mandate(String positionTitle) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"%s Unit\"}".formatted(positionTitle)))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"%s\",\"positionTitle\":\"%s\"}".formatted(clientId, positionTitle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private ResultActions add(String projectId, String body) throws Exception {
        return mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }
}
