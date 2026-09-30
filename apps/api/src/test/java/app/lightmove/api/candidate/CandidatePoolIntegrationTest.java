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
import app.lightmove.api.RecordingProfileEnricher;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * One person per workspace, mapped to many mandates (V91). Whichever door files an executive, a second
 * mandate meeting someone the workspace already knows maps that person rather than starting a second
 * record — by LinkedIn profile or by email, never by name alone, and never across workspaces. What is
 * true of the person is shared; each mandate's status and note stay its own; and every change leaves a
 * line saying who made it, on which mandate.
 */
@IntegrationTest
class CandidatePoolIntegrationTest extends FlowTestSupport {

    private static final EnrichedProfile RESEARCH = new EnrichedProfile(
            "Group CFO", "Finance leader across GCC retail.", "Al Rawabi Dairy", null, null,
            "Dubai", "United Arab Emirates",
            List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2021 – Present", null)),
            List.of(), List.of("Financial Planning"), List.of("English", "Arabic"), null,
            EnrichmentVendor.BRIGHTDATA);

    @Autowired private JdbcTemplate db;
    @Autowired private RecordingProfileEnricher enricher;
    @Autowired private StubChatModel model;

    private String adminToken;

    @BeforeEach
    void resetTheProvider() {
        enricher.clear();
    }

    @AfterEach
    void resetTheModel() {
        model.reset();
    }

    @Test
    @DisplayName("the same profile on a second mandate is the same person, filled in rather than overwritten")
    void aSecondMandateMapsTheSamePerson() throws Exception {
        String first = mandate("Shared Person Firm", "Chief Financial Officer");
        String second = mandateInSameWorkspace("Head of Credit Risk");

        JsonNode onFirst = add(first, """
                {"fullName":"Fatima Al Mazrouei","title":"Group Finance Director",
                 "linkedinUrl":"https://www.linkedin.com/in/fatima-almazrouei",
                 "nationality":"Emirati","status":"engaged","note":"Open to a group CFO move."}""");
        JsonNode onSecond = add(second, """
                {"fullName":"Fatima Al Mazrouei","title":"CFO",
                 "linkedinUrl":"https://linkedin.com/in/Fatima-AlMazrouei/",
                 "email":"fatima@alnaboodah.example","yearsExperience":16,"note":"Seen for credit risk."}""");

        assertThat(onSecond.get("personId").asText()).isEqualTo(onFirst.get("personId").asText());
        assertThat(onSecond.get("id").asText()).isNotEqualTo(onFirst.get("id").asText());
        // What the first mandate recorded stands; what nobody recorded is filled in.
        assertThat(onSecond.get("title").asText()).isEqualTo("Group Finance Director");
        assertThat(onSecond.get("nationality").asText()).isEqualTo("Emirati");
        assertThat(onSecond.get("yearsExperience").asInt()).isEqualTo(16);
        // Each mandate's decision is its own.
        assertThat(onSecond.get("status").asText()).isEqualTo("identified");
        assertThat(onSecond.get("note").asText()).isEqualTo("Seen for credit risk.");

        JsonNode firstNow = read(first, onFirst.get("id").asText());
        assertThat(firstNow.get("status").asText()).isEqualTo("engaged");
        assertThat(firstNow.get("note").asText()).isEqualTo("Open to a group CFO move.");
        // The address the second mandate brought is on the one ledger both read.
        assertThat(firstNow.get("contacts").get("emails").get(0).get("address").asText())
                .isEqualTo("fatima@alnaboodah.example");
        assertThat(firstNow.get("yearsExperience").asInt()).isEqualTo(16);
    }

