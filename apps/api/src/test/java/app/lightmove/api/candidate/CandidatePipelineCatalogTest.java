package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * A workspace's pipeline (V131) and the rules that keep its hierarchy whole: every status under a stage,
 * the built-in statuses never retired nor moved across the closed line, one default per stage, a stage retired only once empty, and
 * one closed stage, last.
 */
@IntegrationTest
class CandidatePipelineCatalogTest extends PipelineFlowSupport {

    @BeforeEach
    void aFirm() throws Exception {
        firm("Pipeline Rules Firm");
    }

    @Test
    @DisplayName("a workspace founded after the migration is seeded the first time its pipeline is read")
    void aNewWorkspaceIsSeeded() throws Exception {
        JsonNode catalog = catalog();

        assertThat(keysOf(catalog.get("stages")))
                .containsExactly("identified", "contacted", "interested", "qualified", "shortlisted", "declined");
        assertThat(stageIn(catalog, "declined").get("kind").asText()).isEqualTo("closed");
        assertThat(keysOf(stageIn(catalog, "declined").get("statuses"))).containsExactly("notInterested", "withdrew",
                "notAFit", "rejectedByClient", "noResponse", "offLimits", "outOfScope");
        assertThat(statusIn(catalog, "screened").get("isDefault").asBoolean()).isTrue();
        assertThat(statusIn(catalog, "offLimits").get("system").asBoolean()).isTrue();
        assertThat(statusIn(catalog, "researching").get("system").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a status is added under its stage, last, with a key read off its name; its name is the workspace's alone")
    void aStatusIsAddedUnderItsStage() throws Exception {
        String interested = stageIn(catalog(), "interested").get("id").asText();

        JsonNode catalog = body(write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"Waiting on visa","colour":"violet"}""".formatted(interested)));

        JsonNode added = statusIn(catalog, "waitingOnVisa");
        assertThat(added.get("label").asText()).isEqualTo("Waiting on visa");
        assertThat(added.get("isDefault").asBoolean()).isFalse();
        assertThat(keysOf(stageIn(catalog, "interested").get("statuses")).getLast()).isEqualTo("waitingOnVisa");

        assertThat(codeOf(write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"WAITING ON VISA"}""".formatted(interested))))
                .isEqualTo("PIPELINE_LABEL_TAKEN");
        assertThat(write(admin, "POST", PIPELINE + "/statuses", """
                {"label":"Floating"}""").getResponse().getStatus()).isEqualTo(400);
        assertThat(write(admin, "POST", PIPELINE + "/stages", """
                {"label":"Interested"}""").getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("a built-in status is renamed, recoloured and moved between active stages, never retired or across the closed line")
    void aSystemStatusStaysOnItsSideOfTheLine() throws Exception {
        JsonNode catalog = catalog();
        String offLimits = statusIn(catalog, "offLimits").get("id").asText();
        String contacted = statusIn(catalog, "contacted").get("id").asText();
        String identifiedStage = stageIn(catalog, "identified").get("id").asText();
        String contactedStage = stageIn(catalog, "contacted").get("id").asText();
        String declinedStage = stageIn(catalog, "declined").get("id").asText();

        JsonNode renamed = body(write(admin, "PATCH", PIPELINE + "/statuses/" + offLimits, """
                {"label":"Do not approach","colour":"signal"}"""));
        assertThat(statusIn(renamed, "offLimits").get("label").asText()).isEqualTo("Do not approach");

        assertThat(codeOf(write(admin, "PATCH", PIPELINE + "/statuses/" + offLimits, """
                {"retired":true}"""))).isEqualTo("PIPELINE_STATUS_SYSTEM");
        assertThat(codeOf(write(admin, "PATCH", PIPELINE + "/statuses/" + offLimits, """
                {"stageId":"%s"}""".formatted(contactedStage)))).isEqualTo("PIPELINE_STATUS_SYSTEM");
        assertThat(codeOf(write(admin, "PATCH", PIPELINE + "/statuses/" + contacted, """
                {"stageId":"%s"}""".formatted(declinedStage)))).isEqualTo("PIPELINE_STATUS_SYSTEM");

        JsonNode moved = body(write(admin, "PATCH", PIPELINE + "/statuses/" + contacted, """
                {"stageId":"%s"}""".formatted(identifiedStage)));
        assertThat(keysOf(stageIn(moved, "identified").get("statuses")).getLast()).isEqualTo("contacted");
        assertThat(defaultsOf(stageIn(moved, "identified"))).containsExactly("identified");
        assertThat(defaultsOf(stageIn(moved, "contacted"))).containsExactly("followUpSent");
    }

    @Test
    @DisplayName("a stage holding built-in statuses is retired once they are moved to another active stage")
    void aStageOfBuiltInsRetiresOnceMoved() throws Exception {
        JsonNode catalog = catalog();
        JsonNode contacted = stageIn(catalog, "contacted");
        String identified = stageIn(catalog, "identified").get("id").asText();

        for (JsonNode status : contacted.get("statuses")) {
            write(admin, "PATCH", PIPELINE + "/statuses/" + status.get("id").asText(), """
                    {"stageId":"%s"}""".formatted(identified));
        }
        catalog = body(write(admin, "PATCH", PIPELINE + "/stages/" + contacted.get("id").asText(), """
                {"retired":true}"""));

        assertThat(stageIn(catalog, "contacted").get("retired").asBoolean()).isTrue();
        assertThat(keysOf(stageIn(catalog, "identified").get("statuses")))
                .containsSubsequence("identified", "contacted", "followUpSent", "engaged", "callBooked");
        assertThat(defaultsOf(stageIn(catalog, "identified"))).containsExactly("identified");
    }

    @Test
    @DisplayName("a stage keeps exactly one default, and retiring it hands the mark to the next live status")
    void oneDefaultPerStage() throws Exception {
        JsonNode catalog = catalog();
        String qualified = statusIn(catalog, "qualified").get("id").asText();
        String screened = statusIn(catalog, "screened").get("id").asText();

        catalog = body(write(admin, "PATCH", PIPELINE + "/statuses/" + qualified, """
                {"isDefault":true}"""));
        assertThat(defaultsOf(stageIn(catalog, "qualified"))).containsExactly("qualified");

        catalog = body(write(admin, "PATCH", PIPELINE + "/statuses/" + qualified, """
                {"retired":true}"""));
        assertThat(statusIn(catalog, "qualified").get("retired").asBoolean()).isTrue();
        assertThat(defaultsOf(stageIn(catalog, "qualified"))).containsExactly("screened");
        assertThat(screened).isEqualTo(statusIn(catalog, "screened").get("id").asText());
    }

    @Test
    @DisplayName("a stage with a live status cannot be retired; once its statuses are retired or moved, it can")
    void aStageRetiresOnlyEmpty() throws Exception {
        JsonNode catalog = catalog();
        JsonNode qualified = stageIn(catalog, "qualified");
        String interested = stageIn(catalog, "interested").get("id").asText();

        assertThat(codeOf(write(admin, "PATCH", PIPELINE + "/stages/" + qualified.get("id").asText(), """
                {"retired":true}"""))).isEqualTo("PIPELINE_STAGE_NOT_EMPTY");

        List<JsonNode> statuses = new ArrayList<>();
        qualified.get("statuses").forEach(statuses::add);
        write(admin, "PATCH", PIPELINE + "/statuses/" + statuses.get(0).get("id").asText(), """
                {"stageId":"%s"}""".formatted(interested));
        for (JsonNode status : statuses.subList(1, statuses.size())) {
            write(admin, "PATCH", PIPELINE + "/statuses/" + status.get("id").asText(), """
                    {"retired":true}""");
        }
        catalog = body(write(admin, "PATCH", PIPELINE + "/stages/" + qualified.get("id").asText(), """
                {"retired":true}"""));

        assertThat(stageIn(catalog, "qualified").get("retired").asBoolean()).isTrue();
        assertThat(keysOf(stageIn(catalog, "interested").get("statuses")).getLast()).isEqualTo("screened");
        assertThat(defaultsOf(stageIn(catalog, "interested"))).containsExactly("interested");
    }

    @Test
    @DisplayName("there is one closed stage: never retired, always last, and a new stage lands before it")
    void theClosedStageStaysLast() throws Exception {
        JsonNode catalog = body(write(admin, "POST", PIPELINE + "/stages", """
                {"label":"Offer stage"}"""));
        assertThat(keysOf(catalog.get("stages"))).containsExactly("identified", "contacted", "interested",
                "qualified", "shortlisted", "offerStage", "declined");
        assertThat(stageIn(catalog, "offerStage").get("kind").asText()).isEqualTo("active");
        String declined = stageIn(catalog, "declined").get("id").asText();

        assertThat(codeOf(write(admin, "PATCH", PIPELINE + "/stages/" + declined, """
                {"retired":true}"""))).isEqualTo("PIPELINE_STAGE_REQUIRED");

        List<String> ids = new ArrayList<>();
        catalog.get("stages").forEach(stage -> ids.add(stage.get("id").asText()));
        List<String> closedFirst = new ArrayList<>(ids);
        closedFirst.addFirst(closedFirst.removeLast());
        assertThat(codeOf(write(admin, "PUT", PIPELINE + "/stages/order", idsJson(closedFirst))))
                .isEqualTo("PIPELINE_ORDER_INVALID");

        List<String> swapped = new ArrayList<>(ids);
        swapped.set(0, ids.get(1));
        swapped.set(1, ids.get(0));
        catalog = body(write(admin, "PUT", PIPELINE + "/stages/order", idsJson(swapped)));
        assertThat(keysOf(catalog.get("stages")).subList(0, 2)).containsExactly("contacted", "identified");
        assertThat(keysOf(catalog.get("stages")).getLast()).isEqualTo("declined");
    }

    @Test
    @DisplayName("statuses reorder within their stage, and a reorder must name every live one")
    void statusesReorderWithinTheirStage() throws Exception {
        JsonNode contacted = stageIn(catalog(), "contacted");
        List<String> ids = new ArrayList<>();
        contacted.get("statuses").forEach(status -> ids.add(status.get("id").asText()));
        String url = PIPELINE + "/stages/" + contacted.get("id").asText() + "/statuses/order";

        JsonNode catalog = body(write(admin, "PUT", url, idsJson(ids.reversed())));

        assertThat(keysOf(stageIn(catalog, "contacted").get("statuses")))
                .containsExactly("callBooked", "engaged", "followUpSent", "contacted");
        assertThat(codeOf(write(admin, "PUT", url, idsJson(ids.subList(1, ids.size())))))
                .isEqualTo("PIPELINE_ORDER_INVALID");
    }

    private static List<String> keysOf(JsonNode rows) {
        List<String> keys = new ArrayList<>();
        rows.forEach(row -> keys.add(row.get("key").asText()));
        return keys;
    }

    private static List<String> defaultsOf(JsonNode stage) {
        List<String> keys = new ArrayList<>();
        stage.get("statuses").forEach(status -> {
            if (status.get("isDefault").asBoolean()) {
                keys.add(status.get("key").asText());
            }
        });
        return keys;
    }

    private static String idsJson(List<String> ids) {
        return "{\"ids\":[" + String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList()) + "]}";
    }
}
