package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingRecallBotApi;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.core.storage.service.StorageDeletionSweeper;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** #918: erasing a person leaves nothing of them in any table, the bucket, the vendor cache or Recall. */
@IntegrationTest
class PersonErasureIntegrationTest extends FlowTestSupport {

    private static final String NOOR = """
            {"fullName":"Noor Haddad","linkedinUrl":"https://www.linkedin.com/in/noor-haddad/",
             "emails":[{"value":"noor@example.com"}]}""";

    /** Columns that keep an erased person's id by design: the append-only audit trail and credit ledger. */
    private static final List<String> KEPT_BY_DESIGN = List.of(
            "app_lm_audit_event.target_id", "app_lm_credit_entry.person_id", "app_lm_credit_hold.person_id");

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired DocumentStore store;
    @Autowired StorageDeletionSweeper sweeper;
    @Autowired RecordingRecallBotApi recall;

    private String admin;
    private String adminId;
    private String workspaceId;

    @BeforeEach
    void resetRecall() {
        recall.clear();
    }

    @Test
    @DisplayName("an erasure deletes every row, file, cache entry and Recall copy of the person, and suppresses them")
    void erasesEverything() throws Exception {
        firm("Erasing Firm");
        String cfo = mandate("Chief Financial Officer");
        String coo = mandate("Chief Operating Officer");
        JsonNode first = body(add(cfo, NOOR).andExpect(status().isCreated()).andReturn());
        JsonNode second = body(add(coo, NOOR).andExpect(status().isCreated()).andReturn());
        String personId = first.get("personId").asText();
        assertThat(second.get("personId").asText()).isEqualTo(personId);
        List<String> candidateIds = List.of(first.get("id").asText(), second.get("id").asText());

        mvc.perform(post("/api/v1/candidates/" + personId + "/notes")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"call\",\"body\":\"Open to a move in Q1\"}"))
                .andExpect(status().isCreated());
        mvc.perform(multipart("/api/v1/candidates/" + personId + "/documents")
                        .file(new MockMultipartFile("file", "Noor_Haddad_CV.pdf", "application/pdf",
                                "%PDF-1.7\nNoor".getBytes(StandardCharsets.US_ASCII)))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isCreated());
        String documentKey = db.queryForObject("select storage_key from app_lm_person_document_version"
                + " where person_id = ?::uuid", String.class, personId);
        String attachmentKey = seedOutreach(cfo, personId, candidateIds.getFirst());
        seedRecordingsTaskAndBell(cfo, personId);
        seedVendorCacheAndRun(cfo, candidateIds.getFirst());
        assertThat(referencesTo(personId, candidateIds)).as("the walk sees the seeded rows").isNotEmpty();

        JsonNode preview = body(mvc.perform(get("/api/v1/candidates/" + personId + "/erase-preview")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(preview.get("fullName").asText()).isEqualTo("Noor Haddad");
        assertThat(preview.get("positions").asLong()).isEqualTo(2);
        assertThat(preview.get("notes").asLong()).isEqualTo(1);
        assertThat(preview.get("documents").asLong()).isEqualTo(1);
        assertThat(preview.get("files").asLong()).isEqualTo(1);
        assertThat(preview.get("emails").asLong()).isEqualTo(2);
        assertThat(preview.get("recordings").asLong()).isEqualTo(2);
        assertThat(preview.get("tasks").asLong()).isEqualTo(1);
        assertThat(preview.get("blockedBy").isNull()).isTrue();

        assertThat(codeOf(erase(personId, "Noor Hadad", null).andExpect(status().isBadRequest()).andReturn()))
                .isEqualTo("VALIDATION_FAILED");
        erase(personId, "  noor   HADDAD ", null).andExpect(status().isNoContent());

        assertThat(referencesTo(personId, candidateIds)).isEmpty();
        assertThat(recall.removed()).containsExactly("bot-live");
        assertThat(recall.mediaDeleted()).containsExactlyInAnyOrder("bot-live", "bot-done");
        assertThat(db.queryForObject("select count(*) from app_lm_vendor_person where linkedin_slug = 'noor-haddad'",
                Long.class)).isZero();
        assertThat(db.queryForObject("select count(*) from app_lm_vendor_people_search where query_key = 'erasure-q'",
                Long.class)).isZero();
        assertThat(db.queryForObject("select details ->> 'names' from app_lm_notification where workspace_id = ?::uuid",
                String.class, workspaceId)).isEqualTo("[\"Omar Saleh\"]");
        assertThat(db.queryForObject("select outcomes::text from app_lm_executive_sourcing_run where project_id = ?::uuid",
                String.class, cfo)).doesNotContain("Noor").doesNotContain(candidateIds.getFirst());

        assertThat(db.queryForList("select storage_key from app_lm_storage_deletion", String.class))
                .contains(documentKey, attachmentKey);
        sweeper.sweepAt(Instant.now());
        assertThat(db.queryForObject("select count(*) from app_lm_storage_deletion where storage_key in (?, ?)",
                Long.class, documentKey, attachmentKey)).isZero();
        assertThatThrownBy(() -> store.open(documentKey).close()).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> store.open(attachmentKey).close()).isInstanceOf(IOException.class);

        String audited = db.queryForObject("select metadata::text from app_lm_audit_event"
                + " where event_type = 'PERSON_ERASED' and target_id = ?", String.class, personId);
        assertThat(audited).doesNotContainIgnoringCase("noor").contains("\"suppressed\": true");

        assertThat(codeOf(add(cfo, """
                {"fullName":"Noor H.","emails":[{"value":"NOOR@example.com"}]}""")
                .andExpect(status().isConflict()).andReturn())).isEqualTo("PERSON_SUPPRESSED");
    }

