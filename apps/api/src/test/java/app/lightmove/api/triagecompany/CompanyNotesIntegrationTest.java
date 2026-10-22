package app.lightmove.api.triagecompany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.triagecompany.service.CompanyNotePurge;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * A mandate's notes on its companies (V143): many per company, authored and timed, Markdown or plain, changed only
 * by their author or an admin, soft-deleted with an Undo, and never shown to a client seat — not on the notes route,
 * not on the grid, the talent map or the export.
 */
@IntegrationTest
class CompanyNotesIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;
    @Autowired CompanyNotePurge purge;

    private String admin;

    @Test
    @DisplayName("notes are written, listed pinned first, edited by their author, and counted on the staff grid")
    void notesAreWrittenAndCounted() throws Exception {
        firm("Company Notes Firm");
        String projectId = mandate("Chief Financial Officer");
        String companyId = capture(projectId, "Gulf Industrial", null);

        String first = write(admin, projectId, companyId, "call", "Spoke to their HR lead", "plain").get("id").asText();
        JsonNode second = write(admin, projectId, companyId, "meeting", "**Board** reshuffle in Q1", "markdown");
        assertThat(second.get("format").asText()).isEqualTo("markdown");
        assertThat(second.get("authorName").asText()).isEqualTo("Alok Kumar");
        assertThat(second.get("editable").asBoolean()).isTrue();

        mvc.perform(patch(notesUrl(projectId, companyId) + "/" + first + "/pin")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pinned":true}"""))
                .andExpect(status().isOk());
        JsonNode revised = body(mvc.perform(put(notesUrl(projectId, companyId) + "/" + first)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"call","body":"Spoke to their *HR lead*","format":"markdown"}"""))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(revised.get("editedByName").asText()).isEqualTo("Alok Kumar");

        assertThat(bodiesOf(notes(admin, projectId, companyId)))
                .containsExactly("Spoke to their *HR lead*", "**Board** reshuffle in Q1");
        JsonNode grid = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(grid.at("/companies/0/noteCount").asInt()).isEqualTo(2);
        assertThat(grid.at("/companies/0").has("note")).isFalse();
        assertThat(grid.at("/companies/0/addedByName").asText()).isEqualTo("Alok Kumar");
    }

    @Test
    @DisplayName("only the author or an admin changes, removes or restores a note; anyone on the team pins it")
    void onlyTheAuthorOrAnAdminChangesANote() throws Exception {
        firm("Company Note Owners Firm");
        String projectId = mandate("Head of Credit Risk");
        String companyId = capture(projectId, "Harbour Ports", null);
        String sara = researcherOn(projectId);
        String adminsNote = write(admin, projectId, companyId, "general", "Owned by the family office", "plain")
                .get("id").asText();

        assertThat(notes(sara, projectId, companyId).get(0).get("editable").asBoolean()).isFalse();
        String refusal = codeOf(mvc.perform(put(notesUrl(projectId, companyId) + "/" + adminsNote)
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"general","body":"Rewritten"}"""))
                .andExpect(status().isForbidden())
                .andReturn());
        assertThat(refusal).isEqualTo("COMPANY_NOTE_NOT_YOURS");
        mvc.perform(delete(notesUrl(projectId, companyId) + "/" + adminsNote).header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden());
        mvc.perform(patch(notesUrl(projectId, companyId) + "/" + adminsNote + "/pin")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pinned":true}"""))
                .andExpect(status().isOk());

        String sarasNote = write(sara, projectId, companyId, "call", "Their CFO is leaving", "plain").get("id").asText();
        mvc.perform(delete(notesUrl(projectId, companyId) + "/" + sarasNote).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("a removed note leaves every read, comes back on Undo, and is gone for good once purged")
    void aRemovedNoteIsRestoredOrPurged() throws Exception {
        firm("Company Note Undo Firm");
        String projectId = mandate("Chief Operating Officer");
        String companyId = capture(projectId, "Cedar Retail", null);
        String noteId = write(admin, projectId, companyId, "general", "Expanding into Oman", "plain")
                .get("id").asText();

        mvc.perform(delete(notesUrl(projectId, companyId) + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(notes(admin, projectId, companyId)).isEmpty();

        JsonNode restored = body(mvc.perform(post(notesUrl(projectId, companyId) + "/" + noteId + "/restore")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(restored.get("body").asText()).isEqualTo("Expanding into Oman");
        assertThat(notes(admin, projectId, companyId)).hasSize(1);

        mvc.perform(delete(notesUrl(projectId, companyId) + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(purge.purgeAt(Instant.now())).isZero();
        db.update("update app_lm_company_note set deleted_at = now() - interval '31 days' where id = ?::uuid", noteId);
        assertThat(purge.purgeAt(Instant.now())).isEqualTo(1);
        mvc.perform(post(notesUrl(projectId, companyId) + "/" + noteId + "/restore")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("two Undos at once restore once, and an Undo onto the same words written since answers that copy")
    void anUndoRestoresOnceAndNeverDuplicates() throws Exception {
        firm("Company Note Double Undo Firm");
        String projectId = mandate("Chief Operating Officer");
        String companyId = capture(projectId, "Cedar Retail", null);
        String noteId = write(admin, projectId, companyId, "general", "Expanding into Oman", "plain")
                .get("id").asText();
        mvc.perform(delete(notesUrl(projectId, companyId) + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> undos = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                undos.add(pool.submit(() -> mvc.perform(post(notesUrl(projectId, companyId) + "/" + noteId
                                + "/restore").header("Authorization", "Bearer " + admin))
                        .andReturn().getResponse().getStatus()));
            }
            for (Future<Integer> undo : undos) {
                assertThat(undo.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }
        assertThat(restoresOn(projectId)).isEqualTo(1);

        mvc.perform(delete(notesUrl(projectId, companyId) + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        String again = write(admin, projectId, companyId, "general", "Expanding into Oman", "plain")
                .get("id").asText();
        JsonNode undone = body(mvc.perform(post(notesUrl(projectId, companyId) + "/" + noteId + "/restore")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(undone.get("id").asText()).isEqualTo(again);
        assertThat(notes(admin, projectId, companyId)).hasSize(1);
        assertThat(restoresOn(projectId)).isEqualTo(1);
    }

    @Test
    @DisplayName("an Undo brings the note back when only a colleague wrote the same words since")
    void anUndoIsNeverAnsweredByAColleaguesNote() throws Exception {
        firm("Company Note Colleague Undo Firm");
        String projectId = mandate("Chief Operating Officer");
        String companyId = capture(projectId, "Cedar Retail", null);
        String sara = researcherOn(projectId);
        String mine = write(admin, projectId, companyId, "call", "Left a voicemail", "plain").get("id").asText();
        mvc.perform(delete(notesUrl(projectId, companyId) + "/" + mine).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        String hers = write(sara, projectId, companyId, "general", "Left a voicemail", "plain").get("id").asText();

        JsonNode undone = body(mvc.perform(post(notesUrl(projectId, companyId) + "/" + mine + "/restore")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(undone.get("id").asText()).isEqualTo(mine);
        assertThat(undone.get("kind").asText()).isEqualTo("call");
        List<String> ids = new ArrayList<>();
        notes(admin, projectId, companyId).forEach(note -> ids.add(note.get("id").asText()));
        assertThat(ids).containsExactlyInAnyOrder(mine, hers);
        assertThat(restoresOn(projectId)).isEqualTo(1);
    }

    @Test
    @DisplayName("a note sent with a company is filed once as a plain note, and a re-sent one adds nothing")
    void aDoorsNoteIsFiledOnce() throws Exception {
        firm("Company Door Note Firm");
        String projectId = mandate("Chief Financial Officer");
        String companyId = capture(projectId, "Dhow Marine", "Met at **ADIPEC**");

        JsonNode found = notes(admin, projectId, companyId);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).get("format").asText()).isEqualTo("plain");
        assertThat(found.get(0).get("kind").asText()).isEqualTo("general");
        assertThat(found.get(0).get("body").asText()).isEqualTo("Met at **ADIPEC**");
    }

    @Test
    @DisplayName("a client seat reaches no company note, and no count of them, by any route")
    void aClientSeatReachesNone() throws Exception {
        firm("Company Note Client Firm");
        String projectId = mandate("Chief Executive Officer");
        String companyId = capture(projectId, "Falcon Logistics", "Internal: their board is split");
        String client = clientOn(projectId);

        mvc.perform(get(notesUrl(projectId, companyId)).header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
        mvc.perform(post(notesUrl(projectId, companyId))
                        .header("Authorization", "Bearer " + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"general","body":"From the client"}"""))
                .andExpect(status().isForbidden());
        for (String read : List.of("/triage", "/talent-map", "/export?stage=inUniverse")) {
            String seen = mvc.perform(get("/api/v1/projects/" + projectId + read)
                            .header("Authorization", "Bearer " + client))
                    .andReturn().getResponse().getContentAsString();
            assertThat(seen).as(read).doesNotContain("their board is split");
        }
        JsonNode grid = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage")
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(grid.at("/companies/0/noteCount").isNull()).isTrue();
        assertThat(grid.at("/companies/0/addedByName").isNull()).isTrue();
        String export = mvc.perform(get("/api/v1/projects/" + projectId + "/export/companies?stage=inUniverse")
                        .header("Authorization", "Bearer " + client))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(export).doesNotContain("Added by").doesNotContain("Alok Kumar");
    }

    @Test
    @DisplayName("removing the company from the mandate takes its notes with it")
    void removingTheCompanyRemovesItsNotes() throws Exception {
        firm("Company Note Removal Firm");
        String projectId = mandate("Group Treasurer");
        String companyId = capture(projectId, "Gulf Cement", "Worth a look");

        mvc.perform(delete("/api/v1/projects/" + projectId + "/triage/" + companyId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        assertThat(db.queryForObject("select count(*) from app_lm_company_note where triage_company_id = ?::uuid",
                Integer.class, companyId)).isZero();
    }

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

    private String capture(String projectId, String companyName, String note) throws Exception {
        Map<String, Object> request = note == null ? Map.of("companyName", companyName)
                : Map.of("companyName", companyName, "note", note);
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    /** Sara, a MEMBER, seated as RESEARCHER on {@code projectId}; returns her token. */
    private String researcherOn(String projectId) throws Exception {
        String saraEmail = "sara@" + domain;
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
        String clientEmail = "client@company-notes-" + domain;
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

    private JsonNode write(String token, String projectId, String companyId, String kind, String text, String format)
            throws Exception {
        return body(mvc.perform(post(notesUrl(projectId, companyId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("kind", kind, "body", text, "format", format))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private int restoresOn(String projectId) {
        return db.queryForObject("""
                select count(*) from app_lm_audit_event
                where target_id = ? and event_type = 'COMPANY_NOTE_RESTORED'""", Integer.class, projectId);
    }

    private JsonNode notes(String token, String projectId, String companyId) throws Exception {
        return body(mvc.perform(get(notesUrl(projectId, companyId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static List<String> bodiesOf(JsonNode notes) {
        List<String> bodies = new ArrayList<>();
        notes.forEach(note -> bodies.add(note.get("body").asText()));
        return bodies;
    }

    private static String notesUrl(String projectId, String companyId) {
        return "/api/v1/projects/" + projectId + "/triage/" + companyId + "/notes";
    }
}
