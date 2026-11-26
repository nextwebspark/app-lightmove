package app.lightmove.api.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.audit.constant.PersonErasureReason;
import app.lightmove.api.retention.service.NeverContactedErasure;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * #1490 (B2, E1): someone held without consent and never contacted is erased six months after they were added, or
 * after the last position they were on was archived — unless anything says they matter: a live position, a
 * restriction, do not contact, an admin's keep however old, a consent, a notice, an email either way, a meeting, a
 * call that reached them.
 */
@IntegrationTest
class NeverContactedErasureIntegrationTest extends FlowTestSupport {

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired NeverContactedErasure pass;

    private String admin;
    private String adminId;
    private String workspaceId;

    @Test
    @DisplayName("grey and never contacted for six months goes; anyone touched, kept, live or restricted stays")
    void onlyTheNeverContactedGo() throws Exception {
        firm("Quiet Firm");
        String archived = mandate("Chief Financial Officer");
        String live = mandate("Chief Operating Officer");
        String recent = mandate("Chief Risk Officer");
        String grey = person(archived, "Gala Grey", "gala-grey");
        String rangOut = person(archived, "Rafi Rangout", "rafi-rangout");
        String young = person(archived, "Yusuf Young", "yusuf-young");
        String onLive = person(live, "Lina Live", "lina-live");
        String onRecent = person(recent, "Rana Recent", "rana-recent");
        String restricted = person(archived, "Rami Restricted", "rami-restricted");
        String kept = person(archived, "Karim Kept", "karim-kept");
        String consented = person(archived, "Celia Consent", "celia-consent");
        String notified = person(archived, "Nora Notified", "nora-notified");
        String emailed = person(archived, "Emad Emailed", "emad-emailed");
        String called = person(archived, "Cyrus Called", "cyrus-called");
        String objected = person(archived, "Omar Objected", "omar-objected");
        db.update("update app_lm_project set archived_at = now() - interval '7 months', archived_by = ?::uuid"
                + " where id = ?::uuid", adminId, archived);
        db.update("update app_lm_project set archived_at = now() - interval '1 month', archived_by = ?::uuid"
                + " where id = ?::uuid", adminId, recent);
        db.update("update app_lm_person set created_at = now() - interval '7 months' where workspace_id = ?::uuid",
                workspaceId);
        db.update("update app_lm_person set created_at = now() - interval '5 months' where id = ?::uuid", young);
        db.update("update app_lm_person set processing_restricted = true, processing_restricted_reason = 'Request',"
                + " processing_restricted_at = now() where id = ?::uuid", restricted);
        db.update("update app_lm_person set retention_kept_at = now() - interval '2 years' where id = ?::uuid", kept);
        db.update("update app_lm_person set privacy_notified_at = now() - interval '6 months',"
                + " privacy_notice_channel = 'MARKED_BY_HAND' where id = ?::uuid", notified);
        db.update("update app_lm_person set do_not_contact = true, do_not_contact_reason = 'Asked us to stop',"
                + " do_not_contact_set_at = now() where id = ?::uuid", objected);
        db.update("""
                insert into app_lm_person_consent (workspace_id, person_id, purpose, status, answered_at, answered_via)
                values (?::uuid, ?::uuid, 'KEEP_ON_FILE', 'GIVEN', now() - interval '7 months', 'BY_HAND')""",
                workspaceId, consented);
        note(rangOut, "CALL", "NO_ANSWER");
        note(called, "CALL", "CONNECTED");
        String candidate = db.queryForObject("select id::text from app_lm_project_candidate where person_id = ?::uuid",
                String.class, emailed);
        db.update("""
                insert into app_lm_outreach_message (workspace_id, project_id, person_id, candidate_id, direction, kind,
                    status, sender_user_id, sent_at)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'OUTBOUND', 'MANUAL', 'SENT', ?::uuid,
                        now() - interval '7 months')""", workspaceId, archived, emailed, candidate, adminId);

        int erased = pass.eraseAt(Instant.now());

        assertThat(remaining(List.of(grey, rangOut))).as("grey and never reached").isEmpty();
        assertThat(remaining(List.of(young, onLive, onRecent, restricted, kept, consented, notified, emailed, called,
                objected))).hasSize(10);
        assertThat(erased).isEqualTo(2);
        assertThat(db.queryForList("select metadata ->> 'reason' from app_lm_audit_event"
                + " where event_type = 'PERSON_ERASED' and workspace_id = ?::uuid", String.class, workspaceId))
                .containsOnly(PersonErasureReason.NEVER_CONTACTED.value()).hasSize(2);
        assertThat(db.queryForObject("select count(*) from app_lm_audit_event where event_type = 'PERSON_ERASED'"
                + " and workspace_id = ?::uuid and actor_user_id is not null", Long.class, workspaceId)).isZero();
        assertThat(db.queryForObject("select count(*) from app_lm_workspace_suppression where workspace_id = ?::uuid",
                Long.class, workspaceId)).as("passing a period is no objection").isZero();
        assertThat(pass.eraseAt(Instant.now())).as("a second pass finds nobody new").isZero();
    }

    @Test
    @DisplayName("a deleted workspace is left to its purge")
    void aDeletedWorkspaceIsSkipped() throws Exception {
        firm("Deleted Firm");
        String archived = mandate("Group Treasurer");
        String grey = person(archived, "Dalia Deleted", "dalia-deleted");
        db.update("update app_lm_project set archived_at = now() - interval '7 months', archived_by = ?::uuid"
                + " where id = ?::uuid", adminId, archived);
        db.update("update app_lm_person set created_at = now() - interval '7 months' where id = ?::uuid", grey);
        db.update("update app_lm_workspace set status = 'DELETED', deleted_at = now(),"
                + " purge_after = now() + interval '30 days' where id = ?::uuid", workspaceId);

        pass.eraseAt(Instant.now());

        assertThat(remaining(List.of(grey))).hasSize(1);
    }

    private void note(String personId, String kind, String outcome) {
        db.update("""
                insert into app_lm_person_note (workspace_id, person_id, kind, body, author_user_id, outcome, created_at)
                values (?::uuid, ?::uuid, ?, '', ?::uuid, ?, now() - interval '7 months')""",
                workspaceId, personId, kind, adminId, outcome);
    }

    private List<String> remaining(List<String> personIds) {
        return db.queryForList("select id::text from app_lm_person where id::text = any (?)", String.class,
                (Object) personIds.toArray(String[]::new));
    }

    private String person(String projectId, String fullName, String slug) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","linkedinUrl":"https://www.linkedin.com/in/%s/"}"""
                                .formatted(fullName, slug)))
                .andExpect(status().isCreated())
                .andReturn()).get("personId").asText();
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
}
