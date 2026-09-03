package app.lightmove.api.triagecompany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingCompanyEnricher;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * How a plugin-captured company resolves into the universe: the full market row when the Apollo
 * universe carries it (matched by LinkedIn slug, or by a name only when unique), and a captured row
 * researched asynchronously through the company enricher when it does not. The consultant's own
 * typed fields never lose to either.
 */
@IntegrationTest
@Import({RecordingEmailSender.Config.class, RecordingCompanyEnricher.Config.class})
class CompanyCaptureResolutionIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;
    @Autowired private RecordingCompanyEnricher companyEnricher;

    private ApolloUniverse universe;
    private String adminToken;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
        companyEnricher.clear();
    }

    @Test
    @DisplayName("a captured company the universe carries lands as the full market row")
    void aCapturedCompanyTheUniverseCarriesLandsAsTheMarketRow() throws Exception {
        String projectId = mandate("Apollo Capture Firm");
        universe.company("a77", "Al Rawabi Dairy").industry("food & beverages")
                .country("United Arab Emirates").city("Dubai").employees(1200)
                .website("https://alrawabi.example")
                .linkedin("http://www.linkedin.com/company/alrawabi").insert();

        JsonNode captured = body(mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Al Rawabi","source":"extension",
                                 "companyLinkedinUrl":"https://www.linkedin.com/company/alrawabi/"}"""))
                .andExpect(status().isCreated())
                .andReturn());

        // The slug resolved it despite the shorter typed name; the row is the market snapshot.
        assertThat(captured.get("apolloAccountId").asText()).isEqualTo("a77");
        assertThat(captured.get("companyName").asText()).isEqualTo("Al Rawabi Dairy");
        assertThat(captured.get("industry").asText()).isEqualTo("food & beverages");
        assertThat(captured.get("companyCountry").asText()).isEqualTo("United Arab Emirates");
        assertThat(captured.get("numEmployees").asInt()).isEqualTo(1200);
        // Badged by the door it came through, carrying the market identity all the same.
        assertThat(captured.get("source").asText()).isEqualTo("extension");
        // A market-resolved row needs no vendor research.
        assertThat(companyEnricher.fetchedSlugs()).isEmpty();
    }

    @Test
    @DisplayName("a name the universe holds twice is ambiguous, and stays a hand-shaped row")
    void anAmbiguousNameStaysCaptured() throws Exception {
        String projectId = mandate("Ambiguous Name Firm");
        universe.company("a1", "Alpha Group").industry("retail").insert();
        universe.company("a2", "Alpha Group").industry("banking").insert();

        JsonNode captured = body(mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Alpha Group","source":"extension"}"""))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(captured.get("apolloAccountId").isNull()).isTrue();
        assertThat(captured.get("industry").isNull()).isTrue();
    }

    @Test
    @DisplayName("a company the market misses is researched in place, keeping what was typed")
    void aMarketMissIsResearchedInPlace() throws Exception {
        String projectId = mandate("Researched Capture Firm");
        companyEnricher.answerWith(new CapturedCompanyDetails("SampleCo", "Software Development",
                "Ireland", "Dublin", 841, null, "https://www.sampleco.example/",
                "https://www.linkedin.com/company/sampleco", 1993,
                "SampleCo is a leading provider of core systems.",
                "https://media.example.com/sampleco-logo.png", null, null));

        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"SampleCo","source":"extension",
                                 "companyCountry":"Éire",
                                 "companyLinkedinUrl":"https://www.linkedin.com/company/sampleco/"}"""))
                .andExpect(status().isCreated());

        assertThat(companyEnricher.fetchedSlugs()).containsExactly("sampleco");

        JsonNode researched = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?status=inUniverse")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("companies").get(0);

        // Research filled the blanks; the consultant's own typed country stood its ground.
        assertThat(researched.get("industry").asText()).isEqualTo("Software Development");
        assertThat(researched.get("companyCity").asText()).isEqualTo("Dublin");
        assertThat(researched.get("companyCountry").asText()).isEqualTo("Éire");
        assertThat(researched.get("numEmployees").asInt()).isEqualTo(841);
        assertThat(researched.get("foundedYear").asInt()).isEqualTo(1993);
        assertThat(researched.get("logoUrl").asText()).isEqualTo("https://media.example.com/sampleco-logo.png");
        assertThat(researched.get("apolloAccountId").isNull()).isTrue();
    }

    @Test
    @DisplayName("a market row the mandate already holds is refused, not duplicated or re-audited")
    void aHeldMarketRowIsRefused() throws Exception {
        String projectId = mandate("Held Market Row Firm");
        universe.company("a77", "Al Rawabi Dairy").industry("food & beverages")
                .linkedin("http://www.linkedin.com/company/alrawabi").insert();

        // Held under the market's own name, which the typed name does not match.
        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Al Rawabi Dairy","source":"extension",
                                 "companyLinkedinUrl":"https://www.linkedin.com/company/alrawabi/"}"""))
                .andExpect(status().isCreated());

        // The typed name differs, so the first guard passes — but the row would land under the
        // market's name, which the mandate already holds. Refused rather than duplicated, and the
        // stage and note the caller asked for are not silently dropped onto the existing row.
        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Al Rawabi","source":"extension","status":"shortlisted",
                                 "note":"CEO met at GITEX",
                                 "companyLinkedinUrl":"https://www.linkedin.com/company/alrawabi/"}"""))
                .andExpect(status().isConflict());

        JsonNode companies = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?status=inUniverse")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("companies");
        assertThat(companies).hasSize(1);
    }

    @Test
    @DisplayName("a market-resolved capture is the export's row and cannot be rewritten")
    void aMarketResolvedCaptureIsNotEditable() throws Exception {
        String projectId = mandate("Market Row Edit Firm");
        universe.company("a88", "Almarai").industry("food & beverages")
                .linkedin("http://www.linkedin.com/company/almarai").insert();

        String companyId = body(mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Almarai","source":"extension",
                                 "companyLinkedinUrl":"https://www.linkedin.com/company/almarai/"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        // Badged extension, but it carries the export's snapshot and id — editing it would rewrite
        // the market's record of a company the ETL owns and re-keys.
        mvc.perform(put("/api/v1/projects/" + projectId + "/triage/" + companyId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"Almarai (our own figures)","industry":"retail"}"""))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("a capture without a LinkedIn page has nothing to research")
    void aCaptureWithoutALinkedInPageIsNotResearched() throws Exception {
        String projectId = mandate("Unresearchable Capture Firm");

        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"A Company With No Page"}"""))
                .andExpect(status().isCreated());

        assertThat(companyEnricher.fetchedSlugs()).isEmpty();
    }

    private static String captureUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/triage/capture";
    }

    private String mandate(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        adminToken = login(alok);

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Capture Client"}"""))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();
    }
}
