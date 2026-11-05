package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.project.model.CandidateCountScope;
import app.lightmove.api.project.service.ProjectCandidateCounter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.databind.JsonNode;

/**
 * Every number read off the pipeline counts by stage, so a workspace's own statuses count where its admin
 * put them: the projects list, the report's market hubs and status mix, and the export's Stage column.
 */
@IntegrationTest
class CandidatePipelineCountsTest extends PipelineFlowSupport {

    @Autowired private ProjectCandidateCounter counter;

    @Test
    @DisplayName("a workspace's own status in each stage counts with that stage, everywhere a number is read")
    void ownStatusesCountByStage() throws Exception {
        firm("Pipeline Counts Firm");
        String projectId = mandate("Chief Financial Officer");
        JsonNode catalog = catalog();
        ownStatus(catalog, "identified", "Long list");
        ownStatus(catalog, "interested", "Hot");
        ownStatus(catalog, "shortlisted", "Final two");
        write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"Placed elsewhere","outOfProcess":true}""".formatted(
                stageIn(catalog, "contacted").get("id").asText()));
        String company = capture(projectId, "ACWA Power");
        for (String key : List.of("longList", "contacted", "engaged", "hot", "finalTwo", "placedElsewhere",
                "offLimits")) {
            add(projectId, """
                    {"triageCompanyId":"%s","fullName":"Exec %s","status":"%s",
                     "locationCountry":"United Arab Emirates"}""".formatted(company, key, key));
        }

        JsonNode project = projectRow(projectId);
        assertThat(project.get("mappedCandidates").asLong()).isEqualTo(7);
        assertThat(project.get("candidates").asLong()).as("everyone not at a status out of process").isEqualTo(5);
        assertThat(project.get("engagedCandidates").asLong()).as("Engaged, and Interested onwards").isEqualTo(3);

        JsonNode report = read("/api/v1/projects/" + projectId + "/report");
        assertThat(report.at("/market/hubs/0/interested").asInt()).as("Interested onwards, still running")
                .isEqualTo(2);

        JsonNode team = read("/api/v1/projects/" + projectId + "/report/team");
        List<String> mix = new ArrayList<>();
        team.at("/researchers/0/statusMix").forEach(row ->
                mix.add(row.get("status").asText() + ":" + row.get("label").asText() + ":" + row.get("count").asInt()));
        assertThat(mix).containsExactly("identified:Research:2", "contacted:Approach:3", "interested:Assessment:1",
                "shortlisted:Offer:1");

        String csv = mvc.perform(get("/api/v1/projects/" + projectId + "/export/companies")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(csv).contains("Hot,Assessment").contains("Placed elsewhere,Approach")
                .contains("Final two,Offer");
    }

    @Test
    @DisplayName("a workspace's own stage placed after Interested counts as engaged")
    void anOwnStageAfterInterestedCountsAsEngaged() throws Exception {
        firm("Own Stage Firm");
        String projectId = mandate("Chief Financial Officer");
        JsonNode catalog = body(write(admin, "POST", PIPELINE + "/stages", """
                {"label":"Onboarding"}"""));
        assertThat(stageIn(catalog, "onboarding").get("position").asInt())
                .isGreaterThan(stageIn(catalog, "interested").get("position").asInt());
        ownStatus(catalog, "onboarding", "Offer out");
        add(projectId, """
                {"fullName":"Offered Person","status":"offerOut"}""");
        add(projectId, """
                {"fullName":"Contacted Person","status":"contacted"}""");

        JsonNode project = projectRow(projectId);
        assertThat(project.get("candidates").asLong()).isEqualTo(2);
        assertThat(project.get("engagedCandidates").asLong()).isEqualTo(1);
    }

    @Test
    @DisplayName("retiring the seeded Interested and Identified statuses leaves the counts and the report readable")
    void retiredSeededStatusesStillAnchorTheCounts() throws Exception {
        firm("Retired Anchors Firm");
        String projectId = mandate("Chief Financial Officer");
        add(projectId, """
                {"fullName":"Interested Person","status":"interested"}""");
        add(projectId, """
                {"fullName":"Contacted Person","status":"contacted"}""");
        JsonNode catalog = catalog();
        for (String key : List.of("interested", "identified")) {
            write(admin, "PATCH", PIPELINE + "/statuses/" + statusIn(catalog, key).get("id").asText(), """
                    {"retired":true}""");
        }

        JsonNode project = projectRow(projectId);
        assertThat(project.get("candidates").asLong()).isEqualTo(2);
        assertThat(project.get("engagedCandidates").asLong()).isEqualTo(1);
        read("/api/v1/projects/" + projectId + "/report");
    }

    @Test
    @DisplayName("with the Identified stage retired and the stages reordered, reached out still counts from where people start")
    void reachedOutCountsFromTheStartingStage() throws Exception {
        firm("Retired Start Firm");
        String projectId = mandate("Chief Financial Officer");
        JsonNode catalog = catalog();
        write(admin, "PATCH", PIPELINE + "/stages/" + stageIn(catalog, "identified").get("id").asText(), """
                {"retired":true,"retireStatuses":true}""");
        catalog = catalog();
        List<String> live = new ArrayList<>();
        catalog.get("stages").forEach(stage -> {
            if (!stage.get("retired").asBoolean()) {
                live.add(stage.get("id").asText());
            }
        });
        write(admin, "PUT", PIPELINE + "/stages/order", """
                {"ids":["%s"]}""".formatted(String.join("\",\"", live)));
        add(projectId, """
                {"fullName":"Starting Person"}""");
        add(projectId, """
                {"fullName":"Interested Person","status":"interested"}""");

        Map<UUID, Long> reached = inWorkspace(() -> counter.countReachedOutByProject(new CandidateCountScope(
                UUID.fromString(workspaceId), List.of(UUID.fromString(projectId)), Set.of())));

        assertThat(reached.get(UUID.fromString(projectId))).isEqualTo(1L);
    }

    private void ownStatus(JsonNode catalog, String stageKey, String label) throws Exception {
        write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"%s"}""".formatted(stageIn(catalog, stageKey).get("id").asText(), label));
    }

    private JsonNode projectRow(String projectId) throws Exception {
        for (JsonNode row : read("/api/v1/projects")) {
            if (row.get("id").asText().equals(projectId)) {
                return row;
            }
        }
        throw new AssertionError("no project " + projectId);
    }

    private JsonNode read(String url) throws Exception {
        return body(mvc.perform(get(url).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }
}
