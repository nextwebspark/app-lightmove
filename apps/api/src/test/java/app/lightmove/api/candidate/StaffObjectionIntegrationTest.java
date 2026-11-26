package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.time.Duration;
import java.util.List;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** B16 (#1489): with data protection on, the team's Do not contact is the person's objection, as their stop link is. */
@IntegrationTest
class StaffObjectionIntegrationTest extends FlowTestSupport {

    private static final String RECORDED_BY_TEAM = "Objected when contacted, recorded by the team";

    @Autowired @FixtureJdbc JdbcTemplate db;

    private String admin;
    private String colleague;
    private String workspace;
    private String person;

    @BeforeEach
    void setUp() throws Exception {
        String address = "alok@" + domain;
        workspace = createWorkspace(verifiedUser("Alok Kumar", address), "Objecting Firm");
        admin = login(address);
        inviteAndAccept(admin, "Sam Lee", "sam@" + domain, "ADMIN");
        colleague = login("sam@" + domain);
        String position = mandate("Chief Financial Officer");
        person = body(call(admin, post("/api/v1/projects/" + position + "/candidates"), """
                {"fullName":"Noor Haddad","linkedinUrl":"https://www.linkedin.com/in/noor-haddad/",
                 "emails":[{"value":"noor@example.com"}]}""")
                .andExpect(status().isCreated()).andReturn()).get("personId").asText();
    }

    @Test
    @DisplayName("marking do not contact suppresses them, is recorded once with its actor, and tells the other admins")
    void markingRecordsTheObjection() throws Exception {
        mark(colleague, true).andExpect(status().isOk());
        mark(colleague, true).andExpect(status().isOk());

        assertThat(db.queryForMap("select do_not_contact, do_not_contact_reason from app_lm_person where id = ?::uuid",
                person)).containsEntry("do_not_contact", true).containsEntry("do_not_contact_reason", RECORDED_BY_TEAM);
        assertThat(db.queryForList("select reason from app_lm_workspace_suppression where workspace_id = ?::uuid",
                String.class, workspace)).containsOnly("OBJECTED").hasSize(2);
        assertThat(activityKinds()).containsOnlyOnce("OBJECTION_RECORDED");
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event e join app_lm_user u on u.id = e.actor_user_id
                where e.event_type = 'PERSON_OBJECTION_RECORDED' and u.email = ?""", Long.class, "sam@" + domain))
                .isOne();
        Awaitility.await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> assertThat(notifiedEmails())
                .containsExactly("alok@" + domain));
    }

    @Test
    @DisplayName("someone already marked for the team's own reason is marked again as objecting")
    void anEarlierReasonGivesWayToTheObjection() throws Exception {
        db.update("update app_lm_person set do_not_contact = true, do_not_contact_reason = 'Works at the client',"
                + " do_not_contact_set_at = now() where id = ?::uuid", person);

        mark(admin, true).andExpect(status().isOk());

        assertThat(db.queryForObject("select do_not_contact_reason from app_lm_person where id = ?::uuid",
                String.class, person)).isEqualTo(RECORDED_BY_TEAM);
        assertThat(activityKinds()).containsOnlyOnce("OBJECTION_RECORDED");
    }

    @Test
    @DisplayName("clearing it is refused while the suppression list holds them, and allowed once an admin lifts that")
    void clearingWaitsForTheSuppressionList() throws Exception {
        mark(admin, true).andExpect(status().isOk());

        assertThat(codeOf(mark(admin, false).andExpect(status().isConflict()).andReturn()))
                .isEqualTo("PERSON_OBJECTION_HELD");
        assertThat(db.queryForObject("select do_not_contact from app_lm_person where id = ?::uuid", Boolean.class,
                person)).isTrue();

        db.update("delete from app_lm_workspace_suppression where workspace_id = ?::uuid", workspace);
        mark(admin, false).andExpect(status().isOk());
        assertThat(db.queryForObject("select do_not_contact from app_lm_person where id = ?::uuid", Boolean.class,
                person)).isFalse();
    }

    @Test
    @DisplayName("a suppression list this deployment cannot read refuses the mark, and nothing changes")
    void anUnreadableListRefusesTheMark() throws Exception {
        call(admin, post("/api/v1/workspace/suppressions"), """
                {"email":"someone-else@example.com"}""").andExpect(status().isCreated());
        db.update("update app_lm_workspace_suppression set key_id = 1 where workspace_id = ?::uuid", workspace);

        assertThat(codeOf(mark(admin, true).andExpect(status().isServiceUnavailable()).andReturn()))
                .isEqualTo("SUPPRESSION_UNAVAILABLE");
        assertThat(db.queryForObject("select do_not_contact from app_lm_person where id = ?::uuid", Boolean.class,
                person)).isFalse();
        assertThat(activityKinds()).doesNotContain("OBJECTION_RECORDED");
    }

    @Test
    @DisplayName("the consent register carries the objection, with the door it came through")
    void theRegisterCarriesTheObjection() throws Exception {
        mark(colleague, true).andExpect(status().isOk());

        String csv = call(admin, get("/api/v1/candidates/export/consent-register"), null)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();

        assertThat(csv.lines().filter(line -> line.contains(person)).toList()).singleElement()
                .satisfies(line -> assertThat(line).contains("Noor Haddad", "Objection to contact", "Objected",
                        "Sam Lee", "Recorded by the team"));
    }

    private ResultActions mark(String caller, boolean doNotContact) throws Exception {
        return call(caller, put("/api/v1/candidates/" + person + "/do-not-contact"), """
                {"doNotContact":%s,"reason":"Asked us to stop"}""".formatted(doNotContact));
    }

    private List<String> activityKinds() {
        return db.queryForList("select kind from app_lm_person_activity where person_id = ?::uuid", String.class,
                person);
    }

    private List<String> notifiedEmails() {
        return db.queryForList("""
                select u.email from app_lm_notification n join app_lm_user u on u.id = n.recipient_user_id
                where n.workspace_id = ?::uuid and n.kind = 'PERSON_OBJECTED'""", String.class, workspace);
    }

    private String mandate(String positionTitle) throws Exception {
        String clientId = body(call(admin, post("/api/v1/clients"), """
                {"customName":"%s Unit"}""".formatted(positionTitle)).andReturn()).get("id").asText();
        JsonNode created = body(call(admin, post("/api/v1/projects"), """
                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, positionTitle))
                .andExpect(status().isCreated()).andReturn());
        return created.get("id").asText();
    }

    private ResultActions call(String caller, MockHttpServletRequestBuilder request, String content)
            throws Exception {
        request.header("Authorization", "Bearer " + caller);
        if (content != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(content);
        }
        return mvc.perform(request);
    }
}
