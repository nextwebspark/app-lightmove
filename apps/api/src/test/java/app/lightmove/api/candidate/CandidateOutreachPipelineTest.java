package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.candidate.model.OutreachRecipient;
import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.outreach.constant.OutreachSkipReason;
import app.lightmove.api.outreach.model.RecipientEligibility;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Outreach reads the pipeline by stage: anyone at a status of the closed stage — a workspace's own included —
 * is out of the running and never approached, and the moves an email or a booked call make go forward only,
 * by the pipeline's own order, never back from where a consultant put someone.
 */
@IntegrationTest
class CandidateOutreachPipelineTest extends PipelineFlowSupport {

    @Autowired private CandidateOutreachService outreach;
    @Autowired private TransactionTemplate transactions;

    private String projectId;

    @BeforeEach
    void aPosition() throws Exception {
        firm("Outreach Pipeline Firm");
        projectId = mandate("Chief Financial Officer");
    }

    @Test
    @DisplayName("every status of the closed stage leaves the running, a workspace's own included")
    void theClosedStageIsOutOfTheRunning() throws Exception {
        String declined = stageIn(catalog(), "declined").get("id").asText();
        write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"Placed elsewhere"}""".formatted(declined));
        UUID placed = candidate("placedElsewhere");
        UUID withdrew = candidate("withdrew");
        UUID researching = candidate("researching");

        Map<UUID, OutreachSkipReason> reasons = new HashMap<>();
        RecipientEligibility eligibility = new RecipientEligibility(Map.of());
        for (OutreachRecipient recipient : outreach.recipientsOf(UUID.fromString(workspaceId),
                UUID.fromString(projectId), List.of(placed, withdrew, researching), List.of())) {
            reasons.put(recipient.candidateId(), eligibility.skipReasonOf(recipient));
        }

        assertThat(reasons.get(placed)).isEqualTo(OutreachSkipReason.LEFT_THE_RUNNING);
        assertThat(reasons.get(withdrew)).isEqualTo(OutreachSkipReason.LEFT_THE_RUNNING);
        assertThat(reasons.get(researching)).isEqualTo(OutreachSkipReason.NO_EMAIL);
    }

    @Test
    @DisplayName("the first email moves someone not yet contacted to Contacted, and nobody further on or ruled out")
    void anEmailMovesForwardOnly() throws Exception {
        UUID researching = candidate("researching");
        UUID followedUp = candidate("followUpSent");
        UUID screened = candidate("screened");
        UUID withdrew = candidate("withdrew");

        for (UUID candidateId : List.of(researching, followedUp, screened, withdrew)) {
            transactions.executeWithoutResult(status -> outreach.recordEmailSent(null, UUID.fromString(projectId),
                    candidateId, UUID.randomUUID(), "Opening", 1));
        }

        assertThat(statusOf(researching)).isEqualTo("contacted");
        assertThat(statusOf(followedUp)).isEqualTo("followUpSent");
        assertThat(statusOf(screened)).isEqualTo("screened");
        assertThat(statusOf(withdrew)).isEqualTo("withdrew");
    }

    @Test
    @DisplayName("a booked call moves someone short of Engaged there, and nobody past it")
    void aBookingMovesForwardOnly() throws Exception {
        UUID identified = candidate("identified");
        UUID followedUp = candidate("followUpSent");
        UUID callBooked = candidate("callBooked");
        UUID offLimits = candidate("offLimits");

        for (UUID candidateId : List.of(identified, followedUp, callBooked, offLimits)) {
            transactions.executeWithoutResult(status -> outreach.recordMeetingBooked(null,
                    UUID.fromString(projectId), candidateId, Instant.now(), false));
        }

        assertThat(statusOf(identified)).isEqualTo("engaged");
        assertThat(statusOf(followedUp)).isEqualTo("engaged");
        assertThat(statusOf(callBooked)).isEqualTo("callBooked");
        assertThat(statusOf(offLimits)).isEqualTo("offLimits");
    }

    private UUID candidate(String statusKey) throws Exception {
        return UUID.fromString(add(projectId, """
                {"fullName":"Person at %s","status":"%s"}""".formatted(statusKey, statusKey)).get("id").asText());
    }

    private String statusOf(UUID candidateId) throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("status").asText();
    }
}
