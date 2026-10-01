package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * A person's notes and history (V96): one set of notes whichever mandate they are read from, written
 * and timed by name, changed only by their author or an admin, and never shown to a client seat — not
 * on the notes routes, not on the row a client reads, not through the workspace's own routes.
 */
@IntegrationTest
class PersonNotesIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    private String admin;
    private String saraEmail;

    @Test
    @DisplayName("a note written on one position is read from every other, with its author, kind and context")
    void aNoteIsSharedAcrossPositions() throws Exception {
        firm("Shared Notes Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        JsonNode onCfo = add(cfo, """
                {"fullName":"Fatima Al Mazrouei","linkedinUrl":"https://www.linkedin.com/in/fatima-notes"}""");
        JsonNode onCredit = add(credit, """
                {"fullName":"Fatima Al Mazrouei","linkedinUrl":"https://www.linkedin.com/in/fatima-notes"}""");

        write(admin, cfo, onCfo.get("id").asText(), "call", "Open to a group CFO move; wants Dubai.");

        JsonNode fromCredit = notes(admin, credit, onCredit.get("id").asText());
        assertThat(fromCredit).hasSize(1);
        JsonNode note = fromCredit.get(0);
        assertThat(note.get("kind").asText()).isEqualTo("call");
        assertThat(note.get("body").asText()).isEqualTo("Open to a group CFO move; wants Dubai.");
        assertThat(note.get("projectTitle").asText()).isEqualTo("Chief Financial Officer");
        assertThat(note.get("authorName").asText()).isEqualTo("Alok Kumar");
        assertThat(note.get("editable").asBoolean()).isTrue();
        assertThat(note.get("createdAt").isNull()).isFalse();

        JsonNode fromPool = body(mvc.perform(get("/api/v1/candidates/" + onCfo.get("personId").asText() + "/notes")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(fromPool).hasSize(1);
        assertThat(fromPool.get(0).get("id").asText()).isEqualTo(note.get("id").asText());
    }

    @Test
    @DisplayName("a note is changed or removed by its author or an admin, and pinned by anyone")
    void onlyItsAuthorOrAnAdminChangesANote() throws Exception {
        firm("Note Owners Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, """
                {"fullName":"Khalid Al Harbi"}""").get("id").asText();
        String sara = researcherOn(cfo);

        String adminsNote = write(admin, cfo, candidateId, "general", "Prior placement at Almarai.")
                .get("id").asText();
        String sarasNote = write(sara, cfo, candidateId, "meeting", "Met at the Riyadh summit.").get("id").asText();

        String refusal = codeOf(mvc.perform(put(notesUrl(cfo, candidateId) + "/" + adminsNote)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"general","body":"Rewritten by someone else."}"""))
                .andExpect(status().isForbidden())
                .andReturn());
        assertThat(refusal).isEqualTo("PERSON_NOTE_NOT_YOURS");
        mvc.perform(delete(notesUrl(cfo, candidateId) + "/" + adminsNote).header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());

        JsonNode revised = body(mvc.perform(put(notesUrl(cfo, candidateId) + "/" + sarasNote)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"call","body":"Met at the Riyadh summit; follow up in May."}"""))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(revised.get("editedByName").asText()).isEqualTo("Alok Kumar");
        assertThat(revised.get("kind").asText()).isEqualTo("call");

        mvc.perform(patch(notesUrl(cfo, candidateId) + "/" + adminsNote + "/pin")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pinned":true}"""))
                .andExpect(status().isOk());

        JsonNode asSara = notes(sara, cfo, candidateId);
        assertThat(asSara.get(0).get("id").asText()).isEqualTo(adminsNote);
        assertThat(asSara.get(0).get("pinned").asBoolean()).isTrue();
        assertThat(asSara.get(0).get("editable").asBoolean()).isFalse();
        assertThat(asSara.get(1).get("editable").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a kind, group or pin nobody offers is a 400, never a null filed or a 500")
    void anUnknownTokenIsRefused() throws Exception {
        firm("Unknown Token Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, """
                {"fullName":"Hamad Al Suwaidi"}""");
        String candidateId = filed.get("id").asText();
        String noteId = write(admin, cfo, candidateId, "general", "First call booked.").get("id").asText();

        String postRefusal = codeOf(mvc.perform(post(notesUrl(cfo, candidateId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"memo","body":"Filed under a kind nobody offers."}"""))
                .andExpect(status().isBadRequest())
                .andReturn());
        assertThat(postRefusal).isEqualTo("VALIDATION_FAILED");
        mvc.perform(put(notesUrl(cfo, candidateId) + "/" + noteId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"memo","body":"Revised under a kind nobody offers."}"""))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(notesUrl(cfo, candidateId) + "/" + noteId + "/pin")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());

        mvc.perform(get(timelineUrl(cfo, candidateId)).param("group", "everything")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/candidates/" + filed.get("personId").asText() + "/timeline")
                        .param("group", "everything")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/candidates/activity").param("group", "everything")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isBadRequest());

        JsonNode kept = notes(admin, cfo, candidateId);
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).get("kind").asText()).isEqualTo("general");
        assertThat(kept.get(0).get("pinned").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("pinning a note is audited, though it leaves no timeline line")
    void aPinIsAudited() throws Exception {
        firm("Pin Audit Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, """
                {"fullName":"Mariam Al Kaabi"}""").get("id").asText();
        String noteId = write(admin, cfo, candidateId, "general", "Strong on treasury.").get("id").asText();

        mvc.perform(patch(notesUrl(cfo, candidateId) + "/" + noteId + "/pin")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pinned":true}"""))
                .andExpect(status().isOk());

        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = 'PERSON_NOTE_PINNED' AND metadata ->> 'noteId' = ?""", Integer.class, noteId))
                .isEqualTo(1);
        assertThat(kindsOf(timeline(cfo, candidateId, "notes").get("entries"))).containsExactly("NOTE_ADDED");
    }

    @Test
    @DisplayName("on the workspace's routes a note is filed about a position only by someone seated on it")
    void aPoolNoteAboutAPositionNeedsItsSeat() throws Exception {
        firm("Pool Note Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        String personId = add(cfo, """
                {"fullName":"Yousef Haddad","linkedinUrl":"https://www.linkedin.com/in/yousef-pool"}""")
                .get("personId").asText();
        add(credit, """
                {"fullName":"Yousef Haddad","linkedinUrl":"https://www.linkedin.com/in/yousef-pool"}""");
        String sara = researcherOn(credit);

        mvc.perform(post("/api/v1/candidates/" + personId + "/notes")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"general","body":"Filed against a mandate I am not on.","projectId":"%s"}"""
                                .formatted(cfo)))
                .andExpect(status().isForbidden());
        JsonNode onCredit = body(mvc.perform(post("/api/v1/candidates/" + personId + "/notes")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"general","body":"Filed against my own mandate.","projectId":"%s"}"""
                                .formatted(credit)))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(onCredit.get("projectTitle").asText()).isEqualTo("Head of Credit Risk");
        mvc.perform(post("/api/v1/candidates/" + personId + "/notes")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"general","body":"About no position at all."}"""))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("a removed note leaves a line saying so and none of its words")
    void aRemovedNoteLeavesNoWords() throws Exception {
        firm("Removed Note Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, """
                {"fullName":"Noura Al Thani"}""");
        String candidateId = filed.get("id").asText();
        String noteId = write(admin, cfo, candidateId, "call", "Asked for 2.4m AED base, discreetly.")
                .get("id").asText();

        JsonNode before = timeline(cfo, candidateId, "notes");
        assertThat(before.get("entries").get(0).get("noteExcerpt").asText()).startsWith("Asked for 2.4m");

        mvc.perform(delete(notesUrl(cfo, candidateId) + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        JsonNode after = timeline(cfo, candidateId, "notes").get("entries");
        assertThat(kindsOf(after)).containsExactly("NOTE_REMOVED", "NOTE_ADDED");
        assertThat(after).allSatisfy(line -> assertThat(line.get("noteExcerpt").isNull()).isTrue());
        assertThat(db.queryForObject("select count(*) from app_lm_person_note where id = ?::uuid", Integer.class,
                noteId)).isZero();
        assertThat(db.queryForObject("""
                select count(*) from app_lm_person_activity
                where person_id = ?::uuid and details::text like '%2.4m%'""", Integer.class,
                filed.get("personId").asText())).isZero();
    }

    @Test
    @DisplayName("a note sent with an executive is filed once as a note about that position")
    void aDoorsNoteIsFiledOnce() throws Exception {
        firm("Door Note Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, """
                {"fullName":"James Whitfield","note":"Captured at the Gulf CFO forum."}""");
        String candidateId = filed.get("id").asText();

        mvc.perform(put("/api/v1/projects/" + cfo + "/candidates/" + candidateId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"James Whitfield","note":"Captured at the Gulf CFO forum."}"""))
                .andExpect(status().isOk());

        JsonNode found = notes(admin, cfo, candidateId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).get("kind").asText()).isEqualTo("general");
        assertThat(found.get(0).get("projectTitle").asText()).isEqualTo("Chief Financial Officer");
        assertThat(filed.has("note")).isFalse();
    }

    @Test
    @DisplayName("the timeline reads newest first by page and group, and the workspace feed by who and where")
    void theTimelineAndTheFeedRead() throws Exception {
        firm("Timeline Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        String candidateId = add(cfo, """
                {"fullName":"Rajesh Menon","linkedinUrl":"https://www.linkedin.com/in/rajesh-timeline"}""")
                .get("id").asText();
        String sara = researcherOn(credit);
        mvc.perform(post("/api/v1/projects/" + credit + "/candidates")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Rajesh Menon","linkedinUrl":"https://www.linkedin.com/in/rajesh-timeline"}"""))
                .andExpect(status().isCreated());
        mvc.perform(patch("/api/v1/projects/" + cfo + "/candidates/" + candidateId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"contacted"}"""))
                .andExpect(status().isOk());
        write(admin, cfo, candidateId, "email", "Sent the brief.");

        JsonNode all = timeline(cfo, candidateId, null).get("entries");
        assertThat(kindsOf(all)).containsExactly("NOTE_ADDED", "STATUS_CHANGED", "MAPPED", "ADDED_TO_POOL");
        assertThat(all.get(1).get("details").get("to").asText()).isEqualTo("contacted");
        assertThat(all.get(2).get("actorName").asText()).isEqualTo("Sara Al-Mansour");
        assertThat(all.get(2).get("projectTitle").asText()).isEqualTo("Head of Credit Risk");

        JsonNode firstPage = body(mvc.perform(get(timelineUrl(cfo, candidateId)).param("limit", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode secondPage = body(mvc.perform(get(timelineUrl(cfo, candidateId)).param("limit", "2")
                        .param("before", firstPage.get("nextCursor").asText())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(kindsOf(secondPage.get("entries"))).containsExactly("MAPPED", "ADDED_TO_POOL");
        assertThat(secondPage.get("nextCursor").isNull()).isTrue();

        String saraId = db.queryForObject("select id::text from app_lm_user where email = ?", String.class, saraEmail);
        JsonNode bySara = body(mvc.perform(get("/api/v1/candidates/activity").param("actor", saraId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("entries");
        assertThat(kindsOf(bySara)).containsExactly("MAPPED");
        assertThat(bySara.get(0).get("personName").asText()).isEqualTo("Rajesh Menon");
        JsonNode onCfo = body(mvc.perform(get("/api/v1/candidates/activity").param("position", cfo)
                        .param("group", "positions")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("entries");
        assertThat(kindsOf(onCfo)).containsExactly("STATUS_CHANGED", "ADDED_TO_POOL");
    }

    @Test
    @DisplayName("the person's positions lead with the one the drawer was opened from")
    void positionsLeadWithThisOne() throws Exception {
        firm("Positions Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        add(cfo, """
                {"fullName":"Aisha Karim","linkedinUrl":"https://www.linkedin.com/in/aisha-positions"}""");
        String onCredit = add(credit, """
                {"fullName":"Aisha Karim","linkedinUrl":"https://www.linkedin.com/in/aisha-positions",
                 "status":"engaged"}""").get("id").asText();

        JsonNode positions = body(mvc.perform(get("/api/v1/projects/" + credit + "/candidates/" + onCredit
                        + "/positions").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(positions).hasSize(2);
        assertThat(positions.get(0).get("positionTitle").asText()).isEqualTo("Head of Credit Risk");
        assertThat(positions.get(0).get("status").asText()).isEqualTo("engaged");
        assertThat(positions.get(0).get("addedByName").asText()).isEqualTo("Alok Kumar");
        assertThat(positions.get(1).get("positionTitle").asText()).isEqualTo("Chief Financial Officer");
    }

    @Test
    @DisplayName("a client seat reaches no note, no history and no other position, by any route")
    void aClientSeatReachesNone() throws Exception {
        firm("Client Notes Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, """
                {"fullName":"Lina Said","note":"Internal: do not share the package."}""");
        String candidateId = filed.get("id").asText();
        String client = clientOn(cfo);

        for (String route : List.of("/notes", "/timeline", "/positions")) {
            mvc.perform(get("/api/v1/projects/" + cfo + "/candidates/" + candidateId + route)
                            .header("Authorization", "Bearer " + client))
                    .andExpect(status().isForbidden());
        }
        mvc.perform(get("/api/v1/candidates/" + filed.get("personId").asText() + "/notes")
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/candidates/activity").header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());

        for (String read : List.of("/candidates/" + candidateId, "/talent-map", "/report")) {
            String seen = mvc.perform(get("/api/v1/projects/" + cfo + read).header("Authorization", "Bearer " + client))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            assertThat(seen).as(read).doesNotContain("do not share the package");
        }
    }

    @Test
    @DisplayName("another workspace's person is not found on the workspace's routes")
    void anotherWorkspacesPersonIsNotFound() throws Exception {
        firm("Ours Firm");
        String cfo = mandate("Chief Financial Officer");
        String personId = add(cfo, """
                {"fullName":"Omar Farouk"}""").get("personId").asText();

        String outsiderEmail = "nadia@other-" + domain;
        createWorkspace(verifiedUser("Nadia Rahman", outsiderEmail), "Theirs Firm");
        String outsider = login(outsiderEmail);
        for (String route : List.of("", "/notes", "/timeline")) {
            mvc.perform(get("/api/v1/candidates/" + personId + route).header("Authorization", "Bearer " + outsider))
                    .andExpect(status().isNotFound());
        }
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

    /** Sara, a MEMBER, seated as RESEARCHER on {@code projectId}; returns her token. */
    private String researcherOn(String projectId) throws Exception {
        saraEmail = "sara@" + domain;
        inviteAndAccept(admin, "Sara Al-Mansour", saraEmail, "MEMBER");
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, saraEmail))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isOk());
        return login(saraEmail);
    }

    private String clientOn(String projectId) throws Exception {
        String clientEmail = "client@notes-client-" + domain;
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"A Client","position":"Chair","email":"%s"}
                                """.formatted(clientEmail)))
                .andExpect(status().isOk());
        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"A Client","password":"%s"}
                                """.formatted(email.latestTokenFor(clientEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }

    private JsonNode add(String projectId, String json) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private JsonNode write(String token, String projectId, String candidateId, String kind, String text)
            throws Exception {
        return body(mvc.perform(post(notesUrl(projectId, candidateId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("kind", kind, "body", text))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private JsonNode notes(String token, String projectId, String candidateId) throws Exception {
        return body(mvc.perform(get(notesUrl(projectId, candidateId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode timeline(String projectId, String candidateId, String group) throws Exception {
        var request = get(timelineUrl(projectId, candidateId)).header("Authorization", "Bearer " + admin);
        if (group != null) {
            request = request.param("group", group);
        }
        return body(mvc.perform(request).andExpect(status().isOk()).andReturn());
    }

    private static List<String> kindsOf(JsonNode entries) {
        List<String> kinds = new ArrayList<>();
        entries.forEach(entry -> kinds.add(entry.get("kind").asText()));
        return kinds;
    }

    private static String notesUrl(String projectId, String candidateId) {
        return "/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/notes";
    }

    private static String timelineUrl(String projectId, String candidateId) {
        return "/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/timeline";
    }
}
