package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.TestKeyedHashers;
import app.lightmove.api.core.security.token.Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** #920: the suppression list, and every door that must ask it before filing someone. */
@IntegrationTest
class PersonSuppressionIntegrationTest extends FlowTestSupport {

    @Autowired @FixtureJdbc JdbcTemplate db;

    private String admin;

    @Test
    @DisplayName("an entry is kept as hashes only, and the list reads back its kind, reason, note and who added it")
    void entriesAreHashesOnly() throws Exception {
        firm("Hashing Firm");

        JsonNode added = body(suppress("""
                {"linkedinUrl":"linkedin.com/in/Noor-Haddad","email":"Noor@Example.com","reason":"objected",
                 "note":"Asked by email on 1 November"}""").andExpect(status().isCreated()).andReturn());

        assertThat(added).hasSize(2);
        assertThat(added).extracting(entry -> entry.get("kind").asText()).containsExactlyInAnyOrder("linkedin", "email");
        assertThat(added.get(0).get("reason").asText()).isEqualTo("objected");
        assertThat(added.get(0).get("note").asText()).isEqualTo("Asked by email on 1 November");
        assertThat(added.get(0).get("addedBy").get("name").asText()).isEqualTo("Alok Kumar");
        assertThat(added.toString()).doesNotContainIgnoringCase("noor");
        assertThat(db.queryForList("select value_hash from app_lm_workspace_suppression", String.class))
                .isNotEmpty()
                .allSatisfy(hash -> assertThat(hash).matches("[0-9a-f]{64}").doesNotContain("noor"));
        String workspaceId = db.queryForObject("select id::text from app_lm_workspace where name = 'Hashing Firm'",
                String.class);
        assertThat(db.queryForList("select value_hash from app_lm_workspace_suppression where workspace_id = ?::uuid",
                String.class, workspaceId))
                .doesNotContain(Tokens.hash(workspaceId + ":EMAIL:noor@example.com"));
        assertThat(db.queryForList("select key_id from app_lm_workspace_suppression where workspace_id = ?::uuid",
                Integer.class, workspaceId)).containsOnly(TestKeyedHashers.DEV_KEY_ID);

        JsonNode list = body(mvc.perform(get("/api/v1/workspace/suppressions")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(list).hasSize(2);

        JsonNode again = body(suppress("""
                {"email":"NOOR@example.com"}""").andExpect(status().isOk()).andReturn());
        assertThat(again).hasSize(1);
        assertThat(again.get(0).get("reason").asText()).isEqualTo("objected");
        assertThat(body(mvc.perform(get("/api/v1/workspace/suppressions")
                .header("Authorization", "Bearer " + admin)).andReturn())).hasSize(2);
    }

    @Test
    @DisplayName("an entry the deployment's keyset cannot read refuses every door, rather than letting the person in")
    void anUnreadableListRefusesTheDoor() throws Exception {
        firm("Rotated Key Firm");
        String position = mandate("Chief Legal Officer");
        suppress("""
                {"email":"rania@example.com"}""").andExpect(status().isCreated());
        db.update("update app_lm_workspace_suppression set key_id = 1 where workspace_id ="
                + " (select id from app_lm_workspace where name = 'Rotated Key Firm')");

        assertThat(codeOf(add(position, """
                {"fullName":"Omar Saleh","linkedinUrl":"https://www.linkedin.com/in/omar-saleh/"}""")
                .andExpect(status().isServiceUnavailable()).andReturn())).isEqualTo("SUPPRESSION_UNAVAILABLE");
        assertThat(db.queryForObject("select count(*) from app_lm_project_candidate where project_id = ?::uuid",
                Long.class, position)).isZero();
    }

    @Test
    @DisplayName("the add form, the plugin and a spreadsheet are refused a suppressed profile or address, however spelled")
    void everyDoorIsRefused() throws Exception {
        firm("Refusing Firm");
        String position = mandate("Chief Financial Officer");
        suppress("""
                {"linkedinUrl":"https://www.linkedin.com/in/noor-haddad/","email":"noor@example.com"}""")
                .andExpect(status().isCreated());

        assertThat(codeOf(add(position, """
                {"fullName":"Noor Haddad","linkedinUrl":"https://linkedin.com/in/Noor-Haddad"}""")
                .andExpect(status().isConflict()).andReturn())).isEqualTo("PERSON_SUPPRESSED");
        assertThat(codeOf(add(position, """
                {"fullName":"Noor Haddad","source":"extension","linkedinUrl":"https://www.linkedin.com/in/noor-haddad/"}""")
                .andExpect(status().isConflict()).andReturn())).isEqualTo("PERSON_SUPPRESSED");
        assertThat(codeOf(add(position, """
                {"fullName":"N. Haddad","source":"csv","email":"NOOR@example.com"}""")
                .andExpect(status().isConflict()).andReturn())).isEqualTo("PERSON_SUPPRESSED");
        assertThat(db.queryForObject("select count(*) from app_lm_person p join app_lm_workspace w on w.id = p.workspace_id"
                + " where w.name = 'Refusing Firm'", Long.class)).isZero();

        add(position, """
                {"fullName":"Omar Saleh","linkedinUrl":"https://www.linkedin.com/in/omar-saleh/"}""")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("an edit is refused only a suppressed key it adds; a person's own keys never block their edits")
    void editsAreRefusedOnlyWhatTheyAdd() throws Exception {
        firm("Editing Firm");
        String position = mandate("Head of Strategy");
        String omar = body(add(position, """
                {"fullName":"Omar Saleh","emails":[{"value":"omar@example.com"}]}""")
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        suppress("""
                {"email":"omar@example.com","reason":"objected"}""").andExpect(status().isCreated());
        suppress("""
                {"email":"noor@example.com"}""").andExpect(status().isCreated());

        mvc.perform(put(candidateUrl(position, omar))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Saleh","title":"Group Strategy Director",
                                 "emails":[{"value":"omar@example.com"}]}"""))
                .andExpect(status().isOk());

        assertThat(codeOf(mvc.perform(put(candidateUrl(position, omar) + "/contacts")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"emails":[{"value":"omar@example.com"},{"value":"Noor@example.com"}],"phones":[]}"""))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("PERSON_SUPPRESSED");
        assertThat(codeOf(mvc.perform(put(candidateUrl(position, omar))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Saleh","emails":[{"value":"noor@example.com"}]}"""))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("PERSON_SUPPRESSED");
    }

    @Test
    @DisplayName("someone the workspace knows is refused by their own keys, whatever the door's request carries")
    void aKnownPersonIsAskedByTheirOwnKeys() throws Exception {
        firm("Known Person Firm");
        String first = mandate("Chief Financial Officer");
        String second = mandate("Chief Risk Officer");
        String noor = body(add(first, """
                {"fullName":"Noor Haddad","employerName":"Aldar Properties",
                 "linkedinUrl":"https://www.linkedin.com/in/noor-haddad/","emails":[{"value":"noor@example.com"}]}""")
                .andExpect(status().isCreated()).andReturn()).get("personId").asText();
        suppress("""
                {"email":"noor@example.com","reason":"objected"}""").andExpect(status().isCreated());

        assertThat(codeOf(add(second, """
                {"fullName":"Noor Haddad","employerName":"Aldar Properties","existingPersonId":"%s"}""".formatted(noor))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("PERSON_SUPPRESSED");
        assertThat(codeOf(add(second, """
                {"fullName":"Noor Haddad","source":"extension","linkedinUrl":"https://www.linkedin.com/in/noor-haddad/"}""")
                .andExpect(status().isConflict()).andReturn())).isEqualTo("PERSON_SUPPRESSED");
        assertThat(db.queryForObject("select count(*) from app_lm_project_candidate where project_id = ?::uuid",
                Long.class, second)).isZero();
    }

    @Test
    @DisplayName("Add to position from the Candidates page leaves a suppressed person out and counts them")
    void addToPositionLeavesSuppressedPeopleOut() throws Exception {
        firm("Pool Firm");
        String first = mandate("Chief Technology Officer");
        String second = mandate("Chief Data Officer");
        String layla = body(add(first, """
                {"fullName":"Layla Nasser","linkedinUrl":"https://www.linkedin.com/in/layla-nasser/"}""")
                .andExpect(status().isCreated()).andReturn()).get("personId").asText();
        String omar = body(add(first, """
                {"fullName":"Omar Saleh","emails":[{"value":"omar@example.com"}]}""")
                .andExpect(status().isCreated()).andReturn()).get("personId").asText();
        suppress("""
                {"linkedinUrl":"linkedin.com/in/Layla-Nasser"}""").andExpect(status().isCreated());

        JsonNode answer = body(mvc.perform(post("/api/v1/candidates/bulk/position")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","personIds":["%s","%s"]}""".formatted(second, layla, omar)))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(answer.get("added").asInt()).isEqualTo(1);
        assertThat(answer.get("alreadyIn").asInt()).isZero();
        assertThat(answer.get("suppressed").asInt()).isEqualTo(1);
        assertThat(db.queryForList("select person_id::text from app_lm_project_candidate where project_id = ?::uuid",
                String.class, second)).containsExactly(omar);
    }

    @Test
    @DisplayName("lifting an entry lets the person be filed again")
    void liftingAnEntryReopensTheDoor() throws Exception {
        firm("Lifting Firm");
        String position = mandate("Chief Operating Officer");
        String entry = body(suppress("""
                {"linkedinUrl":"https://www.linkedin.com/in/layla-nasser/"}""")
                .andExpect(status().isCreated()).andReturn()).get(0).get("id").asText();
        String layla = """
                {"fullName":"Layla Nasser","linkedinUrl":"https://www.linkedin.com/in/layla-nasser/"}""";
        add(position, layla).andExpect(status().isConflict());

        mvc.perform(delete("/api/v1/workspace/suppressions/" + entry).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        add(position, layla).andExpect(status().isCreated());
        assertThat(db.queryForList("select event_type from app_lm_audit_event where event_type like 'PERSON_SUPPRESSION_%'"
                + " and target_id = ?", String.class, entry)).containsExactly("PERSON_SUPPRESSION_REMOVED");
    }

    @Test
    @DisplayName("one firm's list never refuses another's filing")
    void aListIsTheWorkspacesOwn() throws Exception {
        firm("First Listing Firm");
        suppress("""
                {"linkedinUrl":"https://www.linkedin.com/in/rania-khoury/"}""").andExpect(status().isCreated());

        firm("Second Listing Firm");
        String position = mandate("Chief People Officer");
        add(position, """
                {"fullName":"Rania Khoury","linkedinUrl":"https://www.linkedin.com/in/rania-khoury/"}""")
                .andExpect(status().isCreated());
        assertThat(body(mvc.perform(get("/api/v1/workspace/suppressions").header("Authorization", "Bearer " + admin))
                .andReturn())).isEmpty();
    }

    @Test
    @DisplayName("an entry needs a profile link or an address, never says erased, and is an admin's alone")
    void entriesAreCheckedAndAdminOnly() throws Exception {
        firm("Checking Firm");
        assertThat(fieldOf(suppress("{}").andExpect(status().isBadRequest()).andReturn())).isEqualTo("linkedinUrl");
        assertThat(fieldOf(suppress("""
                {"linkedinUrl":"https://www.linkedin.com/company/aldar/"}""")
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("linkedinUrl");
        assertThat(fieldOf(suppress("""
                {"email":"noor@example.com","reason":"erased"}""")
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("reason");

        String member = "member@" + domain;
        inviteAndAccept(admin, "Mira Member", member, "MEMBER");
        String memberToken = login(member);
        mvc.perform(get("/api/v1/workspace/suppressions").header("Authorization", "Bearer " + memberToken))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/workspace/suppressions")
                        .header("Authorization", "Bearer " + memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"noor@example.com\"}"))
                .andExpect(status().isForbidden());
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private void firm(String name) throws Exception {
        String address = name.toLowerCase().replaceAll("[^a-z]", "") + "@" + domain;
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

    private ResultActions suppress(String json) throws Exception {
        return mvc.perform(post("/api/v1/workspace/suppressions")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions add(String projectId, String json) throws Exception {
        return mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private String fieldOf(MvcResult result) throws Exception {
        return body(result).get("fieldErrors").propertyNames().iterator().next();
    }

    private static String candidateUrl(String projectId, String candidateId) {
        return "/api/v1/projects/" + projectId + "/candidates/" + candidateId;
    }
}
