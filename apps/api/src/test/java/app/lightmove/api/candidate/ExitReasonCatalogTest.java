package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * A workspace's reasons for leaving the process (V227): seeded linked to the ways out they belong to, read by staff
 * alone, edited by an admin, offered only at statuses out of process, and retired rather than deleted.
 */
@IntegrationTest
class ExitReasonCatalogTest extends PipelineFlowSupport {

    private static final String REASONS = PIPELINE + "/reasons";

    @BeforeEach
    void aFirm() throws Exception {
        firm("Exit Reasons Firm");
    }

    @Test
    @DisplayName("a workspace starts with the business's list, each reason offered at the ways out it belongs to")
    void aWorkspaceStartsWithTheSeededList() throws Exception {
        JsonNode catalog = catalog();
        JsonNode reasons = reasons(admin);

        assertThat(labelsOf(reasons)).containsExactly("Location", "Senior", "Junior", "Technical fit", "Cultural fit",
                "Language", "Timing", "Company", "Scope", "Compensation", "Negative feedback", "Another offer",
                "Other");
        assertThat(reasonIn(reasons, "other").get("requiresNote").asBoolean()).isTrue();
        assertThat(reasonIn(reasons, "location").get("requiresNote").asBoolean()).isFalse();

        assertThat(offeredAt(reasons, catalog, "withdrewOffer")).containsExactly("Compensation", "Another offer",
                "Other");
        assertThat(offeredAt(reasons, catalog, "withdrew")).isEqualTo(offeredAt(reasons, catalog, "withdrewShortlist"));
        assertThat(offeredAt(reasons, catalog, "outOfScope")).containsExactly("Location", "Senior", "Junior",
                "Technical fit", "Cultural fit", "Language", "Other");
        assertThat(offeredAt(reasons, catalog, "clientRejected")).containsExactly("Senior", "Junior",
                "Technical fit", "Cultural fit", "Negative feedback", "Other");
        assertThat(offeredAt(reasons, catalog, "offLimits")).as("a way out with no reasons asks for none").isEmpty();
        assertThat(offeredAt(reasons, catalog, "unresponsive")).isEmpty();
    }

