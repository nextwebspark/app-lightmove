package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.candidate.service.PersonNotePurge;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * A person's notes and history (V96): a note about a position read on that position alone, one for all
 * positions on every one, both on the workspace's Candidates page; written and timed by name, changed only by
 * their author or an admin, and never shown to a client seat — not on the notes routes, not on the row a
 * client reads, not through the workspace's own routes.
 */
@IntegrationTest
class PersonNotesIntegrationTest extends FlowTestSupport {

    private static final String FATIMA = """
            {"fullName":"Fatima Al Mazrouei","linkedinUrl":"https://www.linkedin.com/in/fatima-notes"}""";

    /** Answers both background prompts of a Suggest with AI press, each binding the half it asked for. */
    private static final String AI_BACKGROUND = """
            {"category":"Emirati","confidence":"high","evidence_for":["Emiratisation graduate programme"],
             "evidence_against":[],"rule_applied":"none",
             "gender":"female","yearsExperience":14,"seniority":"N-1"}""";

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired StubChatModel model;
    @Autowired PersonNotePurge purge;

    private String admin;
    private String saraEmail;

    @Test
    @DisplayName("a note about one position is read there and on the Candidates page, never on another position")
    void aPositionsNoteStaysOnIt() throws Exception {
        firm("Position Notes Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        JsonNode onCfo = add(cfo, FATIMA);
        String onCredit = add(credit, FATIMA).get("id").asText();

        write(admin, cfo, onCfo.get("id").asText(), "call", "Open to a group CFO move; wants Dubai.");

        JsonNode fromCfo = notes(admin, cfo, onCfo.get("id").asText());
        assertThat(fromCfo).hasSize(1);
        JsonNode note = fromCfo.get(0);
        assertThat(note.get("kind").asText()).isEqualTo("call");
        assertThat(note.get("body").asText()).isEqualTo("Open to a group CFO move; wants Dubai.");
        assertThat(note.get("projectId").asText()).isEqualTo(cfo);
        assertThat(note.get("projectTitle").asText()).isEqualTo("Chief Financial Officer");
        assertThat(note.get("authorName").asText()).isEqualTo("Alok Kumar");
        assertThat(note.get("editable").asBoolean()).isTrue();
        assertThat(note.get("createdAt").isNull()).isFalse();

        assertThat(notes(admin, credit, onCredit)).isEmpty();
        JsonNode fromPool = poolNotes(onCfo.get("personId").asText());
        assertThat(fromPool).hasSize(1);
        assertThat(fromPool.get(0).get("id").asText()).isEqualTo(note.get("id").asText());

        // Another position's note is its own work: not even a line naming it reaches this position's history.
        assertThat(timeline(credit, onCredit, "notes").get("entries")).isEmpty();
        assertThat(timeline(cfo, onCfo.get("id").asText(), "notes").get("entries").get(0).get("noteExcerpt").asText())
                .startsWith("Open to a group CFO move");
    }

    @Test
    @DisplayName("a LinkedIn exchange is filed as its own kind, and its timeline line says so")
    void aLinkedInNoteKeepsItsKind() throws Exception {
        firm("LinkedIn Notes Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, FATIMA).get("id").asText();

        write(admin, cfo, candidateId, "linkedin", "Accepted the connection request; open to a call next week.");

        assertThat(notes(admin, cfo, candidateId).get(0).get("kind").asText()).isEqualTo("linkedin");
        JsonNode line = timeline(cfo, candidateId, "notes").get("entries").get(0);
        assertThat(line.get("kind").asText()).isEqualTo("NOTE_ADDED");
        assertThat(line.get("details").get("kind").asText()).isEqualTo("LINKEDIN");
    }

    @Test
    @DisplayName("a note for all positions, written from one, is read on every position the person is in")
    void anAllPositionsNoteIsReadOnEvery() throws Exception {
        firm("All Positions Notes Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        JsonNode onCfo = add(cfo, FATIMA);
        String onCredit = add(credit, FATIMA).get("id").asText();

        JsonNode written = writeForAllPositions(admin, cfo, onCfo.get("id").asText(), "meeting",
                "Prefers a listed company.");
        assertThat(written.get("projectId").isNull()).isTrue();
        assertThat(written.get("projectTitle").isNull()).isTrue();

        JsonNode fromCredit = notes(admin, credit, onCredit);
        assertThat(fromCredit).hasSize(1);
        assertThat(fromCredit.get(0).get("id").asText()).isEqualTo(written.get("id").asText());
        assertThat(timeline(credit, onCredit, "notes").get("entries").get(0).get("noteExcerpt").asText())
                .isEqualTo("Prefers a listed company.");
        assertThat(poolNotes(onCfo.get("personId").asText())).hasSize(1);
    }

    @Test
    @DisplayName("another position's note is not found from this one, to change, pin, remove or set a task from")
    void anotherPositionsNoteIsNotFoundHere() throws Exception {
        firm("Foreign Note Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        String onCfo = add(cfo, FATIMA).get("id").asText();
        String onCredit = add(credit, FATIMA).get("id").asText();
        String noteId = write(admin, cfo, onCfo, "general", "Board seat at a bank.").get("id").asText();

        mvc.perform(put(notesUrl(credit, onCredit) + "/" + noteId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"general","body":"Rewritten from another position."}"""))
                .andExpect(status().isNotFound());
        mvc.perform(patch(notesUrl(credit, onCredit) + "/" + noteId + "/pin")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"pinned":true}"""))
                .andExpect(status().isNotFound());
        mvc.perform(delete(notesUrl(credit, onCredit) + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
        String adminId = db.queryForObject("select id::text from app_lm_user where email = ?", String.class,
                "alok@" + domain);
        mvc.perform(post("/api/v1/projects/" + credit + "/candidates/" + onCredit + "/tasks")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Call back","kind":"call","assigneeUserId":"%s","dueAt":"%s","noteId":"%s"}"""
                                .formatted(adminId, Instant.now().plus(2, ChronoUnit.DAYS), noteId)))
                .andExpect(status().isNotFound());

        JsonNode kept = notes(admin, cfo, onCfo);
        assertThat(kept).hasSize(1);
        assertThat(kept.get(0).get("body").asText()).isEqualTo("Board seat at a bank.");
        assertThat(kept.get(0).get("pinned").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a task already pointing at another position's note names none, while its own position's is named")
    void aTaskNamesOnlyANoteItsPositionReads() throws Exception {
        firm("Linked Note Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        String onCfo = add(cfo, FATIMA).get("id").asText();
        String onCredit = add(credit, FATIMA).get("id").asText();
        String noteId = write(admin, cfo, onCfo, "call", "Asked about the bank's board.").get("id").asText();

        String onCfoTask = task(cfo, onCfo, noteId);
        String onCreditTask = task(credit, onCredit, null);
        db.update("update app_lm_task set note_id = ?::uuid where id = ?::uuid", noteId, onCreditTask);

        assertThat(openTask(cfo, onCfo, onCfoTask).at("/note/id").asText()).isEqualTo(noteId);
        assertThat(openTask(credit, onCredit, onCreditTask).get("note").isNull()).isTrue();
    }

    @Test
    @DisplayName("an AI run on one position reads its own notes and those for all positions, never another's")
    void anAiRunReadsWhatItsPositionReads() throws Exception {
        firm("AI Notes Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        String onCfo = add(cfo, FATIMA).get("id").asText();
        String onCredit = add(credit, FATIMA).get("id").asText();
        write(admin, cfo, onCfo, "call", "Only the CFO team reads this.");
        writeForAllPositions(admin, cfo, onCfo, "general", "Every position reads this.");
        write(admin, credit, onCredit, "general", "The credit team's own reading.");
        model.answerWith(AI_BACKGROUND);

        mvc.perform(post("/api/v1/projects/" + credit + "/candidates/" + onCredit + "/background/ai")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());

        assertThat(model.prompts()).isNotEmpty().allSatisfy(sent -> assertThat(sent.getUserMessage().getText())
                .contains("Every position reads this.", "The credit team's own reading.")
                .doesNotContain("Only the CFO team"));
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
        String bothRefused = codeOf(mvc.perform(post("/api/v1/candidates/" + personId + "/notes")
                        .header("Authorization", "Bearer " + sara)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"general","body":"About one position and all.","projectId":"%s",
                                 "allPositions":true}""".formatted(credit)))
                .andExpect(status().isBadRequest())
                .andReturn());
        assertThat(bothRefused).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("a removed note leaves a line saying so and none of its words, and is gone for good once purged")
    void aRemovedNoteLeavesNoWords() throws Exception {
        firm("Removed Note Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, """
                {"fullName":"Noura Al Thani"}""");
        String candidateId = filed.get("id").asText();
        String noteId = write(admin, cfo, candidateId, "call", "Asked for 2.4m AED base, discreetly.")
                .get("id").asText();
        String taskId = task(cfo, candidateId, noteId);

        JsonNode before = timeline(cfo, candidateId, "notes");
        assertThat(before.get("entries").get(0).get("noteExcerpt").asText()).startsWith("Asked for 2.4m");

        mvc.perform(delete(notesUrl(cfo, candidateId) + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        JsonNode after = timeline(cfo, candidateId, "notes").get("entries");
        assertThat(kindsOf(after)).containsExactly("NOTE_REMOVED", "NOTE_ADDED");
        assertThat(after).allSatisfy(line -> assertThat(line.get("noteExcerpt").isNull()).isTrue());
        assertThat(notes(admin, cfo, candidateId)).isEmpty();
        assertThat(poolNotes(filed.get("personId").asText())).isEmpty();
        assertThat(openTask(cfo, candidateId, taskId).get("note").isNull()).isTrue();
        assertThat(db.queryForObject("""
                select count(*) from app_lm_person_activity
                where person_id = ?::uuid and details::text like '%2.4m%'""", Integer.class,
                filed.get("personId").asText())).isZero();

        assertThat(purge.purgeAt(Instant.now())).isZero();
        db.update("update app_lm_person_note set deleted_at = now() - interval '31 days' where id = ?::uuid", noteId);
        assertThat(purge.purgeAt(Instant.now())).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from app_lm_person_note where id = ?::uuid", Integer.class,
                noteId)).isZero();
        assertThat(db.queryForObject("select note_id::text from app_lm_task where id = ?::uuid", String.class,
                taskId)).isNull();
    }

    @Test
    @DisplayName("a removed note is restored by its author or an admin, as it was, with a line saying so")
    void aRemovedNoteIsRestored() throws Exception {
        firm("Restored Note Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, """
                {"fullName":"Hessa Al Suwaidi"}""");
        String candidateId = filed.get("id").asText();
        String sara = researcherOn(cfo);
        String noteId = write(admin, cfo, candidateId, "meeting", "Wants a board seat within two years.")
                .get("id").asText();
        String taskId = task(cfo, candidateId, noteId);
        mvc.perform(delete(notesUrl(cfo, candidateId) + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        String refusal = codeOf(mvc.perform(post(notesUrl(cfo, candidateId) + "/" + noteId + "/restore")
                        .header("Authorization", "Bearer " + sara))
                .andExpect(status().isForbidden())
                .andReturn());
        assertThat(refusal).isEqualTo("PERSON_NOTE_NOT_YOURS");

        JsonNode restored = body(mvc.perform(post(notesUrl(cfo, candidateId) + "/" + noteId + "/restore")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(restored.get("body").asText()).isEqualTo("Wants a board seat within two years.");
        assertThat(restored.get("kind").asText()).isEqualTo("meeting");
        assertThat(notes(admin, cfo, candidateId)).hasSize(1);
        assertThat(openTask(cfo, candidateId, taskId).at("/note/id").asText()).isEqualTo(noteId);
        JsonNode lines = timeline(cfo, candidateId, "notes").get("entries");
        assertThat(kindsOf(lines)).containsExactly("NOTE_RESTORED", "NOTE_REMOVED", "NOTE_ADDED");
        assertThat(lines.get(0).get("noteExcerpt").asText()).startsWith("Wants a board seat");

        mvc.perform(post("/api/v1/candidates/" + filed.get("personId").asText() + "/notes/" + noteId + "/restore")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk());
        assertThat(kindsOf(timeline(cfo, candidateId, "notes").get("entries")))
                .containsExactly("NOTE_RESTORED", "NOTE_REMOVED", "NOTE_ADDED");
    }

    @Test
    @DisplayName("another position's removed note is not found from this one, to restore")
    void anotherPositionsRemovedNoteIsNotRestoredHere() throws Exception {
        firm("Foreign Restore Firm");
        String cfo = mandate("Chief Financial Officer");
        String credit = mandate("Head of Credit Risk");
        String onCfo = add(cfo, FATIMA).get("id").asText();
        String onCredit = add(credit, FATIMA).get("id").asText();
        String noteId = write(admin, cfo, onCfo, "general", "Board seat at a bank.").get("id").asText();
        mvc.perform(delete(notesUrl(cfo, onCfo) + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mvc.perform(post(notesUrl(credit, onCredit) + "/" + noteId + "/restore")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a door's note that was removed is filed again when the door sends it again")
    void aRemovedDoorNoteIsFiledAgain() throws Exception {
        firm("Removed Door Note Firm");
        String cfo = mandate("Chief Financial Officer");
        String update = """
                {"fullName":"James Whitfield","note":"Captured at the Gulf CFO forum."}""";
        String candidateId = add(cfo, update).get("id").asText();
        String noteId = notes(admin, cfo, candidateId).get(0).get("id").asText();
        mvc.perform(delete(notesUrl(cfo, candidateId) + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        mvc.perform(put("/api/v1/projects/" + cfo + "/candidates/" + candidateId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(update))
                .andExpect(status().isOk());

        JsonNode found = notes(admin, cfo, candidateId);
        assertThat(found).hasSize(1);
        String refiled = found.get(0).get("id").asText();
        assertThat(refiled).isNotEqualTo(noteId);

        JsonNode undone = body(mvc.perform(post(notesUrl(cfo, candidateId) + "/" + noteId + "/restore")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(undone.get("id").asText()).isEqualTo(refiled);
        assertThat(notes(admin, cfo, candidateId)).hasSize(1);
        assertThat(kindsOf(timeline(cfo, candidateId, "notes").get("entries"))).doesNotContain("NOTE_RESTORED");
    }

    @Test
    @DisplayName("an Undo brings the note back when only a colleague wrote the same words since")
    void anUndoIsNeverAnsweredByAColleaguesNote() throws Exception {
        firm("Colleague Undo Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, """
                {"fullName":"Reem Al Mazrouei"}""").get("id").asText();
        String sara = researcherOn(cfo);
        String mine = write(admin, cfo, candidateId, "call", "Left a voicemail").get("id").asText();
        mvc.perform(delete(notesUrl(cfo, candidateId) + "/" + mine).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        String hers = write(sara, cfo, candidateId, "general", "Left a voicemail").get("id").asText();

        JsonNode undone = body(mvc.perform(post(notesUrl(cfo, candidateId) + "/" + mine + "/restore")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(undone.get("id").asText()).isEqualTo(mine);
        List<String> ids = new ArrayList<>();
        notes(admin, cfo, candidateId).forEach(note -> ids.add(note.get("id").asText()));
        assertThat(ids).containsExactlyInAnyOrder(mine, hers);
        assertThat(kindsOf(timeline(cfo, candidateId, "notes").get("entries"))).contains("NOTE_RESTORED");
    }

    @Test
    @DisplayName("two Undos of one delete at once restore it once")
    void twoUndosAtOnceRestoreOnce() throws Exception {
        firm("Double Undo Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, """
                {"fullName":"Mariam Al Hashimi"}""").get("id").asText();
        String noteId = write(admin, cfo, candidateId, "general", "Open to a move in Q2.").get("id").asText();
        mvc.perform(delete(notesUrl(cfo, candidateId) + "/" + noteId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> undos = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                undos.add(pool.submit(() -> mvc.perform(post(notesUrl(cfo, candidateId) + "/" + noteId + "/restore")
                                .header("Authorization", "Bearer " + admin))
                        .andReturn().getResponse().getStatus()));
            }
            for (Future<Integer> undo : undos) {
                assertThat(undo.get()).isEqualTo(200);
            }
        } finally {
            pool.shutdown();
        }

        assertThat(kindsOf(timeline(cfo, candidateId, "notes").get("entries")))
                .containsExactly("NOTE_RESTORED", "NOTE_REMOVED", "NOTE_ADDED");
    }

    @Test
    @DisplayName("a note keeps its format: Markdown as written, read back as plain words on the timeline")
    void aFormattedNoteKeepsItsFormat() throws Exception {
        firm("Formatted Note Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, """
                {"fullName":"Reem Al Hashimi","note":"**Captured** at the forum."}""").get("id").asText();

        JsonNode written = body(mvc.perform(post(notesUrl(cfo, candidateId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"call","body":"## Call\\n\\n**Wants** ++Dubai++\\n\\n- [ ] CV","format":"markdown"}"""))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(written.get("format").asText()).isEqualTo("markdown");
        assertThat(written.get("body").asText()).isEqualTo("## Call\n\n**Wants** ++Dubai++\n\n- [ ] CV");

        JsonNode found = notes(admin, cfo, candidateId);
        JsonNode fromDoor = found.get(1);
        assertThat(fromDoor.get("body").asText()).isEqualTo("**Captured** at the forum.");
        assertThat(fromDoor.get("format").asText()).isEqualTo("plain");
        assertThat(timeline(cfo, candidateId, "notes").get("entries").get(0).get("noteExcerpt").asText())
                .isEqualTo("Call Wants Dubai - [ ] CV");

        String refusal = codeOf(mvc.perform(post(notesUrl(cfo, candidateId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("kind", "general", "body", "x",
                                "format", "html"))))
                .andExpect(status().isBadRequest())
                .andReturn());
        assertThat(refusal).isEqualTo("VALIDATION_FAILED");
        mvc.perform(post(notesUrl(cfo, candidateId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("kind", "general",
                                "body", "x".repeat(10000), "format", "markdown"))))
                .andExpect(status().isCreated());
        mvc.perform(post(notesUrl(cfo, candidateId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("kind", "general",
                                "body", "x".repeat(10001), "format", "markdown"))))
                .andExpect(status().isBadRequest());
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
    @DisplayName("an edited note keeps one line, read as it stands and marked edited; the audit trail keeps the edit")
    void anEditLeavesOneLineMarkedEdited() throws Exception {
        firm("Edited Note Firm");
        String cfo = mandate("Chief Financial Officer");
        JsonNode filed = add(cfo, FATIMA);
        String candidateId = filed.get("id").asText();
        String personId = filed.get("personId").asText();
        String noteId = write(admin, cfo, candidateId, "general", "Not interested - locaton").get("id").asText();
        assertThat(timeline(cfo, candidateId, "notes").get("entries").get(0).get("noteEditedAt").isNull()).isTrue();

        mvc.perform(put(notesUrl(cfo, candidateId) + "/" + noteId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"kind":"call","body":"Not interested - location"}"""))
                .andExpect(status().isOk());
        // A line as the previous release wrote it for an edit: still on the table, never read back.
        db.update("""
                insert into app_lm_person_activity (workspace_id, person_id, project_id, project_title, kind,
                                                    occurred_at, details)
                select p.workspace_id, ?::uuid, p.id, p.position_title, 'NOTE_EDITED', now(), ?::jsonb
                from app_lm_project p where p.id = ?::uuid
                """, personId, "{\"noteId\":\"" + noteId + "\",\"kind\":\"CALL\"}", cfo);

        // The Candidates page's person timeline carries no notes (they stay with the Notes tab and each position).
        assertThat(kindsOf(workspaceRead("/api/v1/candidates/" + personId + "/timeline", null)))
                .isNotEmpty().noneMatch(kind -> kind.startsWith("NOTE_"));
        assertThat(workspaceRead("/api/v1/candidates/" + personId + "/timeline", "notes")).isEmpty();
        List<JsonNode> reads = List.of(
                timeline(cfo, candidateId, null).get("entries"),
                timeline(cfo, candidateId, "notes").get("entries"),
                workspaceRead("/api/v1/candidates/activity", "notes"));
        for (JsonNode entries : reads) {
            List<JsonNode> noteLines = new ArrayList<>();
            entries.forEach(entry -> {
                if (entry.get("kind").asText().startsWith("NOTE_")) {
                    noteLines.add(entry);
                }
            });
            assertThat(noteLines).hasSize(1);
            JsonNode line = noteLines.getFirst();
            assertThat(line.get("kind").asText()).isEqualTo("NOTE_ADDED");
            assertThat(line.get("noteExcerpt").asText()).isEqualTo("Not interested - location");
            assertThat(line.get("details").get("kind").asText()).isEqualTo("CALL");
            assertThat(line.get("noteEditedAt").isNull()).isFalse();
            assertThat(line.get("noteEditedByName").asText()).isEqualTo("Alok Kumar");
        }

        JsonNode row = body(mvc.perform(get("/api/v1/candidates").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("people").get(0);
        assertThat(row.get("lastActivity").get("kind").asText()).isEqualTo("NOTE_ADDED");
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event
                where event_type = 'PERSON_NOTE_EDITED' and metadata ->> 'noteId' = ?""", Integer.class, noteId))
                .isEqualTo(1);
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
        assertThat(kindsOf(all)).containsExactly("NOTE_ADDED", "STATUS_CHANGED", "ADDED_TO_POOL");
        assertThat(all.get(1).get("details").get("to").asText()).isEqualTo("contacted");
        String onCredit = db.queryForObject("""
                select c.id::text from app_lm_project_candidate c where c.project_id = ?::uuid""", String.class, credit);
        JsonNode fromCredit = timeline(credit, onCredit, null).get("entries");
        assertThat(kindsOf(fromCredit)).containsExactly("MAPPED");
        assertThat(fromCredit.get(0).get("actorName").asText()).isEqualTo("Sara Al-Mansour");
        assertThat(fromCredit.get(0).get("projectTitle").asText()).isEqualTo("Head of Credit Risk");

        JsonNode firstPage = body(mvc.perform(get(timelineUrl(cfo, candidateId)).param("limit", "2")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        JsonNode secondPage = body(mvc.perform(get(timelineUrl(cfo, candidateId)).param("limit", "2")
                        .param("before", firstPage.get("nextCursor").asText())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(kindsOf(secondPage.get("entries"))).containsExactly("ADDED_TO_POOL");
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

    @AfterEach
    void resetTheModel() {
        model.reset();
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

    private JsonNode writeForAllPositions(String token, String projectId, String candidateId, String kind,
                                          String text) throws Exception {
        return body(mvc.perform(post(notesUrl(projectId, candidateId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(java.util.Map.of("kind", kind, "body", text,
                                "allPositions", true))))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private String task(String projectId, String candidateId, String noteId) throws Exception {
        String adminId = db.queryForObject("select id::text from app_lm_user where email = ?", String.class,
                "alok@" + domain);
        String linked = noteId == null ? "" : ",\"noteId\":\"" + noteId + "\"";
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/tasks")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Call back","kind":"call","assigneeUserId":"%s","dueAt":"%s"%s}"""
                                .formatted(adminId, Instant.now().plus(2, ChronoUnit.DAYS), linked)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private JsonNode openTask(String projectId, String candidateId, String taskId) throws Exception {
        JsonNode open = body(mvc.perform(get("/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/tasks")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("open");
        for (JsonNode task : open) {
            if (task.get("id").asText().equals(taskId)) {
                return task;
            }
        }
        throw new AssertionError("no open task " + taskId);
    }

    private JsonNode poolNotes(String personId) throws Exception {
        return body(mvc.perform(get("/api/v1/candidates/" + personId + "/notes")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
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

    private JsonNode workspaceRead(String url, String group) throws Exception {
        var request = get(url).header("Authorization", "Bearer " + admin);
        if (group != null) {
            request = request.param("group", group);
        }
        return body(mvc.perform(request).andExpect(status().isOk()).andReturn()).get("entries");
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
