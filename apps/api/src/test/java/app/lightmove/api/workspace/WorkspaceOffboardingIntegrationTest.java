package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingMailboxGateway;
import app.lightmove.api.RecordingPaymentGateway;
import app.lightmove.api.RecordingRecallBotApi;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import app.lightmove.api.core.security.token.Tokens;
import app.lightmove.api.core.storage.service.DocumentStore;
import app.lightmove.api.dataexport.service.WorkspaceExportExpiry;
import app.lightmove.api.workspace.service.WorkspacePurge;
import app.lightmove.api.workspace.service.WorkspacePurgeTables;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** #926: deleting a workspace ends access at once; after the grace period the purge leaves a tombstone and a certificate. */
@IntegrationTest
class WorkspaceOffboardingIntegrationTest extends FlowTestSupport {

    private static final Duration PAST_GRACE = Duration.ofDays(31);

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired DocumentStore store;
    @Autowired WorkspacePurge purge;
    @Autowired WorkspaceExportExpiry exportExpiry;
    @Autowired RecordingRecallBotApi recall;
    @Autowired RecordingMailboxGateway mailboxes;
    @Autowired RecordingPaymentGateway stripe;

    @BeforeEach
    void resetDoubles() {
        recall.clear();
        mailboxes.clear();
    }