    @Test
    @DisplayName("a member reads the reasons; a client seat is not told they exist, and only an admin edits them")
    void staffReadAndAnAdminEdits() throws Exception {
        String projectId = mandate("Chief Financial Officer");
        String member = member("maya");
        String client = clientOn(projectId);

        mvc.perform(get(REASONS).header("Authorization", "Bearer " + member)).andExpect(status().isOk());
        mvc.perform(get(REASONS).header("Authorization", "Bearer " + client)).andExpect(status().isNotFound());
        assertThat(write(member, "POST", REASONS, """
                {"label":"Visa"}""").getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("a reason is added last, named uniquely, and offered only at live statuses out of process")
    void aReasonIsAddedLast() throws Exception {
        JsonNode catalog = catalog();
        String notInterested = statusIn(catalog, "notInterested").get("id").asText();
        String contacted = statusIn(catalog, "contacted").get("id").asText();

        JsonNode reasons = body(write(admin, "POST", REASONS, """
                {"label":"Visa","requiresNote":true,"statusIds":["%s"]}""".formatted(notInterested)));

        JsonNode visa = reasonIn(reasons, "visa");
        assertThat(labelsOf(reasons)).as("the starting list is there before the first write lands")
                .hasSize(14).endsWith("Other", "Visa");
        assertThat(visa.get("requiresNote").asBoolean()).isTrue();
        assertThat(offeredAt(reasons, catalog, "notInterested")).contains("Visa");

        assertThat(codeOf(write(admin, "POST", REASONS, """
                {"label":"VISA"}"""))).isEqualTo("EXIT_REASON_TAKEN");
        assertThat(codeOf(write(admin, "POST", REASONS, """
                {"label":"Relocation","statusIds":["%s"]}""".formatted(contacted))))
                .as("a status in process asks for no reason").isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(write(admin, "POST", REASONS, """
                {"label":"   "}"""))).isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("an admin whose first call is a write still finds the whole starting list")
    void aFirstWriteFindsTheStartingList() throws Exception {
        JsonNode reasons = body(write(admin, "POST", REASONS, """
                {"label":"Notice period"}"""));

        assertThat(labelsOf(reasons)).hasSize(14).startsWith("Location").endsWith("Other", "Notice period");
        assertThat(offeredAt(reasons, catalog(), "withdrewOffer")).containsExactly("Compensation", "Another offer",
                "Other");
    }

    @Test
    @DisplayName("a status brought back into process offers no reason, even once it is a way out again")
    void aStatusBackInProcessOffersNoReason() throws Exception {
        JsonNode catalog = catalog();
        String notInterested = statusIn(catalog, "notInterested").get("id").asText();
        assertThat(offeredAt(reasons(admin), catalog, "notInterested")).isNotEmpty();

        write(admin, "PATCH", PIPELINE + "/statuses/" + notInterested, """
                {"outOfProcess":false}""");
        assertThat(offeredAt(reasons(admin), catalog, "notInterested")).isEmpty();

        write(admin, "PATCH", PIPELINE + "/statuses/" + notInterested, """
                {"outOfProcess":true}""");
        assertThat(offeredAt(reasons(admin), catalog, "notInterested"))
                .as("its reasons are ticked again by an admin, never restored on their own").isEmpty();
    }

    @Test
    @DisplayName("a reason is renamed, asked a note, relinked from its own side, and retired without losing its place")
    void aReasonIsEditedFromItsOwnSide() throws Exception {
        JsonNode catalog = catalog();
        JsonNode reasons = reasons(admin);
        String compensation = reasonIn(reasons, "compensation").get("id").asText();
        String withdrewOffer = statusIn(catalog, "withdrewOffer").get("id").asText();
        String clientRejected = statusIn(catalog, "clientRejected").get("id").asText();

        reasons = body(write(admin, "PATCH", REASONS + "/" + compensation, """
                {"label":"Package","requiresNote":true,"statusIds":["%s","%s"]}"""
                .formatted(withdrewOffer, clientRejected)));

        JsonNode renamed = reasonIn(reasons, "compensation");
        assertThat(renamed.get("label").asText()).isEqualTo("Package");
        assertThat(renamed.get("requiresNote").asBoolean()).isTrue();
        assertThat(offeredAt(reasons, catalog, "clientRejected")).contains("Package");
        assertThat(offeredAt(reasons, catalog, "notInterested")).doesNotContain("Package");

        assertThat(codeOf(write(admin, "PATCH", REASONS + "/" + compensation, """
                {"label":"other"}"""))).isEqualTo("EXIT_REASON_TAKEN");

        reasons = body(write(admin, "PATCH", REASONS + "/" + compensation, """
                {"retired":true}"""));
        assertThat(reasonIn(reasons, "compensation").get("retired").asBoolean()).isTrue();
        assertThat(offeredAt(reasons, catalog, "withdrewOffer")).as("a link is kept on a retired reason")
                .contains("Package");
        assertThat(codeOf(write(admin, "POST", REASONS, """
                {"label":"Package"}"""))).isEqualTo("EXIT_REASON_RETIRED");

        reasons = body(write(admin, "PATCH", REASONS + "/" + compensation, """
                {"retired":false}"""));
        assertThat(reasonIn(reasons, "compensation").get("retired").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("a status retired after it was linked stays linked on a resave, but is never newly linked")
    void aRetiredStatusIsKeptNotAdded() throws Exception {
        JsonNode catalog = catalog();
        String withdrewOffer = statusIn(catalog, "withdrewOffer").get("id").asText();
        String declinedOffer = statusIn(catalog, "declinedOffer").get("id").asText();
        String anotherOffer = reasonIn(reasons(admin), "anotherOffer").get("id").asText();
        write(admin, "PATCH", PIPELINE + "/statuses/" + withdrewOffer, """
                {"retired":true}""");
        write(admin, "PATCH", PIPELINE + "/statuses/" + declinedOffer, """
                {"retired":true}""");

        JsonNode reasons = body(write(admin, "PATCH", REASONS + "/" + anotherOffer, """
                {"statusIds":["%s"]}""".formatted(withdrewOffer)));
        assertThat(reasonIn(reasons, "anotherOffer").get("statusIds")).hasSize(1);

        assertThat(codeOf(write(admin, "PATCH", REASONS + "/" + anotherOffer, """
                {"statusIds":["%s","%s"]}""".formatted(withdrewOffer, declinedOffer))))
                .isEqualTo("VALIDATION_FAILED");
    }

    @Test
    @DisplayName("reasons reorder, and a reorder must name every live one")
    void reasonsReorder() throws Exception {
        List<String> ids = new ArrayList<>();
        reasons(admin).get("reasons").forEach(reason -> ids.add(reason.get("id").asText()));

        JsonNode reasons = body(write(admin, "PUT", REASONS + "/order", idsJson(ids.reversed())));

        assertThat(labelsOf(reasons).getFirst()).isEqualTo("Other");
        assertThat(codeOf(write(admin, "PUT", REASONS + "/order", idsJson(ids.subList(1, ids.size())))))
                .isEqualTo("PIPELINE_ORDER_INVALID");
    }

    private JsonNode reasons(String token) throws Exception {
        return body(mvc.perform(get(REASONS).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static JsonNode reasonIn(JsonNode reasons, String key) {
        for (JsonNode reason : reasons.get("reasons")) {
            if (reason.get("key").asText().equals(key)) {
                return reason;
            }
        }
        throw new AssertionError("no reason " + key + " in " + reasons);
    }

    private static List<String> labelsOf(JsonNode reasons) {
        List<String> labels = new ArrayList<>();
        reasons.get("reasons").forEach(reason -> labels.add(reason.get("label").asText()));
        return labels;
    }

    /** The reasons a status offers, in the list's own order. */
    private static List<String> offeredAt(JsonNode reasons, JsonNode catalog, String statusKey) {
        String statusId = statusIn(catalog, statusKey).get("id").asText();
        List<String> offered = new ArrayList<>();
        for (JsonNode reason : reasons.get("reasons")) {
            for (JsonNode linked : reason.get("statusIds")) {
                if (linked.asText().equals(statusId)) {
                    offered.add(reason.get("label").asText());
                }
            }
        }
        return offered;
    }

    private static String idsJson(List<String> ids) {
        return "{\"ids\":[" + String.join(",", ids.stream().map(id -> "\"" + id + "\"").toList()) + "]}";
    }
}
