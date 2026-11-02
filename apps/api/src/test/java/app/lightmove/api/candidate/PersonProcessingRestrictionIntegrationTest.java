package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubChatModel;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** #920: restriction of processing — what is on file stays readable, and nothing uses it until it is lifted. */
@IntegrationTest
class PersonProcessingRestrictionIntegrationTest extends FlowTestSupport {

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired StubChatModel chat;

    private String admin;
    private String position;
    private String candidate;
    private String person;

    @BeforeEach
    void setUp() throws Exception {
        chat.reset();
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), "Restricting Firm");
        admin = login(address);
        position = mandate("Chief Financial Officer");
        JsonNode added = body(mvc.perform(post("/api/v1/projects/" + position + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Noor Haddad","linkedinUrl":"https://www.linkedin.com/in/noor-haddad/",
                                 "emails":[{"value":"noor@example.com"}]}"""))
                .andExpect(status().isCreated()).andReturn());
        candidate = added.get("id").asText();
        person = added.get("personId").asText();
    }

    @Test
    @DisplayName("a restriction needs a reason, reads back with who set it, is a timeline line and an audit event")
    void restrictionIsRecorded() throws Exception {
        assertThat(body(restrict("""
                {"restricted":true,"reason":"  "}""").andExpect(status().isBadRequest()).andReturn())
                .get("fieldErrors").has("reason")).isTrue();

        JsonNode record = body(restrict("""
                {"restricted":true,"reason":"Contests the accuracy of what we hold."}""")
                .andExpect(status().isOk()).andReturn());
        assertThat(record.at("/processingRestriction/reason").asText()).isEqualTo("Contests the accuracy of what we hold.");
        assertThat(record.at("/processingRestriction/restrictedByName").asText()).isEqualTo("Alok Kumar");
        assertThat(record.at("/processingRestriction/restrictedAt").isNull()).isFalse();

        restrict("""
                {"restricted":true,"reason":"Again."}""").andExpect(status().isOk());
        assertThat(kinds()).containsOnlyOnce("PROCESSING_RESTRICTED");
        assertThat(db.queryForObject("select processing_restricted_reason from app_lm_person where id = ?::uuid",
                String.class, person)).isEqualTo("Contests the accuracy of what we hold.");

        JsonNode lifted = body(restrict("""
                {"restricted":false}""").andExpect(status().isOk()).andReturn());
        assertThat(lifted.get("processingRestriction").isNull()).isTrue();
        assertThat(kinds()).contains("PROCESSING_RESTRICTION_LIFTED");
        assertThat(db.queryForList("select event_type from app_lm_audit_event where target_id = ?"
                + " and event_type like 'PERSON_PROCESSING_%' order by id", String.class, person))
                .containsExactly("PERSON_PROCESSING_RESTRICTED", "PERSON_PROCESSING_RESTRICTION_LIFTED");
        assertThat(db.queryForObject("select processing_restricted_reason is null and processing_restricted_at is null"
                + " from app_lm_person where id = ?::uuid", Boolean.class, person)).isTrue();
    }

    @Test
    @DisplayName("restricting may also suppress the person as an objection, which lifting the restriction leaves")
    void restrictingMaySuppress() throws Exception {
        restrict("""
                {"restricted":true,"reason":"Objected to being approached.","suppress":true}""")
                .andExpect(status().isOk());
        assertThat(db.queryForList("select kind || ':' || reason from app_lm_workspace_suppression s"
                + " join app_lm_person p on p.workspace_id = s.workspace_id where p.id = ?::uuid", String.class, person))
                .containsExactlyInAnyOrder("PROFILE_SLUG:OBJECTED", "EMAIL:OBJECTED");

        restrict("""
                {"restricted":false}""").andExpect(status().isOk());
        assertThat(db.queryForObject("select count(*) from app_lm_workspace_suppression s join app_lm_person p"
                + " on p.workspace_id = s.workspace_id where p.id = ?::uuid", Long.class, person)).isEqualTo(2);
    }

    @Test
    @DisplayName("a restricted person is mapped onto no further position, by any door, until it is lifted")
    void noFurtherPosition() throws Exception {
        String second = mandate("Chief Operating Officer");
        restrict("""
                {"restricted":true,"reason":"Asked what we hold about them."}""").andExpect(status().isOk());

        assertThat(codeOf(mvc.perform(post("/api/v1/projects/" + second + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Noor Haddad","source":"extension",
                                 "linkedinUrl":"https://www.linkedin.com/in/Noor-Haddad"}"""))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("PERSON_PROCESSING_RESTRICTED");

        JsonNode mapped = body(mvc.perform(post("/api/v1/candidates/bulk/position")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","personIds":["%s"]}""".formatted(second, person)))
                .andExpect(status().isOk()).andReturn());
        assertThat(mapped.get("added").asInt()).isZero();
        assertThat(mapped.get("restricted").asInt()).isEqualTo(1);
        assertThat(db.queryForObject("select count(*) from app_lm_project_candidate where person_id = ?::uuid",
                Long.class, person)).isEqualTo(1);

        restrict("""
                {"restricted":false}""").andExpect(status().isOk());
        assertThat(body(mvc.perform(post("/api/v1/candidates/bulk/position")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"projectId":"%s","personIds":["%s"]}""".formatted(second, person)))
                .andExpect(status().isOk()).andReturn()).get("added").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("no AI run reads a restricted person, and nothing is spent on asking")
    void noModelReadsThem() throws Exception {
        restrict("""
                {"restricted":true,"reason":"Asked what we hold about them."}""").andExpect(status().isOk());

        for (String press : new String[] {"/background/ai", "/assessment/ai"}) {
            assertThat(codeOf(mvc.perform(post("/api/v1/projects/" + position + "/candidates/" + candidate + press)
                            .header("Authorization", "Bearer " + admin))
                    .andExpect(status().isConflict()).andReturn())).isEqualTo("PERSON_PROCESSING_RESTRICTED");
        }
        assertThat(chat.prompts()).isEmpty();

        JsonNode read = body(mvc.perform(get("/api/v1/projects/" + position + "/candidates/" + candidate)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk()).andReturn());
        assertThat(read.get("fullName").asText()).isEqualTo("Noor Haddad");
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private ResultActions restrict(String json) throws Exception {
        return mvc.perform(put("/api/v1/candidates/" + person + "/processing-restriction")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private List<String> kinds() {
        return db.queryForList("select kind from app_lm_person_activity where person_id = ?::uuid", String.class,
                person);
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
}
