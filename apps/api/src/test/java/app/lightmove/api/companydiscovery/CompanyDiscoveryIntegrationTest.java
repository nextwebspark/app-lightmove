package app.lightmove.api.companydiscovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingCompanyEnricher;
import app.lightmove.api.companydiscovery.model.DiscoveredCandidate;
import app.lightmove.api.enrichment.company.model.VendorCompanyRecord;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * AI Research end to end: a question in, rows out, and the rows filed through the doors that already
 * exist. The assertion that matters is that a company nobody holds a record of arrives with its name
 * and no figures — and stays that way once filed.
 */
@IntegrationTest
class CompanyDiscoveryIntegrationTest extends FlowTestSupport {

    private static final String DISCOVER_URL = "/api/v1/companies/discover";

    @Autowired JdbcTemplate db;
    @Autowired private RecordingCompanyEnricher companyEnricher;

    private ApolloUniverse universe;
    private String adminToken;
    private String projectId;

    @BeforeEach
    void freshUniverse() throws Exception {
        universe = new ApolloUniverse(db);
        universe.reset();
        companyEnricher.clear();
    }

    @Test
    @DisplayName("each row says where its figures came from, and an unheld company has none")
    void eachRowSaysWhereItsFiguresCameFrom() throws Exception {
        mandate("Discovery Firm");
        universe.company("a1", "ACWA Power").industry("utilities").country("Saudi Arabia")
                .employees(3400).website("https://acwapower.example")
                .linkedin("http://www.linkedin.com/company/acwa-power").insert();
        // Scripted for this slug alone: Shamal Energy must come back empty, and a double answering
        // the same record to every slug would resolve the row this case exists to leave unresolved.
        companyEnricher.answerFor("nebras-power", new VendorCompanyRecord("nebras-power",
                "Nebras Power", "Utilities", "Qatar", "Doha", 410, "https://nebras.example",
                "https://www.linkedin.com/company/nebras-power", 2014, null, null, List.of(), null));

        discovery.answerWith(
                candidate("ACWA", "https://www.linkedin.com/company/acwa-power/"),
                candidate("Nebras Power", "https://www.linkedin.com/company/nebras-power/"),
                candidate("Shamal Energy", "https://www.linkedin.com/company/shamal-energy/"));

        JsonNode answer = body(mvc.perform(post(DISCOVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Who are the large IPPs in the Gulf?","projectId":"%s"}
                                """.formatted(projectId)))
                .andExpect(status().isOk())
                .andReturn());

        JsonNode rows = answer.get("companies");
        assertThat(rows).hasSize(3);

        JsonNode fromUniverse = rows.get(0);
        assertThat(fromUniverse.get("source").asText()).isEqualTo("universe");
        assertThat(fromUniverse.get("apolloAccountId").asText()).isEqualTo("a1");
        // The market's own spelling and the market's own figures, not the model's shorter name.
        assertThat(fromUniverse.get("companyName").asText()).isEqualTo("ACWA Power");
        assertThat(fromUniverse.get("numEmployees").asInt()).isEqualTo(3400);

        JsonNode researched = rows.get(1);
        assertThat(researched.get("source").asText()).isEqualTo("researched");
        assertThat(researched.get("numEmployees").asInt()).isEqualTo(410);
        assertThat(researched.get("apolloAccountId").isNull()).isTrue();

        JsonNode web = rows.get(2);
        assertThat(web.get("source").asText()).isEqualTo("web");
        assertThat(web.get("unresolved").asBoolean()).isTrue();
        assertThat(web.get("companyName").asText()).isEqualTo("Shamal Energy");
        assertThat(web.get("numEmployees").isNull()).isTrue();
        assertThat(web.get("industry").isNull()).isTrue();
        assertThat(web.get("companyCountry").isNull()).isTrue();

        // The mode is on the wire, not only in the log: an empty market and an unreachable provider
        // are different answers.
        assertThat(answer.get("mode").asText()).isEqualTo("GROUNDED_STRUCTURED");

        // The resolution order is what this feature bills on: the universe answers first and nothing
        // is bought for a row it already covered, so ACWA never reaches the vendor and the two the
        // universe missed do.
        assertThat(companyEnricher.fetchedSlugs())
                .containsExactlyInAnyOrder("nebras-power", "shamal-energy");
    }

    @Test
    @DisplayName("a company the mandate already holds says so without changing where its figures came from")
    void alreadyInMandateIsItsOwnFlag() throws Exception {
        mandate("Already Held Firm");
        universe.company("a1", "ACWA Power").industry("utilities")
                .linkedin("http://www.linkedin.com/company/acwa-power").insert();

        mvc.perform(post("/api/v1/projects/" + projectId + "/triage")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountId":"a1"}"""))
                .andExpect(status().isCreated());

        discovery.answerWith(candidate("ACWA Power", "https://www.linkedin.com/company/acwa-power/"));

        JsonNode row = rowsFor("""
                {"question":"Gulf IPPs","projectId":"%s"}""".formatted(projectId)).get(0);

        assertThat(row.get("source").asText()).isEqualTo("universe");
        assertThat(row.get("alreadyInMandate").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("without a mandate the answer claims nothing about what is already held")
    void noMandateMeansNoClaim() throws Exception {
        mandate("No Mandate Firm");
        discovery.answerWith(candidate("Shamal Energy", null));

        JsonNode row = rowsFor("""
                {"question":"Gulf IPPs"}""").get(0);

        assertThat(row.get("alreadyInMandate").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("an unresolved row files as WEB with nothing filled in, and a resolved one by id")
    void anAnswerFilesThroughTheDoorsThatAlreadyExist() throws Exception {
        mandate("Filing Firm");
        universe.company("a1", "ACWA Power").industry("utilities")
                .linkedin("http://www.linkedin.com/company/acwa-power").insert();

        // The resolved half goes through the bulk door with its id, so V34's CHECK stays honest.
        mvc.perform(post("/api/v1/projects/" + projectId + "/triage/bulk")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["a1"],"status":"inUniverse"}"""))
                .andExpect(status().isOk());

        // The unresolved half goes through capture, carrying only what a record supplied — which for
        // this row is nothing but the name, the page and the reason.
        mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Shamal Energy","source":"web",
                                 "companyLinkedinUrl":"https://www.linkedin.com/company/shamal-energy/",
                                 "sourceUrl":"https://example.test/gcc-ipps",
                                 "note":"Regional services player"}"""))
                .andExpect(status().isCreated());

        JsonNode filed = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?status=inUniverse")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("companies");

        assertThat(filed).hasSize(2);
        assertThat(sourcesOf(filed)).containsExactlyInAnyOrder("strategy", "web");
    }

    @Test
    @DisplayName("a provider that answered nothing is not an empty market")
    void anUnreachableProviderSaysSo() throws Exception {
        mandate("Outage Firm");
        discovery.answerIn(app.lightmove.api.companydiscovery.constant.DiscoveryMode.UNAVAILABLE);

        JsonNode answer = body(mvc.perform(post(DISCOVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Gulf IPPs"}"""))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(answer.get("companies")).isEmpty();
        assertThat(answer.get("mode").asText()).isEqualTo("UNAVAILABLE");
    }

    @Test
    @DisplayName("a limit past the ceiling is refused, never quietly narrowed")
    void anOverLimitIsRefused() throws Exception {
        mandate("Limit Firm");

        mvc.perform(post(DISCOVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Gulf IPPs","limit":500}"""))
                .andExpect(status().isBadRequest());

        assertThat(discovery.questionsAsked())
                .as("refused before anything was asked, so nothing was billed")
                .isEmpty();
    }

    @Test
    @DisplayName("an unconfigured deployment refuses rather than answering an empty market")
    void anUnconfiguredDeploymentRefuses() throws Exception {
        mandate("Unconfigured Firm");
        discovery.offer(false);

        mvc.perform(post(DISCOVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question":"Gulf IPPs"}"""))
                .andExpect(status().isServiceUnavailable());

        JsonNode config = body(mvc.perform(get("/api/v1/companies/discover/config")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(config.get("offered").asBoolean()).isFalse();
    }

    private JsonNode rowsFor(String requestBody) throws Exception {
        return body(mvc.perform(post(DISCOVER_URL)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isOk())
                .andReturn()).get("companies");
    }

    private static List<String> sourcesOf(JsonNode companies) {
        return companies.valueStream().map(company -> company.get("source").asText()).toList();
    }

    private static DiscoveredCandidate candidate(String name, String linkedin) {
        return new DiscoveredCandidate(name, linkedin, null, "https://example.test/gcc-ipps",
                "Regional services player", 80);
    }

    private void mandate(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        adminToken = login(alok);

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Discovery Client"}"""))
                .andReturn()).get("id").asText();
        projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Energy"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();
    }
}
