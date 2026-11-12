package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * A workspace's pipeline (V131, V163) and the rules that keep its hierarchy whole: every status under a stage,
 * the seeded statuses retired like any other, one in-process default per stage, a stage retired only once
 * empty, and a status flagged out of process wherever it sits — every stage alike, an old closed stage included.
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
                .containsExactly("identified", "contacted", "interested", "qualified", "shortlisted");
        assertThat(labelsOf(catalog.get("stages")))
                .containsExactly("Research", "Approach", "Assessment", "Shortlist", "Offer");
        catalog.get("stages").forEach(stage -> assertThat(stage.has("kind")).isFalse());
        assertThat(labelsOf(stageIn(catalog, "contacted").get("statuses"))).containsExactly("Awaiting reply",
                "Confirming interest", "Call being arranged", "Call arranged", "Not interested", "Unresponsive");
        assertThat(flaggedOf(catalog)).containsExactlyInAnyOrder("outOfScope", "offLimits", "notInterested",
                "unresponsive", "screenedOut", "notProgressed", "withdrew", "clientRejected",
                "clientRejectedAfterReferences", "declinedOffer");
        assertThat(defaultsOf(catalog)).containsExactly("identified", "contacted", "interested", "shortlisted",
                "references");
        assertThat(systemOf(catalog)).containsExactlyInAnyOrder("identified", "outOfScope", "offLimits",
                "contacted", "engaged", "notInterested", "interested");
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
                {"label":"Assessment"}""").getResponse().getStatus()).isEqualTo(409);
    }

    @Test
    @DisplayName("a seeded status is renamed, recoloured and retired like any other")
    void aSeededStatusIsLikeAnyOther() throws Exception {
        String offLimits = statusIn(catalog(), "offLimits").get("id").asText();

        JsonNode renamed = body(write(admin, "PATCH", PIPELINE + "/statuses/" + offLimits, """
                {"label":"Do not approach","colour":"signal"}"""));
        assertThat(statusIn(renamed, "offLimits").get("label").asText()).isEqualTo("Do not approach");

        JsonNode retired = body(write(admin, "PATCH", PIPELINE + "/statuses/" + offLimits, """
                {"retired":true}"""));
        assertThat(statusIn(retired, "offLimits").get("retired").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a stage with live statuses retires with them when asked, its seeded ones included")
    void aStageRetiresWithItsStatuses() throws Exception {
        String contacted = stageIn(catalog(), "contacted").get("id").asText();

        assertThat(codeOf(write(admin, "PATCH", PIPELINE + "/stages/" + contacted, """
                {"retired":true}"""))).isEqualTo("PIPELINE_STAGE_NOT_EMPTY");
        JsonNode catalog = body(write(admin, "PATCH", PIPELINE + "/stages/" + contacted, """
                {"retired":true,"retireStatuses":true}"""));

        JsonNode stage = stageIn(catalog, "contacted");
        assertThat(stage.get("retired").asBoolean()).isTrue();
        stage.get("statuses").forEach(status -> {
            assertThat(status.get("retired").asBoolean()).isTrue();
            assertThat(status.get("isDefault").asBoolean()).isFalse();
        });
    }

    @Test
    @DisplayName("the last live in-process status new people could start at is never retired, nor its stage with it")
    void theLastStartingStatusStays() throws Exception {
        JsonNode catalog = catalog();
        for (String stageKey : List.of("contacted", "interested", "qualified")) {
            write(admin, "PATCH", PIPELINE + "/stages/" + stageIn(catalog, stageKey).get("id").asText(), """
                    {"retired":true,"retireStatuses":true}""");
        }
        for (JsonNode status : stageIn(catalog, "shortlisted").get("statuses")) {
            write(admin, "PATCH", PIPELINE + "/statuses/" + status.get("id").asText(), """
                    {"retired":true}""");
        }
        String identifiedStage = stageIn(catalog, "identified").get("id").asText();
        write(admin, "PATCH", PIPELINE + "/statuses/" + statusIn(catalog, "source").get("id").asText(), """
                {"retired":true}""");

        assertThat(codeOf(write(admin, "PATCH", PIPELINE + "/statuses/" + statusIn(catalog, "identified")
                .get("id").asText(), """
                {"retired":true}"""))).isEqualTo("PIPELINE_STATUS_REQUIRED");
        assertThat(codeOf(write(admin, "PATCH", PIPELINE + "/stages/" + identifiedStage, """
                {"retired":true,"retireStatuses":true}"""))).isEqualTo("PIPELINE_STATUS_REQUIRED");
        assertThat(statusIn(catalog(), "identified").get("retired").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a retire preview counts who holds each status and names the automations it would stop")
    void aRetirePreviewSaysWhatItTouches() throws Exception {
        String projectId = mandate("Chief Financial Officer");
        add(projectId, """
                {"fullName":"Rania Haddad","status":"contacted"}""");
        add(projectId, """
                {"fullName":"Omar Saleh","status":"contacted"}""");
        JsonNode catalog = catalog();
        String contactedStage = stageIn(catalog, "contacted").get("id").asText();
        String contacted = statusIn(catalog, "contacted").get("id").asText();

        JsonNode preview = body(mvc.perform(get(PIPELINE + "/stages/" + contactedStage + "/retire-preview")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());

        Map<String, Long> holders = new HashMap<>();
        preview.get("statuses").forEach(row -> holders.put(row.get("label").asText(), row.get("holders").asLong()));
        assertThat(holders).containsEntry("Awaiting reply", 2L).containsEntry("Call being arranged", 0L).hasSize(6);
        List<String> triggers = new ArrayList<>();
        preview.get("automations").forEach(row -> triggers.add(row.get("trigger").asText()));
        assertThat(triggers).containsExactlyInAnyOrder("emailSent", "callBooked");

        JsonNode statusPreview = body(mvc.perform(get(PIPELINE + "/statuses/" + contacted + "/retire-preview")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(statusPreview.get("statuses")).hasSize(1);
        assertThat(statusPreview.get("automations").get(0).get("trigger").asText()).isEqualTo("emailSent");
    }

    @Test
    @DisplayName("a stage keeps exactly one default, and retiring it hands the mark to the next live status in process")
    void oneDefaultPerStage() throws Exception {
        JsonNode catalog = catalog();
        String arranged = statusIn(catalog, "clientInterviewArranged").get("id").asText();
        String first = statusIn(catalog, "presentedAwaitingFeedback").get("id").asText();
        write(admin, "PATCH", PIPELINE + "/statuses/" + first, """
                {"outOfProcess":true}""");

        catalog = body(write(admin, "PATCH", PIPELINE + "/statuses/" + arranged, """
                {"isDefault":true}"""));
        assertThat(defaultsOfStage(stageIn(catalog, "qualified"))).containsExactly("clientInterviewArranged");

        catalog = body(write(admin, "PATCH", PIPELINE + "/statuses/" + arranged, """
                {"retired":true}"""));
        assertThat(statusIn(catalog, "clientInterviewArranged").get("retired").asBoolean()).isTrue();
        assertThat(defaultsOfStage(stageIn(catalog, "qualified"))).containsExactly("clientInterviewBeingArranged");
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
        assertThat(keysOf(stageIn(catalog, "interested").get("statuses")).getLast())
                .isEqualTo("presentedAwaitingFeedback");
        assertThat(defaultsOfStage(stageIn(catalog, "interested"))).containsExactly("interested");
    }

    @Test
    @DisplayName("a new stage lands after the live ones")
    void aNewStageLandsLast() throws Exception {
        JsonNode catalog = body(write(admin, "POST", PIPELINE + "/stages", """
                {"label":"Onboarding"}"""));

        assertThat(keysOf(catalog.get("stages"))).containsExactly("identified", "contacted", "interested",
                "qualified", "shortlisted", "onboarding");
    }

    @Test
    @DisplayName("an old closed stage is a stage like any other: reordered anywhere, and retired with its statuses")
    void anOldClosedStageIsLikeAnyOther() throws Exception {
        formerClosedStage("Declined", "notAFit", "noResponse");
        JsonNode catalog = body(write(admin, "POST", PIPELINE + "/stages", """
                {"label":"Onboarding"}"""));
        assertThat(keysOf(catalog.get("stages"))).containsExactly("identified", "contacted", "interested",
                "qualified", "shortlisted", "declined", "onboarding");

        List<String> ids = new ArrayList<>();
        catalog.get("stages").forEach(stage -> ids.add(stage.get("id").asText()));
        List<String> declinedFirst = new ArrayList<>(ids);
        declinedFirst.addFirst(declinedFirst.remove(5));
        catalog = body(write(admin, "PUT", PIPELINE + "/stages/order", idsJson(declinedFirst)));
        assertThat(keysOf(catalog.get("stages")).getFirst()).isEqualTo("declined");
        assertThat(add(mandate("Chief Financial Officer"), """
                {"fullName":"Starts In Process"}""").get("status").asText())
                .as("a stage of ways out alone gives nobody a start").isEqualTo("identified");

        String declined = stageIn(catalog, "declined").get("id").asText();
        catalog = body(write(admin, "PATCH", PIPELINE + "/stages/" + declined, """
                {"retired":true,"retireStatuses":true}"""));
        assertThat(stageIn(catalog, "declined").get("retired").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a status is flagged out of process and back, and a new one may start flagged")
    void aStatusIsFlaggedOutOfProcess() throws Exception {
        JsonNode catalog = catalog();
        String callArranged = statusIn(catalog, "callArranged").get("id").asText();

        catalog = body(write(admin, "PATCH", PIPELINE + "/statuses/" + callArranged, """
                {"outOfProcess":true}"""));
        assertThat(statusIn(catalog, "callArranged").get("outOfProcess").asBoolean()).isTrue();
        catalog = body(write(admin, "PATCH", PIPELINE + "/statuses/" + callArranged, """
                {"outOfProcess":false}"""));
        assertThat(statusIn(catalog, "callArranged").get("outOfProcess").asBoolean()).isFalse();

        String research = stageIn(catalog, "identified").get("id").asText();
        catalog = body(write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"Retired from work","outOfProcess":true}""".formatted(research)));
        assertThat(statusIn(catalog, "retiredFromWork").get("outOfProcess").asBoolean()).isTrue();
        assertThat(statusIn(catalog, "retiredFromWork").get("isDefault").asBoolean()).isFalse();

        String onboarding = stageIn(body(write(admin, "POST", PIPELINE + "/stages", """
                {"label":"Onboarding"}""")), "onboarding").get("id").asText();
        catalog = body(write(admin, "PATCH", PIPELINE + "/statuses/" + statusIn(catalog, "hold").get("id").asText(),
                """
                {"stageId":"%s","outOfProcess":true}""".formatted(onboarding)));
        assertThat(statusIn(catalog, "hold").get("outOfProcess").asBoolean()).isTrue();
        assertThat(statusIn(catalog, "hold").get("isDefault").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("an active stage's default stays in process: it is never flagged, nor a flagged status made default")
    void theDefaultStaysInProcess() throws Exception {
        JsonNode catalog = catalog();

        assertThat(codeOf(write(admin, "PATCH", PIPELINE + "/statuses/" + statusIn(catalog, "contacted").get("id")
                .asText(), """
                {"outOfProcess":true}"""))).isEqualTo("PIPELINE_DEFAULT_IN_PROCESS");
        assertThat(codeOf(write(admin, "PATCH", PIPELINE + "/statuses/" + statusIn(catalog, "notInterested")
                .get("id").asText(), """
                {"isDefault":true}"""))).isEqualTo("PIPELINE_DEFAULT_IN_PROCESS");

        String onboarding = stageIn(body(write(admin, "POST", PIPELINE + "/stages", """
                {"label":"Onboarding"}""")), "onboarding").get("id").asText();
        catalog = body(write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"Fell through","outOfProcess":true}""".formatted(onboarding)));
        assertThat(statusIn(catalog, "fellThrough").get("isDefault").asBoolean()).isFalse();
        catalog = body(write(admin, "POST", PIPELINE + "/statuses", """
                {"stageId":"%s","label":"Started"}""".formatted(onboarding)));
        assertThat(defaultsOfStage(stageIn(catalog, "onboarding"))).containsExactly("started");
    }

    @Test
    @DisplayName("a stage of ways out takes a default only once a status there is back in process")
    void aStageOfWaysOutTakesADefaultOnlyInProcess() throws Exception {
        formerClosedStage("Declined", "notAFit");
        JsonNode catalog = catalog();
        String declined = stageIn(catalog, "declined").get("id").asText();

        catalog = body(write(admin, "PATCH", PIPELINE + "/statuses/" + statusIn(catalog, "withdrew").get("id")
                .asText(), """
                {"stageId":"%s"}""".formatted(declined)));
        assertThat(statusIn(catalog, "withdrew").get("outOfProcess").asBoolean()).isTrue();
        assertThat(statusIn(catalog, "withdrew").get("isDefault").asBoolean()).isFalse();

        catalog = body(write(admin, "PATCH", PIPELINE + "/statuses/" + statusIn(catalog, "notAFit").get("id")
                .asText(), """
                {"outOfProcess":false}"""));
        assertThat(statusIn(catalog, "notAFit").get("outOfProcess").asBoolean()).isFalse();
        assertThat(defaultsOfStage(stageIn(catalog, "declined"))).containsExactly("notAFit");
    }

    @Test
    @DisplayName("statuses reorder within their stage, and a reorder must name every live one")
    void statusesReorderWithinTheirStage() throws Exception {
        JsonNode contacted = stageIn(catalog(), "contacted");
        List<String> ids = new ArrayList<>();
        contacted.get("statuses").forEach(status -> ids.add(status.get("id").asText()));
        String url = PIPELINE + "/stages/" + contacted.get("id").asText() + "/statuses/order";

        JsonNode catalog = body(write(admin, "PUT", url, idsJson(ids.reversed())));

        assertThat(keysOf(stageIn(catalog, "contacted").get("statuses"))).containsExactly("unresponsive",
                "notInterested", "callArranged", "callBeingArranged", "engaged", "contacted");
        assertThat(codeOf(write(admin, "PUT", url, idsJson(ids.subList(1, ids.size())))))
                .isEqualTo("PIPELINE_ORDER_INVALID");
    }

    private static List<String> keysOf(JsonNode rows) {
        List<String> keys = new ArrayList<>();
        rows.forEach(row -> keys.add(row.get("key").asText()));
        return keys;
    }

    private static List<String> labelsOf(JsonNode rows) {
        List<String> labels = new ArrayList<>();
        rows.forEach(row -> labels.add(row.get("label").asText()));
        return labels;
    }

    private static List<String> flaggedOf(JsonNode catalog) {
        return statusKeysWhere(catalog, "outOfProcess");
    }

    private static List<String> defaultsOf(JsonNode catalog) {
        return statusKeysWhere(catalog, "isDefault");
    }

    private static List<String> systemOf(JsonNode catalog) {
        return statusKeysWhere(catalog, "system");
    }

    private static List<String> statusKeysWhere(JsonNode catalog, String field) {
        List<String> keys = new ArrayList<>();
        catalog.get("stages").forEach(stage -> stage.get("statuses").forEach(status -> {
            if (status.get(field).asBoolean()) {
                keys.add(status.get("key").asText());
            }
        }));
        return keys;
    }

    private static List<String> defaultsOfStage(JsonNode stage) {
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
