package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * A candidate holds one status of their workspace's pipeline and reads its stage through it. The seven
 * keys every client already speaks keep working; a status from another workspace, or one retired, is
 * refused; and every move is a timeline line whose labels outlive a rename.
 */
@IntegrationTest
class CandidateStatusChangeTest extends PipelineFlowSupport {

    @Test
    @DisplayName("the seven old keys are still accepted and read back, with the status's label and stage beside them")
    void theOldKeysStillWork() throws Exception {
        firm("Status Keys Firm");
        String projectId = mandate("Chief Financial Officer");

        for (String key : List.of("identified", "contacted", "engaged", "interested", "notInterested", "offLimits",
                "outOfScope")) {
            JsonNode added = add(projectId, """
                    {"fullName":"Person %s","status":"%s"}""".formatted(key, key));
            assertThat(added.get("status").asText()).isEqualTo(key);
            assertThat(added.get("statusChangedAt").isNull()).isFalse();
        }
        JsonNode offLimits = add(projectId, """
                {"fullName":"Rana Off Limits","status":"offLimits"}""");
        assertThat(offLimits.get("statusLabel").asText()).isEqualTo("Off-limits");
        assertThat(offLimits.get("statusColour").asText()).isEqualTo("red");
        assertThat(offLimits.at("/pipelineStage/key").asText()).isEqualTo("declined");
        assertThat(offLimits.at("/pipelineStage/kind").asText()).isEqualTo("closed");

        JsonNode filed = add(projectId, """
                {"fullName":"No Status Given"}""");
        assertThat(filed.get("status").asText()).isEqualTo("identified");
        assertThat(filed.at("/pipelineStage/key").asText()).isEqualTo("identified");
    }

