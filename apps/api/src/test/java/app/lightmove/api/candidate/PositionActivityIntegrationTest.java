package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * The executive drawer's Activity read on a position: that position's own history and the changes made to the person,
 * never another position's work, which stays in the database rather than being hidden on screen. Profile saves record
 * each field old and new; outreach lines name the email and the automation behind them.
 */
@IntegrationTest
class PositionActivityIntegrationTest extends FlowTestSupport {

    private static final String FATIMA = """
            {"fullName":"Fatima Al Mazrouei","linkedinUrl":"https://www.linkedin.com/in/fatima-activity"}""";

    @Autowired JdbcTemplate db;
    @Autowired CandidateOutreachService outreach;
    @Autowired TransactionTemplate transactions;

    private String admin;

    @Test
    @DisplayName("another position's notes, status moves and filing never leave the server for this position")
    void anotherPositionsWorkNeverReachesThisOne() throws Exception {
        firm("Boundary Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        String onCfo = add(cfo, FATIMA).get("id").asText();
        String onCredit = add(credit, FATIMA).get("id").asText();

        setStatus(credit, onCredit, "contacted");
        note(credit, onCredit, "Credit-only read: strong on treasury.", false);
        setStatus(cfo, onCfo, "engaged");

        String raw = timelineText(cfo, onCfo, null);
        assertThat(raw).doesNotContain("Head of Credit Risk").doesNotContain(credit)
                .doesNotContain("Credit-only read");
        JsonNode entries = json.readTree(raw).get("entries");
        assertThat(kindsOf(entries)).containsExactly("STATUS_CHANGED", "ADDED_TO_POOL");
        assertThat(entries.get(0).get("details").get("to").asText()).isEqualTo("engaged");

        JsonNode fromCredit = timeline(credit, onCredit, null).get("entries");
        assertThat(kindsOf(fromCredit)).containsExactly("NOTE_ADDED", "STATUS_CHANGED", "MAPPED");
        assertThat(fromCredit.toString()).doesNotContain("engaged");
    }

    @Test
    @DisplayName("a change to the person made on another position shows here without naming where it was made")
    void personLevelChangesShowEverywhereUnnamed() throws Exception {
        firm("Person Level Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        JsonNode filed = add(cfo, FATIMA);
        String onCfo = filed.get("id").asText();
        String onCredit = add(credit, FATIMA).get("id").asText();

        save(credit, onCredit, """
                {"fullName":"Fatima Al Mazrouei","linkedinUrl":"https://www.linkedin.com/in/fatima-activity",
                 "title":"Group Finance Director"}""");
        note(credit, onCredit, "Prefers a listed company.", true);
        line(filed.get("personId").asText(), credit, "TAGGED", """
                {"tagId":"%s","tag":"Referral"}""".formatted(UUID.randomUUID()));
        line(filed.get("personId").asText(), credit, "DOCUMENT_ADDED", """
                {"documentId":"%s","category":"CV"}""".formatted(UUID.randomUUID()));
        line(filed.get("personId").asText(), credit, "UPDATED_FROM_CALL", """
                {"fields":"noticePeriod","call":"Credit intro call"}""");

        String raw = timelineText(cfo, onCfo, null);
        assertThat(raw).doesNotContain("Head of Credit Risk").doesNotContain("Credit intro call");
        JsonNode entries = json.readTree(raw).get("entries");
        assertThat(kindsOf(entries)).containsExactly("DOCUMENT_ADDED", "TAGGED", "NOTE_ADDED", "PROFILE_EDITED",
                "ADDED_TO_POOL");
        for (JsonNode entry : entries) {
            if (!entry.get("kind").asText().equals("ADDED_TO_POOL")) {
                assertThat(entry.get("projectId").isNull()).as(entry.get("kind").asText()).isTrue();
                assertThat(entry.get("projectTitle").isNull()).as(entry.get("kind").asText()).isTrue();
            }
        }
        assertThat(entries.get(2).get("noteExcerpt").asText()).isEqualTo("Prefers a listed company.");

        JsonNode onCredit2 = timeline(credit, onCredit, null).get("entries");
        assertThat(kindsOf(onCredit2)).contains("UPDATED_FROM_CALL");
        JsonNode profileOnCredit = onCredit2.get(kindsOf(onCredit2).indexOf("PROFILE_EDITED"));
        assertThat(profileOnCredit.get("projectTitle").asText()).isEqualTo("Head of Credit Risk");
    }

    @Test
    @DisplayName("the position read pages and groups within its boundary, and refuses a group nobody offers")
    void pagingAndGroupsStayInsideTheBoundary() throws Exception {
        firm("Paging Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        String onCfo = add(cfo, FATIMA).get("id").asText();
        String onCredit = add(credit, FATIMA).get("id").asText();
        for (String status : List.of("researching", "contacted", "engaged")) {
            setStatus(cfo, onCfo, status);
            setStatus(credit, onCredit, status);
        }

        JsonNode first = body(mvc.perform(get(timelineUrl(cfo, onCfo)).param("limit", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        JsonNode second = body(mvc.perform(get(timelineUrl(cfo, onCfo)).param("limit", "2")
                        .param("before", first.get("nextCursor").asText())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(kindsOf(first.get("entries"))).containsExactly("STATUS_CHANGED", "STATUS_CHANGED");
        assertThat(kindsOf(second.get("entries"))).containsExactly("STATUS_CHANGED", "ADDED_TO_POOL");
        assertThat(second.get("nextCursor").isNull()).isTrue();
        assertThat(first.toString() + second).doesNotContain("Head of Credit Risk");

        assertThat(kindsOf(timeline(cfo, onCfo, "notes").get("entries"))).isEmpty();
        mvc.perform(get(timelineUrl(cfo, onCfo)).param("group", "everything").header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a candidate of another position is not found through this position's route")
    void anotherPositionsCandidateIsNotFoundHere() throws Exception {
        firm("Wrong Route Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        add(cfo, FATIMA);
        String onCredit = add(credit, FATIMA).get("id").asText();

        mvc.perform(get(timelineUrl(cfo, onCredit)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        mvc.perform(get(timelineUrl(cfo, UUID.randomUUID().toString())).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a profile save records each changed field old and new; a save that changes nothing records none")
    void profileSavesRecordOldAndNew() throws Exception {
        firm("Changes Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, """
                {"fullName":"Fatima Al Mazrouei","title":"Finance Director","locationCity":"Dubai",
                 "locationCountry":"United Arab Emirates","yearsExperience":12,
                 "compensation":{"currency":"AED","baseSalary":85000,"noticePeriod":"1 month"}}""")
                .get("id").asText();

        save(cfo, candidateId, """
                {"fullName":"Fatima Al Mazrouei","title":"Group Finance Director","locationCity":"Abu Dhabi",
                 "locationCountry":"United Arab Emirates",
                 "compensation":{"currency":"AED","baseSalary":92000,"noticePeriod":"3 months"}}""");

        JsonNode edited = timeline(cfo, candidateId, "profile").get("entries").get(0);
        assertThat(edited.get("kind").asText()).isEqualTo("PROFILE_EDITED");
        Map<String, List<String>> changes = changesOf(edited);
        assertThat(changes).containsOnlyKeys("title", "location", "yearsExperience", "baseSalary", "noticePeriod");
        assertThat(changes.get("title")).containsExactly("Finance Director", "Group Finance Director");
        assertThat(changes.get("location")).containsExactly("Dubai, United Arab Emirates",
                "Abu Dhabi, United Arab Emirates");
        assertThat(changes.get("yearsExperience")).containsExactly("12", null);
        assertThat(changes.get("baseSalary")).containsExactly("AED 85,000", "AED 92,000");
        assertThat(changes.get("noticePeriod")).containsExactly("1 month", "3 months");
        assertThat(edited.get("details").has("changes")).isFalse();

        save(cfo, candidateId, """
                {"fullName":"Fatima Al Mazrouei","title":"Group Finance Director","locationCity":"Abu Dhabi",
                 "locationCountry":"United Arab Emirates",
                 "compensation":{"currency":"AED","baseSalary":92000,"noticePeriod":"3 months"}}""");
        JsonNode unchanged = timeline(cfo, candidateId, "profile").get("entries").get(0);
        assertThat(unchanged.get("id").asLong()).isNotEqualTo(edited.get("id").asLong());
        assertThat(unchanged.get("changes")).isEmpty();
    }

    @Test
    @DisplayName("contacts never appear among a profile save's changes, whatever the save supplied")
    void contactsAreNeverQuotedInChanges() throws Exception {
        firm("No Contacts Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, """
                {"fullName":"Lina Said","emails":[{"value":"lina@example.com"}]}""").get("id").asText();

        save(cfo, candidateId, """
                {"fullName":"Lina Said","title":"CFO","emails":[{"value":"lina.said@example.org"}]}""");

        String raw = timelineText(cfo, candidateId, "profile");
        assertThat(raw).doesNotContain("lina.said@example.org").doesNotContain("lina@example.com");
        assertThat(changesOf(json.readTree(raw).get("entries").get(0))).containsOnlyKeys("title");
    }

    @Test
    @DisplayName("an About that states an email or a phone is kept among the changes with both taken out")
    void aboutChangesAreRedacted() throws Exception {
        firm("Redacted About Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, FATIMA).get("id").asText();

        save(cfo, candidateId, """
                {"fullName":"Fatima Al Mazrouei","linkedinUrl":"https://www.linkedin.com/in/fatima-activity",
                 "summary":"Reach me at fatima.private@example.com or +971 50 123 4567 for treasury roles."}""");

        String raw = timelineText(cfo, candidateId, "profile");
        assertThat(raw).doesNotContain("fatima.private@example.com").doesNotContain("123 4567");
        String about = changesOf(json.readTree(raw).get("entries").get(0)).get("summary").get(1);
        assertThat(about).contains("[email removed]").contains("[phone removed]").contains("treasury roles");
    }

    @Test
    @DisplayName("the position read takes up to 500 lines in one page, while the workspace read still pages by 50")
    void thePositionReadTakesTheWholeHistoryInOnePage() throws Exception {
        firm("Long History Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, FATIMA);
        String personId = filed.get("personId").asText();
        for (int index = 0; index < 60; index++) {
            line(personId, cfo, "TAGGED", """
                    {"tagId":"%s","tag":"Tag %d"}""".formatted(UUID.randomUUID(), index));
        }

        JsonNode position = body(mvc.perform(get(timelineUrl(cfo, filed.get("id").asText())).param("limit", "500")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(position.get("entries")).hasSize(61);
        assertThat(position.get("nextCursor").isNull()).isTrue();

        JsonNode capped = body(mvc.perform(get(timelineUrl(cfo, filed.get("id").asText())).param("limit", "9999")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(capped.get("entries")).hasSize(61);

        JsonNode workspace = body(mvc.perform(get("/api/v1/candidates/" + personId + "/timeline").param("limit", "500")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(workspace.get("entries")).hasSize(50);
        assertThat(workspace.get("nextCursor").isNull()).isFalse();
    }

    @Test
    @DisplayName("a history line whose stored changes are malformed reads with none, never a failure")
    void malformedStoredChangesReadAsNone() throws Exception {
        firm("Malformed Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, FATIMA);
        String personId = filed.get("personId").asText();
        line(personId, cfo, "PROFILE_EDITED", """
                {"door":"MANUAL","changes":"not a list"}""");
        line(personId, cfo, "PROFILE_EDITED", """
                {"door":"MANUAL","changes":[{"from":"no field"},7,{"field":"title","from":null,"to":"CFO"}]}""");

        JsonNode entries = timeline(cfo, filed.get("id").asText(), "profile").get("entries");
        assertThat(entries.get(0).get("changes")).hasSize(1);
        assertThat(entries.get(0).get("changes").get(0).get("field").asText()).isEqualTo("title");
        assertThat(entries.get(0).get("changes").get(0).get("from").isNull()).isTrue();
        assertThat(entries.get(1).get("changes")).isEmpty();
    }

    @Test
    @DisplayName("an email line names its message, and a status the email moved names the automation")
    void outreachLinesNameTheirEmailAndAutomation() throws Exception {
        firm("Outreach Lines Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, FATIMA);
        UUID candidateId = UUID.fromString(filed.get("id").asText());
        UUID sent = UUID.randomUUID();
        UUID reply = UUID.randomUUID();
        String workspaceId = db.queryForObject("select workspace_id::text from app_lm_project where id = ?::uuid",
                String.class, cfo);

        transactions.executeWithoutResult(status -> outreach.recordEmailSent(null, UUID.fromString(cfo), candidateId,
                UUID.randomUUID(), "CFO approach", 1, sent));
        transactions.executeWithoutResult(status -> outreach.recordReplied(UUID.fromString(workspaceId),
                UUID.fromString(cfo), UUID.fromString(filed.get("personId").asText()), UUID.randomUUID(),
                "CFO approach", reply));

        JsonNode entries = timeline(cfo, candidateId.toString(), null).get("entries");
        Map<String, JsonNode> byKind = new java.util.LinkedHashMap<>();
        entries.forEach(entry -> byKind.putIfAbsent(entry.get("kind").asText(), entry));
        assertThat(byKind.get("EMAIL_SENT").get("details").get("messageId").asText()).isEqualTo(sent.toString());
        assertThat(byKind.get("EMAIL_REPLIED").get("details").get("messageId").asText()).isEqualTo(reply.toString());
        JsonNode moved = byKind.get("STATUS_CHANGED");
        assertThat(moved.get("details").get("automation").asText()).isEqualTo("EMAIL_SENT");
        assertThat(moved.get("details").get("to").asText()).isEqualTo("contacted");
    }

    @Test
    @DisplayName("a status moved by hand names no automation")
    void aStatusMovedByHandNamesNoAutomation() throws Exception {
        firm("By Hand Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, FATIMA).get("id").asText();
        setStatus(cfo, candidateId, "engaged");

        JsonNode moved = timeline(cfo, candidateId, "positions").get("entries").get(0);
        assertThat(moved.get("details").has("automation")).isFalse();
        assertThat(moved.get("actorName").asText()).isEqualTo("Alok Kumar");
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private void firm(String name) throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), name);
        admin = login(address);
    }

    private String mandate(String positionTitle) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s Unit"}""".formatted(positionTitle)))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, positionTitle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private JsonNode add(String projectId, String candidate) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidate))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private void save(String projectId, String candidateId, String candidate) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/candidates/" + candidateId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidate))
                .andExpect(status().isOk());
    }

    private void setStatus(String projectId, String candidateId, String statusKey) throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId + "/candidates/" + candidateId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}""".formatted(statusKey)))
                .andExpect(status().isOk());
    }

    private void note(String projectId, String candidateId, String text, boolean allPositions) throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/notes")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("kind", "general", "body", text,
                                "allPositions", allPositions))))
                .andExpect(status().isCreated());
    }

    /** A line as another door would have written it, for kinds whose own route needs a fixture this test has not. */
    private void line(String personId, String projectId, String kind, String details) {
        db.update("""
                insert into app_lm_person_activity (workspace_id, person_id, project_id, project_title, kind,
                                                    occurred_at, details)
                select p.workspace_id, ?::uuid, p.id, p.position_title, ?, now(), ?::jsonb
                from app_lm_project p where p.id = ?::uuid
                """, personId, kind, details, projectId);
    }

    private String timelineText(String projectId, String candidateId, String group) throws Exception {
        var request = get(timelineUrl(projectId, candidateId)).header("Authorization", "Bearer " + admin);
        if (group != null) {
            request = request.param("group", group);
        }
        return mvc.perform(request).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    }

    private JsonNode timeline(String projectId, String candidateId, String group) throws Exception {
        return json.readTree(timelineText(projectId, candidateId, group));
    }

    private static Map<String, List<String>> changesOf(JsonNode entry) {
        Map<String, List<String>> changes = new java.util.LinkedHashMap<>();
        entry.get("changes").forEach(change -> {
            List<String> values = new ArrayList<>();
            values.add(change.get("from").isNull() ? null : change.get("from").asText());
            values.add(change.get("to").isNull() ? null : change.get("to").asText());
            changes.put(change.get("field").asText(), values);
        });
        return changes;
    }

    private static List<String> kindsOf(JsonNode entries) {
        List<String> kinds = new ArrayList<>();
        entries.forEach(entry -> kinds.add(entry.get("kind").asText()));
        return kinds;
    }

    private static String timelineUrl(String projectId, String candidateId) {
        return "/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/timeline";
    }
}