    @Test
    @DisplayName("an edit made through one mandate is what every other mandate reads")
    void anEditIsSharedButTheStatusIsNot() throws Exception {
        String first = mandate("Shared Edit Firm", "Chief Financial Officer");
        String second = mandateInSameWorkspace("Head of Credit Risk");
        String onFirst = add(first, """
                {"fullName":"Khalid Al Harbi","linkedinUrl":"https://www.linkedin.com/in/khalid-alharbi"}""")
                .get("id").asText();
        String onSecond = add(second, """
                {"fullName":"Khalid Al Harbi","linkedinUrl":"https://www.linkedin.com/in/khalid-alharbi"}""")
                .get("id").asText();

        mvc.perform(put(candidatesUrl(second) + "/" + onSecond)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Khalid Al Harbi","title":"Chief Financial Officer",
                                 "linkedinUrl":"https://www.linkedin.com/in/khalid-alharbi",
                                 "status":"identified","compensation":{"currency":"AED","baseSalary":1700000}}"""))
                .andExpect(status().isOk());
        mvc.perform(patch(candidatesUrl(second) + "/" + onSecond)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"interested"}"""))
                .andExpect(status().isOk());

        JsonNode firstNow = read(first, onFirst);
        assertThat(firstNow.get("title").asText()).isEqualTo("Chief Financial Officer");
        assertThat(firstNow.get("compensation").get("baseSalary").asLong()).isEqualTo(1_700_000L);
        assertThat(firstNow.get("status").asText()).isEqualTo("identified");
    }

    @Test
    @DisplayName("an email address finds the person a second mandate spelled differently")
    void anEmailFindsThePerson() throws Exception {
        String first = mandate("Email Match Firm", "Chief Financial Officer");
        String second = mandateInSameWorkspace("Head of Credit Risk");

        JsonNode onFirst = add(first, """
                {"fullName":"Hind Al Suwaidi","email":"Hind.AlSuwaidi@chalhoub.example"}""");
        JsonNode onSecond = add(second, """
                {"fullName":"Hind Suwaidi","email":"hind.alsuwaidi@chalhoub.example"}""");

        assertThat(onSecond.get("personId").asText()).isEqualTo(onFirst.get("personId").asText());
        assertThat(onSecond.get("fullName").asText()).isEqualTo("Hind Al Suwaidi");
    }

    @Test
    @DisplayName("a name alone never folds two people into one")
    void aNameAloneIsNotAMatch() throws Exception {
        String first = mandate("Namesake Firm", "Chief Financial Officer");
        String second = mandateInSameWorkspace("Head of Credit Risk");

        JsonNode onFirst = add(first, """
                {"fullName":"Omar Farouk","employerName":"Trading Enterprises"}""");
        JsonNode onSecond = add(second, """
                {"fullName":"Omar Farouk","employerName":"Trading Enterprises"}""");

        assertThat(onSecond.get("personId").asText()).isNotEqualTo(onFirst.get("personId").asText());
    }

    @Test
    @DisplayName("a mandate holds a person once, however the second filing spells the profile")
    void aMandateHoldsAPersonOnce() throws Exception {
        String projectId = mandate("Held Once Firm", "Chief Financial Officer");
        add(projectId, """
                {"fullName":"Noura Al Thani","linkedinUrl":"https://www.linkedin.com/in/noura-althani"}""");

        String code = codeOf(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"N. Al Thani","linkedinUrl":"https://www.linkedin.com/in/Noura-AlThani/"}"""))
                .andExpect(status().isConflict())
                .andReturn());
        assertThat(code).isEqualTo("CANDIDATE_ALREADY_MAPPED");
    }

    @Test
    @DisplayName("another workspace never finds this workspace's person")
    void anotherWorkspaceHasItsOwnPerson() throws Exception {
        String ours = mandate("Our Search Firm", "Chief Financial Officer");
        String ourPerson = add(ours, """
                {"fullName":"Sarah Collins","linkedinUrl":"https://www.linkedin.com/in/sarahcollinscfo"}""")
                .get("personId").asText();

        String theirs = mandate("Their Search Firm", "Chief Financial Officer", "bea");
        String theirPerson = add(theirs, """
                {"fullName":"Sarah Collins","linkedinUrl":"https://www.linkedin.com/in/sarahcollinscfo"}""")
                .get("personId").asText();

        assertThat(theirPerson).isNotEqualTo(ourPerson);
        assertThat(db.queryForObject("select count(distinct workspace_id) from app_lm_person where id in (?::uuid, ?::uuid)",
                Integer.class, ourPerson, theirPerson)).isEqualTo(2);
    }

    @Test
    @DisplayName("removing someone from a mandate keeps the person; filing them again finds them")
    void removingFromAMandateKeepsThePerson() throws Exception {
        String projectId = mandate("Keeps Person Firm", "Chief Financial Officer");
        JsonNode filed = add(projectId, """
                {"fullName":"James Whitfield","linkedinUrl":"https://www.linkedin.com/in/jameswhitfield",
                 "email":"james@alrostamani.example"}""");

        mvc.perform(delete(candidatesUrl(projectId) + "/" + filed.get("id").asText())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        JsonNode again = add(projectId, """
                {"fullName":"James Whitfield","linkedinUrl":"https://www.linkedin.com/in/jameswhitfield"}""");

        assertThat(again.get("personId").asText()).isEqualTo(filed.get("personId").asText());
        assertThat(again.get("contacts").get("emails").get(0).get("address").asText())
                .isEqualTo("james@alrostamani.example");
    }

    @Test
    @DisplayName("a plugin capture of someone already researched costs no second research call")
    void aResearchedPersonIsNotResearchedAgain() throws Exception {
        String first = mandate("Researched Once Firm", "Chief Financial Officer");
        String second = mandateInSameWorkspace("Head of Credit Risk");
        enricher.answerWith(RESEARCH);

        capture(first, "sample-profile");
        JsonNode onSecond = capture(second, "sample-profile");

        assertThat(enricher.fetchedUrls()).hasSize(1);
        assertThat(onSecond.get("enrichedAt").isNull()).isFalse();
        assertThat(onSecond.get("summary").asText()).isEqualTo("Finance leader across GCC retail.");
    }

    @Test
    @DisplayName("every change leaves a line naming who made it and on which mandate")
    void everyChangeIsRecorded() throws Exception {
        String first = mandate("Recorded Firm", "Chief Financial Officer");
        String second = mandateInSameWorkspace("Head of Credit Risk");
        JsonNode onFirst = add(first, """
                {"fullName":"Rajesh Menon","linkedinUrl":"https://www.linkedin.com/in/rajesh-menon"}""");
        String onSecond = add(second, """
                {"fullName":"Rajesh Menon","linkedinUrl":"https://www.linkedin.com/in/rajesh-menon"}""")
                .get("id").asText();
        mvc.perform(patch(candidatesUrl(second) + "/" + onSecond)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"contacted"}"""))
                .andExpect(status().isOk());
        mvc.perform(put(candidatesUrl(second) + "/" + onSecond)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Rajesh Menon","title":"VP Finance","status":"contacted",
                                 "linkedinUrl":"https://www.linkedin.com/in/rajesh-menon"}"""))
                .andExpect(status().isOk());
        mvc.perform(delete(candidatesUrl(first) + "/" + onFirst.get("id").asText())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());

        String admin = db.queryForObject("select id::text from app_lm_user where email = ?", String.class,
                "alok@" + domain);
        List<Map<String, Object>> lines = db.queryForList("""
                select kind, project_title, actor_user_id::text as actor, details::text as details
                from app_lm_person_activity where person_id = ?::uuid order by id""",
                onFirst.get("personId").asText());

        assertThat(lines).extracting(line -> line.get("kind")).containsExactly(
                "ADDED_TO_POOL", "MAPPED", "STATUS_CHANGED", "PROFILE_EDITED", "UNMAPPED");
        assertThat(lines).extracting(line -> line.get("project_title")).containsExactly(
                "Chief Financial Officer", "Head of Credit Risk", "Head of Credit Risk",
                "Head of Credit Risk", "Chief Financial Officer");
        assertThat(lines).extracting(line -> line.get("actor")).containsOnly(admin);
        assertThat((String) lines.get(2).get("details")).contains("\"from\": \"identified\"", "\"to\": \"contacted\"");
    }

    private JsonNode add(String projectId, String body) throws Exception {
        return body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private JsonNode capture(String projectId, String slug) throws Exception {
        String id = add(projectId, """
                {"fullName":"Sample Person","source":"extension",
                 "linkedinUrl":"https://www.linkedin.com/in/%s"}""".formatted(slug)).get("id").asText();
        return read(projectId, id);
    }

    private JsonNode read(String projectId, String candidateId) throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static String candidatesUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/candidates";
    }

    private String mandate(String firmName, String positionTitle) throws Exception {
        return mandate(firmName, positionTitle, "alok");
    }

    /** A workspace founded by {@code founder}, who is then the one signed in. */
    private String mandate(String firmName, String positionTitle, String founder) throws Exception {
        String address = founder + "@" + domain;
        createWorkspace(verifiedUser(founder, address), firmName);
        adminToken = login(address);
        return mandateInSameWorkspace(positionTitle);
    }

    private String mandateInSameWorkspace(String positionTitle) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s Unit"}""".formatted(positionTitle)))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, positionTitle)))
                .andReturn()).get("id").asText();
    }
}