    @Test
    @DisplayName("a key the workspace does not hold is a 400, and a retired one a 409 that leaves whoever holds it alone")
    void anotherWorkspacesOrARetiredStatusIsRefused() throws Exception {
        firm("Status Owner Firm");
        String interested = stageIn(catalog(), "interested").get("id").asText();
        write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"Counter offer"}""".formatted(interested));

        domain = "status-other-" + domain;
        firm("Status Other Firm");
        String projectId = mandate("Head of Treasury");
        String candidateId = add(projectId, """
                {"fullName":"Karim Nassar"}""").get("id").asText();
        assertThat(moveTo(projectId, candidateId, "counterOffer").getResponse().getStatus()).isEqualTo(400);

        String ourInterested = stageIn(catalog(), "interested").get("id").asText();
        String waiting = statusIn(body(write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"Waiting on board"}""".formatted(ourInterested))), "waitingOnBoard")
                .get("id").asText();
        assertThat(moveTo(projectId, candidateId, "waitingOnBoard").getResponse().getStatus()).isEqualTo(200);
        write(admin, "PATCH", PIPELINE + "/statuses/" + waiting, """
                {"retired":true}""");

        String other = add(projectId, """
                {"fullName":"Hana Aziz"}""").get("id").asText();
        assertThat(codeOf(moveTo(projectId, other, "waitingOnBoard"))).isEqualTo("PIPELINE_STATUS_RETIRED");
        JsonNode held = read(projectId, candidateId);
        assertThat(held.get("status").asText()).isEqualTo("waitingOnBoard");
        assertThat(held.get("statusLabel").asText()).isEqualTo("Waiting on board");
    }

    @Test
    @DisplayName("a move is a timeline line naming both statuses as they were spelled, whatever they are called later")
    void aMoveKeepsItsLabels() throws Exception {
        firm("Status Line Firm");
        String projectId = mandate("Chief Financial Officer");
        String candidateId = add(projectId, """
                {"fullName":"Layla Haddad"}""").get("id").asText();
        String contacted = statusIn(catalog(), "contacted").get("id").asText();

        moveTo(projectId, candidateId, "contacted");
        write(admin, "PATCH", PIPELINE + "/statuses/" + contacted, """
                {"label":"Approached"}""");

        JsonNode line = statusLines(projectId, candidateId).getFirst();
        assertThat(line.at("/details/from").asText()).isEqualTo("identified");
        assertThat(line.at("/details/to").asText()).isEqualTo("contacted");
        assertThat(line.at("/details/fromLabel").asText()).isEqualTo("Identified");
        assertThat(line.at("/details/toLabel").asText()).isEqualTo("Contacted");
        assertThat(read(projectId, candidateId).get("statusLabel").asText()).isEqualTo("Approached");
    }

    @Test
    @DisplayName("a whole-record save that names a new status records the move; one naming none leaves it where it stands")
    void aReplaceRecordsTheMove() throws Exception {
        firm("Status Replace Firm");
        String projectId = mandate("Chief Financial Officer");
        String candidateId = add(projectId, """
                {"fullName":"Omar Farouk"}""").get("id").asText();

        write(admin, "PUT", candidatesUrl(projectId) + "/" + candidateId, """
                {"fullName":"Omar Farouk","status":"interested"}""");
        assertThat(statusLines(projectId, candidateId)).hasSize(1);

        write(admin, "PUT", candidatesUrl(projectId) + "/" + candidateId, """
                {"fullName":"Omar Farouk","title":"Group CFO"}""");
        assertThat(read(projectId, candidateId).get("status").asText()).isEqualTo("interested");
        assertThat(statusLines(projectId, candidateId)).hasSize(1);
    }

    @Test
    @DisplayName("the list narrows to a status or to every status of a stage")
    void theListFiltersByStatusAndStage() throws Exception {
        firm("Status Filter Firm");
        String projectId = mandate("Chief Financial Officer");
        add(projectId, """
                {"fullName":"Identified Person"}""");
        add(projectId, """
                {"fullName":"Off Limits Person","status":"offLimits"}""");
        add(projectId, """
                {"fullName":"Withdrew Person","status":"withdrew"}""");

        assertThat(names(list(projectId, "pipelineStage", "declined")))
                .containsExactlyInAnyOrder("Off Limits Person", "Withdrew Person");
        assertThat(names(list(projectId, "status", "withdrew"))).containsExactly("Withdrew Person");
        assertThat(mvc.perform(get(candidatesUrl(projectId)).param("pipelineStage", "nowhere")
                        .header("Authorization", "Bearer " + admin))
                .andReturn().getResponse().getStatus()).isEqualTo(400);
    }

    @Test
    @DisplayName("the Companies grid ranks by stage then status, a workspace's own statuses where its admin put them")
    void theGridRanksByThePipeline() throws Exception {
        firm("Status Rank Firm");
        String projectId = mandate("Chief Financial Officer");
        JsonNode catalog = catalog();
        JsonNode identifiedStage = stageIn(catalog, "identified");
        catalog = body(write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"Long list"}""".formatted(identifiedStage.get("id").asText())));

        executiveAt(projectId, "Follow Up Co", "followUpSent");
        executiveAt(projectId, "Contacted Co", "contacted");
        executiveAt(projectId, "Researching Co", "researching");
        executiveAt(projectId, "Long List Co", "longList");

        assertThat(rankedCompanies(projectId))
                .containsExactly("Researching Co", "Long List Co", "Contacted Co", "Follow Up Co");

        List<String> order = new ArrayList<>();
        order.add(statusIn(catalog, "longList").get("id").asText());
        stageIn(catalog, "identified").get("statuses").forEach(status -> {
            if (!status.get("key").asText().equals("longList")) {
                order.add(status.get("id").asText());
            }
        });
        write(admin, "PUT", PIPELINE + "/stages/" + identifiedStage.get("id").asText() + "/statuses/order",
                "{\"ids\":[" + String.join(",", order.stream().map(id -> "\"" + id + "\"").toList()) + "]}");

        assertThat(rankedCompanies(projectId))
                .containsExactly("Long List Co", "Researching Co", "Contacted Co", "Follow Up Co");
    }

    private void executiveAt(String projectId, String companyName, String statusKey) throws Exception {
        add(projectId, """
                {"triageCompanyId":"%s","fullName":"Exec at %s","status":"%s"}"""
                .formatted(capture(projectId, companyName), companyName, statusKey));
    }

    private List<String> rankedCompanies(String projectId) throws Exception {
        JsonNode page = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage")
                        .param("sort", "executiveStatus").param("direction", "asc")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        List<String> names = new ArrayList<>();
        page.get("companies").forEach(company -> names.add(company.get("companyName").asText()));
        return names;
    }

    private JsonNode read(String projectId, String candidateId) throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode list(String projectId, String param, String value) throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectId)).param(param, value)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    private List<JsonNode> statusLines(String projectId, String candidateId) throws Exception {
        JsonNode timeline = body(mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId + "/timeline")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        List<JsonNode> lines = new ArrayList<>();
        timeline.get("entries").forEach(entry -> {
            if (entry.get("kind").asText().equals("STATUS_CHANGED")) {
                lines.add(entry);
            }
        });
        return lines;
    }

    private static List<String> names(JsonNode page) {
        List<String> names = new ArrayList<>();
        page.get("candidates").forEach(candidate -> names.add(candidate.get("fullName").asText()));
        return names;
    }
}
