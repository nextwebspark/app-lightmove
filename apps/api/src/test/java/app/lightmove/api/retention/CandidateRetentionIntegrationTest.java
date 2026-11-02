package app.lightmove.api.retention;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.tenant.WorkspaceScope;
import app.lightmove.api.retention.service.CandidateRetentionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** #921: a workspace's candidate retention — the period, who is past it, and keeping or deleting them. */
@IntegrationTest
class CandidateRetentionIntegrationTest extends FlowTestSupport {

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired CandidateRetentionService retention;

    private String admin;
    private String adminId;
    private String workspaceId;

    @Test
    @DisplayName("the period is the deployment's until an admin chooses one, a year at least, or never")
    void periodIsChosenByAnAdmin() throws Exception {
        firm("Keeping Firm");
        JsonNode initial = body(period().andExpect(status().isOk()).andReturn());
        assertThat(initial.get("months").asInt()).isEqualTo(24);
        assertThat(initial.get("chosen").asBoolean()).isFalse();

        assertThat(codeOf(choose("18").andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        choose("12").andExpect(status().isOk());
        choose("12").andExpect(status().isOk());
        JsonNode never = body(choose("null").andExpect(status().isOk()).andReturn());
        assertThat(never.get("months").isNull()).isTrue();
        assertThat(never.get("chosen").asBoolean()).isTrue();
        assertThat(never.get("defaultMonths").asInt()).isEqualTo(24);

        assertThat(db.queryForList("select metadata ->> 'months' from app_lm_audit_event"
                + " where workspace_id = ?::uuid and event_type = 'WORKSPACE_UPDATED' and metadata ->> 'section' = 'retention'"
                + " order by id", String.class, workspaceId)).containsExactly("12", "never");
        JsonNode due = body(due().andExpect(status().isOk()).andReturn());
        assertThat(due.get("total").asLong()).isZero();
        assertThat(due.get("cutoff").isNull()).isTrue();

        String member = "keeper-member@" + domain;
        inviteAndAccept(admin, "Mira Member", member, "MEMBER");
        String memberToken = login(member);
        mvc.perform(get("/api/v1/workspace/retention").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/workspace/retention/due").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("due is untouched past the period, on no live position and not restricted; keep re-stamps, delete erases")
    void dueKeepAndDelete() throws Exception {
        firm("Reviewing Firm");
        String archived = mandate("Chief Financial Officer");
        String live = mandate("Chief Operating Officer");
        String dormant = person(archived, "Dina Dormant", "dina-dormant");
        String working = person(live, "Wael Working", "wael-working");
        String noted = person(archived, "Nadia Noted", "nadia-noted");
        String restricted = person(archived, "Rami Restricted", "rami-restricted");
        String kept = person(archived, "Karim Kept", "karim-kept");
        db.update("update app_lm_project set archived_at = now(), archived_by = ?::uuid where id = ?::uuid",
                adminId, archived);
        db.update("update app_lm_person set created_at = now() - interval '3 years' where workspace_id = ?::uuid",
                workspaceId);
        db.update("update app_lm_person_activity set occurred_at = now() - interval '3 years'"
                + " where workspace_id = ?::uuid", workspaceId);
        assertThat(db.queryForObject("select count(*) from app_lm_person where workspace_id = ?::uuid"
                + " and created_at < now() - interval '2 years'", Long.class, workspaceId))
                .as("the aged people are there to find").isEqualTo(5);
        mvc.perform(post("/api/v1/candidates/" + noted + "/notes")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"kind\":\"call\",\"body\":\"Still in touch\"}"))
                .andExpect(status().isCreated());
        db.update("update app_lm_person set processing_restricted = true, processing_restricted_reason = 'Request',"
                + " processing_restricted_at = now() where id = ?::uuid", restricted);
        db.update("update app_lm_person set retention_kept_at = now() - interval '1 month' where id = ?::uuid", kept);

        JsonNode due = body(due().andExpect(status().isOk()).andReturn());
        assertThat(due.get("months").asInt()).isEqualTo(24);
        assertThat(due.get("total").asLong()).isEqualTo(1);
        JsonNode row = due.get("people").get(0);
        assertThat(row.get("personId").asText()).isEqualTo(dormant);
        assertThat(row.get("fullName").asText()).isEqualTo("Dina Dormant");
        assertThat(row.get("positions").asInt()).isEqualTo(1);
        assertThat(row.get("employer").asText()).isEqualTo("Dormant Holdings");
        assertThat(row.get("lastActivityKind").asText()).isEqualTo("ADDED_TO_POOL");
        assertThat(WorkspaceScope.call(UUID.randomUUID(), () -> retention.due(UUID.fromString(workspaceId), 10, 0)
                .total())).as("read from another workspace, nobody is due").isZero();

        JsonNode keptNow = body(people("keep", dormant).andExpect(status().isOk()).andReturn());
        assertThat(keptNow.get("kept").asInt()).isEqualTo(1);
        assertThat(body(due().andReturn()).get("total").asLong()).isZero();
        db.update("update app_lm_person set retention_kept_at = now() - interval '25 months' where id = ?::uuid",
                dormant);
        assertThat(body(due().andReturn()).get("people").get(0).get("lastActivityKind").asText())
                .isEqualTo("RETENTION_KEPT");

        JsonNode erased = body(people("erase", dormant, working).andExpect(status().isOk()).andReturn());
        assertThat(erased.get("erased").asInt()).isEqualTo(1);
        assertThat(erased.get("noLongerDue").asInt()).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from app_lm_person where id = ?::uuid", Long.class, dormant))
                .isZero();
        assertThat(db.queryForObject("select count(*) from app_lm_person where id = ?::uuid", Long.class, working))
                .isEqualTo(1);
        String audited = db.queryForObject("select metadata::text from app_lm_audit_event"
                + " where event_type = 'PERSON_ERASED' and target_id = ?", String.class, dormant);
        assertThat(audited).doesNotContainIgnoringCase("dina").contains("\"reason\": \"retention\"")
                .contains("\"suppressed\": false");
        assertThat(db.queryForObject("select count(*) from app_lm_workspace_suppression where workspace_id = ?::uuid",
                Long.class, workspaceId)).isZero();
        assertThat(db.queryForObject("select count(*) from app_lm_audit_event where event_type = 'PERSON_RETENTION_KEPT'"
                + " and target_id = ?", Long.class, dormant)).isEqualTo(1);
    }

    @Test
    @DisplayName("someone still being emailed waits for the next review; deleting is an admin's alone")
    void aSendInFlightWaitsAndDeletingIsAnAdminsOwn() throws Exception {
        firm("Waiting Firm");
        String archived = mandate("Group Treasurer");
        String sending = person(archived, "Sami Sending", "sami-sending");
        String candidateId = db.queryForObject("select id::text from app_lm_project_candidate where person_id = ?::uuid",
                String.class, sending);
        db.update("""
                insert into app_lm_outreach_message (workspace_id, project_id, person_id, candidate_id, direction, kind,
                    status, sender_user_id, send_at, sending_since)
                values (?::uuid, ?::uuid, ?::uuid, ?::uuid, 'OUTBOUND', 'MANUAL', 'SCHEDULED', ?::uuid, now(), now())""",
                workspaceId, archived, sending, candidateId, adminId);
        db.update("update app_lm_project set archived_at = now(), archived_by = ?::uuid where id = ?::uuid",
                adminId, archived);
        db.update("update app_lm_person set created_at = now() - interval '3 years' where id = ?::uuid", sending);
        db.update("update app_lm_person_activity set occurred_at = now() - interval '3 years' where person_id = ?::uuid",
                sending);

        String member = "waiting-member@" + domain;
        inviteAndAccept(admin, "Mira Member", member, "MEMBER");
        mvc.perform(post("/api/v1/workspace/retention/due/erase")
                        .header("Authorization", "Bearer " + login(member))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"personIds\":[\"" + sending + "\"]}"))
                .andExpect(status().isForbidden());

        JsonNode erased = body(people("erase", sending).andExpect(status().isOk()).andReturn());
        assertThat(erased.get("sendInFlight").asInt()).isEqualTo(1);
        assertThat(erased.get("erased").asInt()).isZero();
        assertThat(db.queryForObject("select count(*) from app_lm_person where id = ?::uuid", Long.class, sending))
                .isEqualTo(1);
    }

    private String person(String projectId, String fullName, String slug) throws Exception {
        String employer = fullName.substring(fullName.indexOf(' ') + 1) + " Holdings";
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","linkedinUrl":"https://www.linkedin.com/in/%s/",
                                 "employerName":"%s"}""".formatted(fullName, slug, employer)))
                .andExpect(status().isCreated())
                .andReturn()).get("personId").asText();
    }

    private ResultActions period() throws Exception {
        return mvc.perform(get("/api/v1/workspace/retention").header("Authorization", "Bearer " + admin));
    }

    private ResultActions choose(String months) throws Exception {
        return mvc.perform(put("/api/v1/workspace/retention")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"months\":" + months + "}"));
    }

    private ResultActions due() throws Exception {
        return mvc.perform(get("/api/v1/workspace/retention/due").header("Authorization", "Bearer " + admin));
    }

    private ResultActions people(String action, String... personIds) throws Exception {
        return mvc.perform(post("/api/v1/workspace/retention/due/" + action)
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"personIds\":[" + String.join(",", List.of(personIds).stream()
                        .map(id -> "\"" + id + "\"").toList()) + "]}"));
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