    @Test
    @DisplayName("deletion stops sending, lets mailboxes and bots go and cancels Stripe; the purge leaves nothing else")
    void deletionThenPurge() throws Exception {
        Firm doomed = firm("Purged Firm");
        Seeded seeded = seed(doomed);
        Firm neighbour = firm("Neighbour Firm");
        Seeded kept = seed(neighbour);
        String subscription = "sub_" + UUID.randomUUID();
        db.update("""
                insert into app_lm_workspace_subscription (workspace_id, plan_code, billing_interval, seats, status,
                    stripe_subscription_id)
                values (?::uuid, 'PRO', 'MONTHLY', 1, 'ACTIVE', ?)""", doomed.workspaceId(), subscription);
        UUID scheduled = db.queryForObject("""
                insert into app_lm_outreach_message (workspace_id, project_id, person_id, candidate_id, direction, kind,
                    status, sender_user_id, send_at)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'OUTBOUND', 'MANUAL', 'SCHEDULED', ?::uuid,
                    now() + interval '1 day')
                returning id""", UUID.class, doomed.workspaceId(), seeded.projectId(), seeded.personId(),
                seeded.candidateId(), doomed.adminId());
        String grant = "nylas-" + UUID.randomUUID();
        db.update("""
                insert into app_lm_mailbox_connection (workspace_id, user_id, provider, address, grant_id, status,
                    daily_cap, connected_at, gateway)
                values (?::uuid, ?::uuid, 'google', 'alok@mailbox.example', ?, 'ACTIVE', 50, now(), 'NYLAS')""",
                doomed.workspaceId(), doomed.adminId(), grant);

        Map<String, Long> before = WorkspacePurgeTablesView.rows(db, doomed.workspaceId());
        assertThat(before).as("the walk sees the seeded rows")
                .containsKeys("app_lm_person", "app_lm_project", "app_lm_person_note", "app_lm_meeting_recording");
        Map<String, Long> neighbourBefore = WorkspacePurgeTablesView.rows(db, neighbour.workspaceId());
        List<String> doomedIds = List.of(seeded.personId(), seeded.projectId(), seeded.candidateId());

        assertThat(codeOf(deleteWorkspace(doomed, "Purged Firn").andReturn())).isEqualTo("WORKSPACE_NAME_MISMATCH");
        deleteWorkspace(doomed, "purged firm").andExpect(status().isNoContent());

        Map<String, Object> deleted = db.queryForMap("""
                select status, deleted_at, purge_after, purge_notice_email from app_lm_workspace where id = ?::uuid""",
                doomed.workspaceId());
        assertThat(deleted.get("status")).isEqualTo("DELETED");
        assertThat(Duration.between(((Timestamp) deleted.get("deleted_at")).toInstant(),
                ((Timestamp) deleted.get("purge_after")).toInstant())).isEqualTo(Duration.ofDays(30));
        assertThat(deleted.get("purge_notice_email")).isEqualTo(doomed.email());
        assertThat(db.queryForObject("select status from app_lm_outreach_message where id = ?", String.class,
                scheduled)).isEqualTo("CANCELLED");
        assertThat(db.queryForObject("select count(*) from app_lm_mailbox_connection where workspace_id = ?::uuid",
                Long.class, doomed.workspaceId())).isZero();
        assertThat(mailboxes.revoked()).contains(grant);
        assertThat(recall.removed()).containsExactly(botOf("bot-live", doomed));
        assertThat(stripe.isCancellingAtPeriodEnd(subscription)).isTrue();
        assertThat(db.queryForObject("""
                select bool_and(status = 'REMOVED' and ended_with_workspace) from app_lm_workspace_member
                where workspace_id = ?::uuid""", Boolean.class, doomed.workspaceId())).isTrue();

        purge.purgeDueAt(Instant.now());
        assertThat(db.queryForObject("select purged_at from app_lm_workspace where id = ?::uuid", Instant.class,
                doomed.workspaceId())).as("not before the grace period is over").isNull();

        recall.clear();
        email.clear();
        assertThat(purge.purgeDueAt(Instant.now().plus(PAST_GRACE))).isPositive();

        assertThat(WorkspacePurgeTablesView.rows(db, doomed.workspaceId())).isEmpty();
        assertThat(referencesTo(doomedIds)).isEmpty();
        assertThat(recall.mediaDeleted()).contains(botOf("bot-done", doomed))
                .doesNotContain(botOf("bot-done", neighbour));
        assertThatThrownBy(() -> store.open(seeded.documentKey()).close()).isInstanceOf(IOException.class);
        assertThatThrownBy(() -> store.open(seeded.exportKey()).close()).isInstanceOf(IOException.class);

        Map<String, Object> tombstone = db.queryForMap("""
                select name, slug, email_domain, apollo_account_id, purge_notice_email, purge_notice_name, purged_at
                from app_lm_workspace where id = ?::uuid""", doomed.workspaceId());
        assertThat(tombstone.get("name")).isEqualTo("Deleted workspace");
        assertThat(tombstone.get("slug")).isEqualTo("deleted-" + doomed.workspaceId());
        assertThat(tombstone.get("email_domain")).isEqualTo("deleted.invalid");
        assertThat(tombstone.get("purge_notice_email")).isNull();
        assertThat(tombstone.get("purge_notice_name")).as("cleared once the certificate's email went").isNull();
        assertThat(tombstone.get("purged_at")).isNotNull();

        Map<String, Object> certificate = db.queryForMap("""
                select id, row_counts ->> 'app_lm_person' as people, row_counts ->> 'app_lm_project' as projects,
                       object_count, body, body_sha256
                from app_lm_workspace_purge_certificate where workspace_id = ?::uuid""", doomed.workspaceId());
        assertThat(certificate.get("people")).isEqualTo(String.valueOf(before.get("app_lm_person")));
        assertThat(certificate.get("projects")).isEqualTo(String.valueOf(before.get("app_lm_project")));
        assertThat((Long) certificate.get("object_count")).isGreaterThanOrEqualTo(2);
        assertThat(certificate.get("body_sha256")).isEqualTo(Tokens.hash((String) certificate.get("body")));
        assertThat((String) certificate.get("body")).doesNotContain("Purged Firm").doesNotContain("Noor");
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event where event_type = 'WORKSPACE_PURGED'
                  and workspace_id = ?::uuid and target_id = ?""", Long.class, doomed.workspaceId(),
                certificate.get("id").toString())).isEqualTo(1);
        assertThat(email.subjectsFor(doomed.email())).contains("Purged Firm has been permanently deleted");
        assertThat(db.queryForObject("select count(*) from app_lm_workspace_subscription where workspace_id = ?::uuid",
                Long.class, doomed.workspaceId())).as("billing records are kept by id").isEqualTo(1);

        assertThat(WorkspacePurgeTablesView.rows(db, neighbour.workspaceId())).isEqualTo(neighbourBefore);
        store.open(kept.documentKey()).close();
        store.open(kept.exportKey()).close();

        Object purgedAt = tombstone.get("purged_at");
        purge.purgeDueAt(Instant.now().plus(PAST_GRACE));
        assertThat(db.queryForObject("select purged_at from app_lm_workspace where id = ?::uuid", Object.class,
                doomed.workspaceId())).as("purged once").isEqualTo(purgedAt);
    }

    @Test
    @DisplayName("a platform admin restores a deleted workspace and its members within the grace period, never after")
    void restoreWithinGrace() throws Exception {
        Firm firm = firm("Restored Firm");
        String sara = "restored-sara@" + domain;
        inviteAndAccept(firm.token(), "Sara Saleh", sara, "MEMBER");
        String removedEarlier = "restored-omar@" + domain;
        inviteAndAccept(firm.token(), "Omar Saleh", removedEarlier, "MEMBER");
        mvc.perform(delete("/api/v1/members/" + memberIdOf(firm.token(), removedEarlier))
                        .header("Authorization", "Bearer " + firm.token()))
                .andExpect(status().isNoContent());
        deleteWorkspace(firm, "Restored Firm").andExpect(status().isNoContent());

        String platform = superAdmin();
        mvc.perform(post("/api/v1/platform/workspaces/" + firm.workspaceId() + "/restore")
                        .header("Authorization", "Bearer " + login(sara)))
                .andExpect(status().isNotFound());
        JsonNode restored = body(mvc.perform(post("/api/v1/platform/workspaces/" + firm.workspaceId() + "/restore")
                        .header("Authorization", "Bearer " + platform))
                .andExpect(status().isOk()).andReturn());
        assertThat(restored.get("membersRestored").asInt()).isEqualTo(2);
        assertThat(db.queryForList("""
                select u.email from app_lm_workspace_member m join app_lm_user u on u.id = m.user_id
                where m.workspace_id = ?::uuid and m.status = 'ACTIVE' order by u.email""", String.class,
                firm.workspaceId())).containsExactlyInAnyOrder(firm.email(), sara);
        mvc.perform(get("/api/v1/workspace").header("Authorization", "Bearer " + login(firm.email())))
                .andExpect(status().isOk());

        deleteWorkspace(firm, "Restored Firm").andExpect(status().isNoContent());
        db.update("update app_lm_workspace set purge_started_at = now() where id = ?::uuid", firm.workspaceId());
        assertThat(codeOf(mvc.perform(post("/api/v1/platform/workspaces/" + firm.workspaceId() + "/restore")
                        .header("Authorization", "Bearer " + platform))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("WORKSPACE_NOT_RESTORABLE");
    }

    @Test
    @DisplayName("an admin's export carries positions, people, notes, documents and the audit log, then expires")
    void exportWholeWorkspace() throws Exception {
        Firm firm = firm("Exported Firm");
        seed(firm);
        String member = "exported-member@" + domain;
        inviteAndAccept(firm.token(), "Mira Member", member, "MEMBER");
        mvc.perform(post("/api/v1/workspace/exports").header("Authorization", "Bearer " + login(member)))
                .andExpect(status().isForbidden());

        mvc.perform(get("/api/v1/workspace/exports/latest").header("Authorization", "Bearer " + firm.token()))
                .andExpect(status().isNoContent());
        JsonNode started = body(mvc.perform(post("/api/v1/workspace/exports")
                        .header("Authorization", "Bearer " + firm.token()))
                .andExpect(status().isAccepted()).andReturn());
        JsonNode latest = body(mvc.perform(get("/api/v1/workspace/exports/latest")
                        .header("Authorization", "Bearer " + firm.token()))
                .andExpect(status().isOk()).andReturn());
        assertThat(latest.get("id").asText()).isEqualTo(started.get("id").asText());
        assertThat(latest.get("status").asText()).isEqualTo("READY");
        assertThat(latest.get("fileName").asText()).startsWith("exported-firm-export-").endsWith(".zip");
        assertThat(latest.get("people").asInt()).isEqualTo(1);
        assertThat(latest.get("documents").asInt()).isEqualTo(1);
        assertThat(latest.get("requestedByName").asText()).isEqualTo("Alok Kumar");
        assertThat(email.subjectsFor(firm.email())).contains("Your Uncava export is ready");

        MvcResult streaming = mvc.perform(get("/api/v1/workspace/exports/" + latest.get("id").asText() + "/download")
                        .header("Authorization", "Bearer " + firm.token()))
                .andExpect(request().asyncStarted()).andReturn();
        MvcResult download = mvc.perform(asyncDispatch(streaming)).andExpect(status().isOk()).andReturn();
        Map<String, String> entries = unzip(download.getResponse().getContentAsByteArray());
        assertThat(entries).containsKeys("README.txt", "positions.csv", "candidates.csv", "audit-log.csv");
        assertThat(entries.get("candidates.csv")).contains("Noor Haddad");
        assertThat(entries.keySet()).anyMatch(name -> name.matches("positions/001-chief-financial-officer/brief.json"));
        assertThat(entries.keySet()).anyMatch(name -> name.endsWith("/companies-in-universe.csv"));
        String notes = entries.entrySet().stream().filter(entry -> entry.getKey().endsWith("/notes.md"))
                .map(Map.Entry::getValue).findFirst().orElseThrow();
        assertThat(notes).contains("# Noor Haddad").contains("Open to a move in Q1");
        assertThat(entries.keySet()).anyMatch(name -> name.matches("people/noor-haddad-[0-9a-f]{8}/documents/.*"
                + "Noor_Haddad_CV\\.pdf"));
        assertThat(entries.get("audit-log.csv")).contains("Started an export of the whole workspace");
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event where event_type = 'WORKSPACE_EXPORT_DOWNLOADED'
                  and workspace_id = ?::uuid""", Long.class, firm.workspaceId())).isEqualTo(1);

        db.update("""
                insert into app_lm_workspace_export (workspace_id, requested_by, status, file_name, storage_key)
                values (?::uuid, ?::uuid, 'RUNNING', 'other.zip', ?)""", firm.workspaceId(), firm.adminId(),
                "ws/" + firm.workspaceId() + "/exports/running.zip");
        assertThat(codeOf(mvc.perform(post("/api/v1/workspace/exports")
                        .header("Authorization", "Bearer " + firm.token()))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("WORKSPACE_EXPORT_RUNNING");
        db.update("delete from app_lm_workspace_export where file_name = 'other.zip'");

        String readyKey = db.queryForObject("select storage_key from app_lm_workspace_export where id = ?::uuid",
                String.class, latest.get("id").asText());
        exportExpiry.closeDueAt(Instant.now().plus(Duration.ofDays(8)));
        assertThat(db.queryForObject("select status from app_lm_workspace_export where id = ?::uuid", String.class,
                latest.get("id").asText())).isEqualTo("EXPIRED");
        assertThat(db.queryForObject("select count(*) from app_lm_storage_deletion where storage_key = ?",
                Long.class, readyKey)).isEqualTo(1);
        assertThat(codeOf(mvc.perform(get("/api/v1/workspace/exports/" + latest.get("id").asText() + "/download")
                        .header("Authorization", "Bearer " + firm.token()))
                .andExpect(status().isNotFound()).andReturn())).isEqualTo("WORKSPACE_EXPORT_UNAVAILABLE");
    }

    @Test
    @DisplayName("a grant whose provider cannot be reached keeps its row and holds the purge back until it is revoked")
    void unrevokedGrantHoldsThePurge() throws Exception {
        Firm firm = firm("Unreachable Firm");
        String grant = connectMailbox(firm);
        mailboxes.failRevokesWith(new VendorException(VendorCall.of("nylas", "revoke"), VendorFailureKind.UNAVAILABLE,
                null));
        deleteWorkspace(firm, "Unreachable Firm").andExpect(status().isNoContent());

        assertThat(mailboxStatusOf(firm)).as("kept, and never read or sent through").isEqualTo("ERROR");

        Instant firstAttempt = Instant.now().plus(PAST_GRACE);
        purge.purgeDueAt(firstAttempt);
        assertThat(purgedAt(firm)).as("held back while the grant is unrevoked").isNull();
        assertThat(mailboxStatusOf(firm)).isEqualTo("ERROR");

        mailboxes.failRevokesWith(null);
        purge.purgeDueAt(firstAttempt.plus(Duration.ofHours(2)));
        assertThat(mailboxes.revoked()).contains(grant);
        assertThat(purgedAt(firm)).isNotNull();
    }

    @Test
    @DisplayName("a provider still unreachable a week after the purge began no longer holds it back")
    void purgeStopsWaitingOnAProvider() throws Exception {
        Firm firm = firm("Gone Dark Firm");
        String grant = connectMailbox(firm);
        mailboxes.failRevokesWith(new VendorException(VendorCall.of("nylas", "revoke"), VendorFailureKind.TIMEOUT,
                null));
        deleteWorkspace(firm, "Gone Dark Firm").andExpect(status().isNoContent());

        Instant firstAttempt = Instant.now().plus(PAST_GRACE);
        purge.purgeDueAt(firstAttempt);
        assertThat(purgedAt(firm)).isNull();

        purge.purgeDueAt(firstAttempt.plus(WorkspacePurge.RELEASE_PATIENCE).plus(Duration.ofHours(1)));
        assertThat(purgedAt(firm)).isNotNull();
        assertThat(mailboxes.revoked()).doesNotContain(grant);
        assertThat(WorkspacePurgeTablesView.rows(db, firm.workspaceId())).isEmpty();
    }

    @Test
    @DisplayName("a certificate email the mail service refused stays owed, address and name, until it is accepted")
    void refusedCertificateEmailIsSentAgain() throws Exception {
        Firm firm = firm("Refused Notice Firm");
        deleteWorkspace(firm, "Refused Notice Firm").andExpect(status().isNoContent());
        email.refuseDeliveryTo(firm.email());

        Instant purgedOn = Instant.now().plus(PAST_GRACE);
        assertThat(purge.purgeDueAt(purgedOn)).isPositive();
        Map<String, Object> owed = db.queryForMap("""
                select name, purge_notice_email, purge_notice_name from app_lm_workspace where id = ?::uuid""",
                firm.workspaceId());
        assertThat(owed.get("name")).isEqualTo("Deleted workspace");
        assertThat(owed.get("purge_notice_email")).isEqualTo(firm.email());
        assertThat(owed.get("purge_notice_name")).isEqualTo("Refused Notice Firm");

        email.clear();
        purge.purgeDueAt(purgedOn.plus(Duration.ofMinutes(30)));
        assertThat(email.subjectsFor(firm.email())).as("not before the lease runs out").isEmpty();

        purge.purgeDueAt(purgedOn.plus(Duration.ofHours(2)));
        assertThat(email.subjectsFor(firm.email())).containsExactly("Refused Notice Firm has been permanently deleted");
        Map<String, Object> sent = db.queryForMap("""
                select purge_notice_email, purge_notice_name from app_lm_workspace where id = ?::uuid""",
                firm.workspaceId());
        assertThat(sent.get("purge_notice_email")).isNull();
        assertThat(sent.get("purge_notice_name")).isNull();

        purge.purgeDueAt(purgedOn.plus(Duration.ofHours(4)));
        assertThat(email.subjectsFor(firm.email())).as("sent once").hasSize(1);
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private String connectMailbox(Firm firm) {
        String grant = "nylas-" + UUID.randomUUID();
        db.update("""
                insert into app_lm_mailbox_connection (workspace_id, user_id, provider, address, grant_id, status,
                    daily_cap, connected_at, gateway)
                values (?::uuid, ?::uuid, 'google', ?, ?, 'ACTIVE', 50, now(), 'NYLAS')""",
                firm.workspaceId(), firm.adminId(), "mailbox-" + UUID.randomUUID() + "@mailbox.example", grant);
        return grant;
    }

    private String mailboxStatusOf(Firm firm) {
        return db.queryForObject("select status from app_lm_mailbox_connection where workspace_id = ?::uuid",
                String.class, firm.workspaceId());
    }

    private Object purgedAt(Firm firm) {
        return db.queryForObject("select purged_at from app_lm_workspace where id = ?::uuid", Object.class,
                firm.workspaceId());
    }

    private record Firm(String token, String email, String adminId, String workspaceId) {}

    private record Seeded(String projectId, String personId, String candidateId, String documentKey,
                          String exportKey) {}

    private Firm firm(String name) throws Exception {
        String address = name.toLowerCase().replaceAll("[^a-z]", "") + "@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), name);
        String token = login(address);
        String adminId = db.queryForObject("select id::text from app_lm_user where email = ?", String.class, address);
        String workspaceId = db.queryForObject("""
                select m.workspace_id::text from app_lm_workspace_member m
                join app_lm_workspace w on w.id = m.workspace_id
                where m.user_id = ?::uuid and w.name = ?""", String.class, adminId, name);
        return new Firm(token, address, adminId, workspaceId);
    }

    /** A position with one executive, their note and CV, a call recorded and one still to come, a task and a file. */
    private Seeded seed(Firm firm) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + firm.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"Finance Unit\"}"))
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + firm.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"%s\",\"positionTitle\":\"Chief Financial Officer\"}"
                                .formatted(clientId)))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        JsonNode filed = body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + firm.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Noor Haddad","linkedinUrl":"https://www.linkedin.com/in/noor-haddad/",
                                 "emails":[{"value":"noor@example.com"}]}"""))
                .andExpect(status().isCreated()).andReturn());
        String personId = filed.get("personId").asText();
        mvc.perform(post("/api/v1/candidates/" + personId + "/notes")
                        .header("Authorization", "Bearer " + firm.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"call\",\"body\":\"Open to a move in Q1\"}"))
                .andExpect(status().isCreated());
        mvc.perform(multipart("/api/v1/candidates/" + personId + "/documents")
                        .file(new MockMultipartFile("file", "Noor_Haddad_CV.pdf", "application/pdf",
                                "%PDF-1.7\nNoor".getBytes(StandardCharsets.US_ASCII)))
                        .header("Authorization", "Bearer " + firm.token()))
                .andExpect(status().isCreated());
        String documentKey = db.queryForObject("select storage_key from app_lm_person_document_version"
                + " where person_id = ?::uuid", String.class, personId);
        for (String[] bot : List.of(new String[] {"bot-live", "SCHEDULED"}, new String[] {"bot-done", "DONE"})) {
            db.update("""
                    insert into app_lm_meeting_recording (workspace_id, person_id, project_id, starts_at, ends_at,
                        join_url, requested_by, status, recall_bot_id)
                    values (?::uuid, ?::uuid, ?::uuid, now() + interval '1 day', now() + interval '1 day 30 minutes',
                        'https://meet.example/' || ?, ?::uuid, ?, ?)""",
                    firm.workspaceId(), personId, projectId, botOf(bot[0], firm), firm.adminId(), bot[1],
                    botOf(bot[0], firm));
        }
        db.update("""
                insert into app_lm_task (workspace_id, project_id, person_id, title, kind, assignee_user_id, source)
                values (?::uuid, ?::uuid, ?::uuid, 'Call back', 'CALL', ?::uuid, 'MANUAL')""",
                firm.workspaceId(), projectId, personId, firm.adminId());
        String exportKey = "ws/" + firm.workspaceId() + "/exports/" + UUID.randomUUID() + ".zip";
        byte[] zip = "PK".getBytes(StandardCharsets.US_ASCII);
        store.put(exportKey, new ByteArrayInputStream(zip), zip.length, "application/zip");
        return new Seeded(projectId, personId, filed.get("id").asText(), documentKey, exportKey);
    }

    /** Recall's bot ids are unique, and each firm's must be told apart in what the double records. */
    private static String botOf(String bot, Firm firm) {
        return bot + "-" + firm.workspaceId();
    }

    private ResultActions deleteWorkspace(Firm firm, String confirmName)
            throws Exception {
        return mvc.perform(delete("/api/v1/workspace")
                .header("Authorization", "Bearer " + firm.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"confirmName\":\"" + confirmName + "\"}"));
    }

    private String superAdmin() throws Exception {
        String address = "offboarding-platform@" + domain;
        createWorkspace(verifiedUser("Dana Aboud", address), "Offboarding Platform Firm");
        db.update("""
                insert into app_lm_user_platform_role (user_id, role_id)
                select u.id, r.id from app_lm_user u, app_lm_role r
                where u.email = ? and r.scope = 'PLATFORM' and r.name = 'SUPER_ADMIN'""", address);
        return login(address);
    }

    /** Every uuid column of every table still holding one of the ids, bar the audit trail and the credit ledger. */
    private List<String> referencesTo(List<String> ids) {
        List<Map<String, Object>> columns = db.queryForList("""
                select table_name, column_name from information_schema.columns
                where table_schema = 'public' and table_name like 'app\\_lm\\_%' and data_type = 'uuid'
                  and table_name in (select table_name from information_schema.tables
                                     where table_schema = 'public' and table_type = 'BASE TABLE')""");
        List<String> holding = new ArrayList<>();
        for (Map<String, Object> column : columns) {
            String table = (String) column.get("table_name");
            if (WorkspacePurgeTables.RETAINED.contains(table)) {
                continue;
            }
            Long rows = db.queryForObject("select count(*) from " + table + " where " + column.get("column_name")
                    + "::text = any(?::text[])", Long.class, (Object) ids.toArray(String[]::new));
            if (rows != null && rows > 0) {
                holding.add(table + "." + column.get("column_name"));
            }
        }
        return holding;
    }

    private static Map<String, String> unzip(byte[] archive) throws IOException {
        Map<String, String> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(archive), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                entries.put(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8));
            }
        }
        return entries;
    }

    /** The superuser's count of a workspace's rows in each table the purge empties, the ones holding any. */
    private static final class WorkspacePurgeTablesView {

        static Map<String, Long> rows(JdbcTemplate db, String workspaceId) {
            Map<String, Long> counts = new HashMap<>();
            for (String table : db.queryForList("""
                    select c.relname from pg_class c
                    where c.relkind = 'r' and c.relnamespace = 'public'::regnamespace and c.relname like 'app\\_lm\\_%'
                      and exists (select 1 from pg_attribute a where a.attrelid = c.oid and a.attname = 'workspace_id'
                                  and not a.attisdropped)""", String.class)) {
                if (WorkspacePurgeTables.RETAINED.contains(table)) {
                    continue;
                }
                Long rows = db.queryForObject("select count(*) from " + table + " where workspace_id = ?::uuid",
                        Long.class, workspaceId);
                if (rows != null && rows > 0) {
                    counts.put(table, rows);
                }
            }
            return counts;
        }
    }
}
