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
import tools.jackson.databind.JsonNode;

/**
 * Outreach reads the pipeline by status: anyone at a status out of process — wherever it sits, a workspace's own
 * and an old closed stage's included — is out of the running and never approached, and the moves an email or a
 * booked call make go forward only, by the pipeline's own order, never back from where a consultant put someone
 * and never from a status out of process.
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
    @DisplayName("a status out of process leaves the running wherever it sits, a workspace's own and an old closed stage's")
    void anOutOfProcessStatusIsOutOfTheRunning() throws Exception {
        write(admin, "PATCH", PIPELINE + "/statuses/" + statusIn(catalog(), "callArranged").get("id").asText(), """
                {"outOfProcess":true}""");
        formerClosedStage("Declined", "placedElsewhere");
        UUID flagged = candidate("callArranged");
        UUID placed = candidate("placedElsewhere");
        UUID withdrew = candidate("withdrew");
        UUID source = candidate("source");

        Map<UUID, OutreachSkipReason> reasons = new HashMap<>();
        RecipientEligibility eligibility = new RecipientEligibility(Map.of());
        for (OutreachRecipient recipient : inWorkspace(() -> outreach.recipientsOf(UUID.fromString(workspaceId),
                UUID.fromString(projectId), List.of(flagged, placed, withdrew, source), List.of()))) {
            reasons.put(recipient.candidateId(), eligibility.skipReasonOf(recipient));
        }

        assertThat(reasons.get(flagged)).isEqualTo(OutreachSkipReason.LEFT_THE_RUNNING);
        assertThat(reasons.get(placed)).isEqualTo(OutreachSkipReason.LEFT_THE_RUNNING);
        assertThat(reasons.get(withdrew)).isEqualTo(OutreachSkipReason.LEFT_THE_RUNNING);
        assertThat(reasons.get(source)).isEqualTo(OutreachSkipReason.NO_EMAIL);
    }

    @Test
    @DisplayName("the first email moves someone not yet contacted to Awaiting reply, and nobody further on or out of process")
    void anEmailMovesForwardOnly() throws Exception {
        UUID source = candidate("source");
        UUID arranging = candidate("callBeingArranged");
        UUID screening = candidate("interested");
        UUID outOfScope = candidate("outOfScope");

        for (UUID candidateId : List.of(source, arranging, screening, outOfScope)) {
            inWorkspace(() -> transactions.executeWithoutResult(status -> outreach.recordEmailSent(null,
                    UUID.fromString(projectId), candidateId, UUID.randomUUID(), "Opening", 1, UUID.randomUUID())));
        }

        assertThat(statusOf(source)).isEqualTo("contacted");
        assertThat(statusOf(arranging)).isEqualTo("callBeingArranged");
        assertThat(statusOf(screening)).isEqualTo("interested");
        assertThat(statusOf(outOfScope)).isEqualTo("outOfScope");
    }

    @Test
    @DisplayName("a booked call moves someone short of Confirming interest there, and nobody past it or out of process")
    void aBookingMovesForwardOnly() throws Exception {
        UUID identified = candidate("identified");
        UUID awaiting = candidate("contacted");
        UUID callArranged = candidate("callArranged");
        UUID offLimits = candidate("offLimits");

        for (UUID candidateId : List.of(identified, awaiting, callArranged, offLimits)) {
            inWorkspace(() -> transactions.executeWithoutResult(status -> outreach.recordMeetingBooked(null,
                    UUID.fromString(projectId), candidateId, Instant.now(), false)));
        }

        assertThat(statusOf(identified)).isEqualTo("engaged");
        assertThat(statusOf(awaiting)).isEqualTo("engaged");
        assertThat(statusOf(callArranged)).isEqualTo("callArranged");
        assertThat(statusOf(offLimits)).isEqualTo("offLimits");
    }

    @Test
    @DisplayName("once the contact statuses move into Identified's stage, an email and a booking still move people forward")
    void theMovesFollowBuiltInsToTheirNewStage() throws Exception {
        JsonNode catalog = catalog();
        String identifiedStage = stageIn(catalog, "identified").get("id").asText();
        for (JsonNode status : stageIn(catalog, "contacted").get("statuses")) {
            write(admin, "PATCH", PIPELINE + "/statuses/" + status.get("id").asText(), """
                    {"stageId":"%s"}""".formatted(identifiedStage));
        }
        UUID emailed = candidate("source");
        UUID booked = candidate("identified");

        inWorkspace(() -> {
            transactions.executeWithoutResult(status -> outreach.recordEmailSent(null, UUID.fromString(projectId),
                    emailed, UUID.randomUUID(), "Opening", 1, UUID.randomUUID()));
            transactions.executeWithoutResult(status -> outreach.recordMeetingBooked(null,
                    UUID.fromString(projectId), booked, Instant.now(), false));
        });

        assertThat(statusOf(emailed)).isEqualTo("contacted");
        assertThat(statusOf(booked)).isEqualTo("engaged");
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
