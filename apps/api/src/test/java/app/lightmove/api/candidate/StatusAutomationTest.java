package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;

/**
 * The status each outreach event moves a candidate to (V140): the seeded behaviour until an admin chooses, the
 * workspace's choice after, and nobody moved — the event still recorded — once the chosen status is retired.
 */
@IntegrationTest
class StatusAutomationTest extends PipelineFlowSupport {

    private static final String AUTOMATIONS = PIPELINE + "/automations";

    @Autowired private CandidateOutreachService outreach;
    @Autowired private TransactionTemplate transactions;

    private String projectId;

    @BeforeEach
    void aPosition() throws Exception {
        firm("Status Automation Firm");
        projectId = mandate("Chief Financial Officer");
    }

    @Test
    @DisplayName("a workspace that never chose sends an email to Contacted and a booking to Engaged, and a reply nowhere")
    void theSeededRulesAreTodaysBehaviour() throws Exception {
        JsonNode catalog = catalog();
        JsonNode rules = automations(admin);

        assertThat(rule(rules, "emailSent").get("enabled").asBoolean()).isTrue();
        assertThat(rule(rules, "emailSent").get("statusId").asText())
                .isEqualTo(statusIn(catalog, "contacted").get("id").asText());
        assertThat(rule(rules, "callBooked").get("statusId").asText())
                .isEqualTo(statusIn(catalog, "engaged").get("id").asText());
        assertThat(rule(rules, "replied").get("enabled").asBoolean()).isFalse();
        assertThat(rule(rules, "replied").get("statusId").isNull()).isTrue();
    }

    @Test
    @DisplayName("an email moves people to the status an admin chose, and a reply moves them once switched on")
    void theWorkspacesChoiceIsWherePeopleGo() throws Exception {
        JsonNode catalog = catalog();
        write(admin, "PUT", AUTOMATIONS + "/emailSent", """
                {"enabled":true,"statusId":"%s"}""".formatted(statusIn(catalog, "followUpSent").get("id").asText()));
        write(admin, "PUT", AUTOMATIONS + "/replied", """
                {"enabled":true,"statusId":"%s"}""".formatted(statusIn(catalog, "interested").get("id").asText()));
        JsonNode emailed = candidate("researching");
        JsonNode replied = candidate("contacted");

        transactions.executeWithoutResult(status -> outreach.recordEmailSent(null, UUID.fromString(projectId),
                idOf(emailed), UUID.randomUUID(), "Opening", 1));
        transactions.executeWithoutResult(status -> outreach.recordReplied(UUID.fromString(workspaceId),
                UUID.fromString(projectId), UUID.fromString(replied.get("personId").asText()), UUID.randomUUID(),
                "Opening"));

        assertThat(statusOf(idOf(emailed))).isEqualTo("followUpSent");
        assertThat(statusOf(idOf(replied))).isEqualTo("interested");
    }

    @Test
    @DisplayName("a rule whose status was retired is reported broken and moves nobody, the booking still made")
    void aRetiredTargetPausesTheRule() throws Exception {
        String engaged = statusIn(catalog(), "engaged").get("id").asText();
        write(admin, "PATCH", PIPELINE + "/statuses/" + engaged, """
                {"retired":true}""");
        JsonNode booked = candidate("identified");

        transactions.executeWithoutResult(status -> outreach.recordMeetingBooked(null,
                UUID.fromString(projectId), idOf(booked), Instant.now(), false));

        assertThat(rule(automations(admin), "callBooked").get("broken").asBoolean()).isTrue();
        assertThat(statusOf(idOf(booked))).isEqualTo("identified");
        assertThat(codeOf(write(admin, "PUT", AUTOMATIONS + "/callBooked", """
                {"enabled":true,"statusId":"%s"}""".formatted(engaged)))).isEqualTo("PIPELINE_STATUS_RETIRED");
    }

    @Test
    @DisplayName("a rule needs a status to be switched on, and only an admin changes one")
    void onlyAnAdminChangesARule() throws Exception {
        String member = member("noor");
        String interested = statusIn(catalog(), "interested").get("id").asText();

        assertThat(codeOf(write(admin, "PUT", AUTOMATIONS + "/replied", """
                {"enabled":true}"""))).isEqualTo("VALIDATION_FAILED");
        assertThat(write(member, "PUT", AUTOMATIONS + "/replied", """
                {"enabled":true,"statusId":"%s"}""".formatted(interested)).getResponse().getStatus()).isEqualTo(403);
        assertThat(rule(automations(member), "replied").get("enabled").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a paused rule can be switched off, which clears its pause")
    void aPausedRuleSwitchesOff() throws Exception {
        String contacted = statusIn(catalog(), "contacted").get("id").asText();
        write(admin, "PATCH", PIPELINE + "/statuses/" + contacted, """
                {"retired":true}""");

        JsonNode rules = body(write(admin, "PUT", AUTOMATIONS + "/emailSent", """
                {"enabled":false,"statusId":"%s"}""".formatted(contacted)));

        assertThat(rule(rules, "emailSent").get("enabled").asBoolean()).isFalse();
        assertThat(rule(rules, "emailSent").get("broken").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a rule never points into the closed stage, nor at another workspace's status")
    void aRuleTargetsAnActiveStatusOfItsOwnWorkspace() throws Exception {
        String withdrew = statusIn(catalog(), "withdrew").get("id").asText();
        String foreign = db.queryForObject("""
                select id::text from app_lm_workspace_candidate_status
                where workspace_id <> ?::uuid limit 1""", String.class, workspaceId);

        assertThat(codeOf(write(admin, "PUT", AUTOMATIONS + "/replied", """
                {"enabled":true,"statusId":"%s"}""".formatted(withdrew)))).isEqualTo("VALIDATION_FAILED");
        assertThat(write(admin, "PUT", AUTOMATIONS + "/replied", """
                {"enabled":true,"statusId":"%s"}""".formatted(foreign == null ? UUID.randomUUID() : foreign))
                .getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("a client seat is told neither the automations nor what a retire would touch")
    void aClientSeatReadsNoneOfIt() throws Exception {
        String client = clientOn(projectId);
        JsonNode catalog = catalog();

        for (String url : List.of(AUTOMATIONS,
                PIPELINE + "/stages/" + stageIn(catalog, "contacted").get("id").asText() + "/retire-preview",
                PIPELINE + "/statuses/" + statusIn(catalog, "contacted").get("id").asText() + "/retire-preview")) {
            mvc.perform(get(url).header("Authorization", "Bearer " + client)).andExpect(status().isNotFound());
        }
    }

    private JsonNode automations(String token) throws Exception {
        return body(mvc.perform(get(AUTOMATIONS).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static JsonNode rule(JsonNode rules, String trigger) {
        for (JsonNode rule : rules) {
            if (rule.get("trigger").asText().equals(trigger)) {
                return rule;
            }
        }
        throw new AssertionError("No automation " + trigger);
    }

    private JsonNode candidate(String statusKey) throws Exception {
        return add(projectId, """
                {"fullName":"Person at %s","status":"%s"}""".formatted(statusKey, statusKey));
    }

    private static UUID idOf(JsonNode candidate) {
        return UUID.fromString(candidate.get("id").asText());
    }

    private String statusOf(UUID candidateId) throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("status").asText();
    }
}