    @Test
    @DisplayName("an email being sent blocks the erasure, which changes nothing; without suppression they may return")
    void aSendInFlightBlocksAndSuppressionIsOptional() throws Exception {
        firm("Racing Firm");
        String cfo = mandate("Group Treasurer");
        JsonNode filed = body(add(cfo, NOOR).andExpect(status().isCreated()).andReturn());
        String personId = filed.get("personId").asText();
        db.update("""
                insert into app_lm_outreach_message (workspace_id, project_id, person_id, candidate_id, direction, kind,
                    status, sender_user_id, send_at, sending_since)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'OUTBOUND', 'MANUAL', 'SCHEDULED', ?::uuid, now(), now())""",
                workspaceId, cfo, personId, filed.get("id").asText(), adminId);

        assertThat(body(mvc.perform(get("/api/v1/candidates/" + personId + "/erase-preview")
                        .header("Authorization", "Bearer " + admin)).andReturn()).get("blockedBy").asText())
                .isEqualTo("PERSON_ERASE_SEND_IN_FLIGHT");
        assertThat(codeOf(erase(personId, "Noor Haddad", null).andExpect(status().isConflict()).andReturn()))
                .isEqualTo("PERSON_ERASE_SEND_IN_FLIGHT");
        assertThat(db.queryForObject("select count(*) from app_lm_person where id = ?::uuid", Long.class, personId))
                .isEqualTo(1);

        db.update("update app_lm_outreach_message set sending_since = null where person_id = ?::uuid", personId);
        erase(personId, "Noor Haddad", false).andExpect(status().isNoContent());
        assertThat(db.queryForObject("select count(*) from app_lm_workspace_suppression where workspace_id = ?::uuid",
                Long.class, workspaceId)).isZero();
        add(cfo, NOOR).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("erasing is an admin's alone, and only of the workspace's own people")
    void erasingIsAnAdminsOwn() throws Exception {
        firm("Guarded Firm");
        String cfo = mandate("Chief Risk Officer");
        String personId = body(add(cfo, NOOR).andExpect(status().isCreated()).andReturn()).get("personId").asText();
        String member = "eraser-member@" + domain;
        inviteAndAccept(admin, "Mira Member", member, "MEMBER");
        String memberToken = login(member);
        mvc.perform(get("/api/v1/candidates/" + personId + "/erase-preview")
                        .header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/candidates/" + personId + "/erase")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmName\":\"Noor Haddad\"}"))
                .andExpect(status().isForbidden());

        firm("Other Guarded Firm");
        erase(personId, "Noor Haddad", null).andExpect(status().isNotFound());
        assertThat(db.queryForObject("select count(*) from app_lm_person where id = ?::uuid", Long.class, personId))
                .isEqualTo(1);
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    /** A reply, and a consultant's email carrying a file in the bucket; answers the file's key. */
    private String seedOutreach(String projectId, String personId, String candidateId) throws IOException {
        db.update("""
                insert into app_lm_outreach_message (workspace_id, project_id, person_id, candidate_id, direction, kind,
                    status, sender_user_id)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'INBOUND', 'REPLY', 'RECEIVED', ?::uuid)""",
                workspaceId, projectId, personId, candidateId, adminId);
        UUID sent = db.queryForObject("""
                insert into app_lm_outreach_message (workspace_id, project_id, person_id, candidate_id, direction, kind,
                    status, sender_user_id, sent_at)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'OUTBOUND', 'MANUAL', 'SENT', ?::uuid, now())
                returning id""", UUID.class, workspaceId, projectId, personId, candidateId, adminId);
        String key = "ws/" + workspaceId + "/outreach/attachments/" + UUID.randomUUID();
        byte[] bytes = "%PDF-1.7\ncopy".getBytes(StandardCharsets.US_ASCII);
        store.put(key, new ByteArrayInputStream(bytes), bytes.length, "application/pdf");
        db.update("""
                insert into app_lm_outreach_message_attachment (message_id, workspace_id, position, file_name,
                    content_type, size_bytes, sha256, storage_key, source)
                values (?, ?::uuid, 0, 'Noor_Haddad_CV.pdf', 'application/pdf', ?, ?, ?, 'UPLOAD')""",
                sent, workspaceId, bytes.length, "0".repeat(64), key);
        return key;
    }

    private void seedRecordingsTaskAndBell(String projectId, String personId) {
        for (String[] bot : List.of(new String[] {"bot-live", "RECORDING"}, new String[] {"bot-done", "DONE"})) {
            db.update("""
                    insert into app_lm_meeting_recording (workspace_id, person_id, project_id, starts_at, ends_at,
                        join_url, requested_by, status, recall_bot_id)
                    values (?::uuid, ?::uuid, ?::uuid, now(), now() + interval '30 minutes',
                        'https://meet.example/' || ?, ?::uuid, ?, ?)""",
                    workspaceId, personId, projectId, bot[0], adminId, bot[1], bot[0]);
        }
        db.update("""
                insert into app_lm_task (workspace_id, project_id, person_id, title, kind, assignee_user_id, source)
                values (?::uuid, ?::uuid, ?::uuid, 'Call back', 'CALL', ?::uuid, 'MANUAL')""",
                workspaceId, projectId, personId, adminId);
        db.update("""
                insert into app_lm_notification (workspace_id, recipient_user_id, kind, group_key, details)
                values (?::uuid, ?::uuid, 'OUTREACH_REPLIED', 'replies:' || ?, '{"names":["Noor Haddad","Omar Saleh"]}')""",
                workspaceId, adminId, projectId);
    }

    private void seedVendorCacheAndRun(String projectId, String candidateId) {
        db.update("""
                insert into app_lm_vendor_person (linkedin_slug, provider, fetched_at, raw)
                values ('noor-haddad', 'contactout', now(), '{"name":"Noor Haddad"}')
                on conflict (linkedin_slug) do update set fetched_at = now()""");
        db.update("""
                insert into app_lm_vendor_people_search (query_key, company_slug, slugs, fetched_at)
                values ('erasure-q', 'aldar', array['someone-else','Noor-Haddad'], now())
                on conflict (query_key) do update set slugs = excluded.slugs""");
        db.update("""
                insert into app_lm_executive_sourcing_run (workspace_id, project_id, requested_by, status, companies,
                    outcomes)
                values (?::uuid, ?::uuid, ?::uuid, 'COMPLETED', '[]',
                    jsonb_build_array(jsonb_build_object('companyName', 'Aldar', 'outcome', 'FILED', 'picks',
                        jsonb_build_array(jsonb_build_object('name', 'Noor Haddad', 'candidateId', ?),
                                          jsonb_build_object('name', 'Omar Saleh', 'candidateId', null)))))""",
                workspaceId, projectId, adminId, candidateId);
    }

    /** Every uuid column of every table that still holds the person or one of their mappings, bar the kept ones. */
    private List<String> referencesTo(String personId, List<String> candidateIds) {
        List<String> ids = new ArrayList<>(candidateIds);
        ids.add(personId);
        List<Map<String, Object>> columns = db.queryForList("""
                select table_name, column_name from information_schema.columns
                where table_schema = 'public' and table_name like 'app\\_lm\\_%' and data_type = 'uuid'
                  and table_name in (select table_name from information_schema.tables
                                     where table_schema = 'public' and table_type = 'BASE TABLE')""");
        List<String> holding = new ArrayList<>();
        for (Map<String, Object> column : columns) {
            String qualified = column.get("table_name") + "." + column.get("column_name");
            if (KEPT_BY_DESIGN.contains(qualified)) {
                continue;
            }
            Long rows = db.queryForObject("select count(*) from " + column.get("table_name") + " where "
                    + column.get("column_name") + "::text = any(?::text[])", Long.class, (Object) ids.toArray(String[]::new));
            if (rows != null && rows > 0) {
                holding.add(qualified);
            }
        }
        return holding;
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

    private ResultActions add(String projectId, String json) throws Exception {
        return mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions erase(String personId, String confirmName, Boolean suppress) throws Exception {
        String suppressPart = suppress == null ? "" : ",\"suppress\":" + suppress;
        return mvc.perform(post("/api/v1/candidates/" + personId + "/erase")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmName\":\"" + confirmName + "\"" + suppressPart + "}"));
    }
}
