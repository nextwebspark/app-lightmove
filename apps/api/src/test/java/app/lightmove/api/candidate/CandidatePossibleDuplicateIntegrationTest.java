package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** Phase 4b-1: the possible-duplicate question a hand-typed add asks, and the answers it accepts. */
@IntegrationTest
class CandidatePossibleDuplicateIntegrationTest extends FlowTestSupport {

    private static final String LAYLA_AT_ALDAR = """
            {"fullName":"Layla Nasser","title":"Director of Leasing","employerName":"Aldar Properties"}""";

    @Autowired JdbcTemplate db;

    private String admin;

    @Test
    @DisplayName("a namesake at the same employer is asked about, then mapped or added as new as answered")
    void aNamesakeIsAskedAbout() throws Exception {
        firm("Duplicate Question Firm");
        String leasing = mandate("Leasing Director");
        String coo = mandate("Chief Operating Officer");
        String strategy = mandate("Head of Strategy");
        String layla = body(add(leasing, LAYLA_AT_ALDAR).andExpect(status().isCreated()).andReturn())
                .get("personId").asText();

        JsonNode asked = body(add(coo, """
                {"fullName":"layla nasser","employerName":"ALDAR PROPERTIES"}""")
                .andExpect(status().isConflict())
                .andReturn());
        assertThat(asked.get("code").asText()).isEqualTo("CANDIDATE_POSSIBLE_DUPLICATE");
        assertThat(textsOf(asked.get("personIds"))).containsExactly(layla);
        assertThat(rowsOf(coo)).isZero();

        JsonNode mapped = body(add(coo, """
                {"fullName":"Layla Nasser","employerName":"Aldar Properties","note":"Met at Cityscape",
                 "existingPersonId":"%s"}""".formatted(layla))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(mapped.get("personId").asText()).isEqualTo(layla);
        assertThat(db.queryForObject("select count(*) from app_lm_person_note where person_id = ?::uuid",
                Long.class, layla)).isEqualTo(1);

        JsonNode separate = body(add(strategy, """
                {"fullName":"Layla Nasser","employerName":"Aldar Properties","addAsNewPerson":true}""")
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(separate.get("personId").asText()).isNotEqualTo(layla);
    }

    @Test
    @DisplayName("only the hand-typed add asks: another employer, a key match, a capture and an import never do")
    void onlyTheHandTypedAddAsks() throws Exception {
        firm("Duplicate Doors Firm");
        String first = mandate("Chief Financial Officer");
        String second = mandate("Group Treasurer");
        String third = mandate("Finance Director");
        String fourth = mandate("Head of Tax");
        String fifth = mandate("Head of Audit");
        add(first, """
                {"fullName":"Omar Haddad","employerName":"Emaar","emails":[{"value":"omar@emaar.example"}]}""")
                .andExpect(status().isCreated());

        add(second, """
                {"fullName":"Omar Haddad","employerName":"Mubadala"}""").andExpect(status().isCreated());
        JsonNode byEmail = body(add(third, """
                {"fullName":"Omar Haddad","employerName":"Emaar","emails":[{"value":"OMAR@emaar.example"}]}""")
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(db.queryForObject("select count(distinct person_id) from app_lm_project_candidate c "
                + "join app_lm_project p on p.id = c.project_id where p.id in (?::uuid, ?::uuid)",
                Long.class, first, third)).isEqualTo(1);
        assertThat(byEmail.get("personId").isNull()).isFalse();

        add(fourth, """
                {"fullName":"Omar Haddad","employerName":"Emaar","source":"extension"}""")
                .andExpect(status().isCreated());
        add(fifth, """
                {"fullName":"Omar Haddad","employerName":"Emaar","source":"csv"}""")
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("the answer must name one of the namesakes the question named")
    void theAnswerMustBeANamesake() throws Exception {
        firm("Duplicate Stranger Firm");
        String leasing = mandate("Leasing Director");
        String cfo = mandate("Chief Financial Officer");
        add(leasing, LAYLA_AT_ALDAR).andExpect(status().isCreated());
        String omar = body(add(leasing, """
                {"fullName":"Omar Saleh","employerName":"Aldar Properties"}""")
                .andExpect(status().isCreated()).andReturn()).get("personId").asText();

        add(cfo, """
                {"fullName":"Layla Nasser","employerName":"Aldar Properties","existingPersonId":"%s"}"""
                .formatted(UUID.randomUUID())).andExpect(status().isNotFound());
        add(cfo, """
                {"fullName":"Layla Nasser","employerName":"Aldar Properties","existingPersonId":"%s"}"""
                .formatted(omar)).andExpect(status().isNotFound());
        assertThat(rowsOf(cfo)).isZero();
    }

    @Test
    @DisplayName("the keys still decide: an answer naming someone other than whose email was typed is refused")
    void theKeysOverruleTheAnswer() throws Exception {
        firm("Duplicate Keys Firm");
        String leasing = mandate("Leasing Director");
        String cfo = mandate("Chief Financial Officer");
        String layla = body(add(leasing, LAYLA_AT_ALDAR).andExpect(status().isCreated()).andReturn())
                .get("personId").asText();
        String omar = body(add(leasing, """
                {"fullName":"Omar Saleh","employerName":"Emaar","emails":[{"value":"omar@emaar.example"}]}""")
                .andExpect(status().isCreated()).andReturn()).get("personId").asText();

        JsonNode refused = body(add(cfo, """
                {"fullName":"Layla Nasser","employerName":"Aldar Properties",
                 "emails":[{"value":"omar@emaar.example"}],"existingPersonId":"%s"}""".formatted(layla))
                .andExpect(status().isConflict())
                .andReturn());
        assertThat(refused.get("code").asText()).isEqualTo("CANDIDATE_KEYS_NAME_ANOTHER");
        assertThat(db.queryForObject("select count(*) from app_lm_person_contact where person_id = ?::uuid",
                Long.class, layla)).isZero();

        JsonNode agreed = body(add(cfo, """
                {"fullName":"Omar Saleh","employerName":"Emaar","emails":[{"value":"omar@emaar.example"}],
                 "existingPersonId":"%s"}""".formatted(omar))
                .andExpect(status().isCreated())
                .andReturn());
        assertThat(agreed.get("personId").asText()).isEqualTo(omar);
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

    private ResultActions add(String projectId, String json) throws Exception {
        return mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private long rowsOf(String projectId) {
        return db.queryForObject("select count(*) from app_lm_project_candidate where project_id = ?::uuid",
                Long.class, projectId);
    }

    private static List<String> textsOf(JsonNode array) {
        List<String> texts = new ArrayList<>();
        array.forEach(item -> texts.add(item.asText()));
        return texts;
    }
}
