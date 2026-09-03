package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.RecordingProfileEnricher;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.candidate.model.EnrichedPhoto;
import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * A plugin capture is researched in place: the POST returns immediately with what the plugin read,
 * and the enrichment worker fills the row in behind it — here inline, because {@code @Async} work
 * runs on the calling thread in tests (see {@code SynchronousAuditWrites}).
 *
 * <p>What must hold: only an extension capture with a real profile URL spends a research call, a
 * provider failure costs the capture nothing, and research naming an employer files that company
 * into the mandate's universe with the person mapped to it.
 */
@IntegrationTest
@Import({RecordingEmailSender.Config.class, RecordingProfileEnricher.Config.class})
class CandidateEnrichmentIntegrationTest extends FlowTestSupport {

    private static final byte[] PHOTO_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};

    private static final EnrichedProfile RESEARCH = new EnrichedProfile(
            "Group CFO", "Finance leader across GCC retail.", "Al Rawabi Dairy",
            "https://www.linkedin.com/company/alrawabi/", "https://media.example.com/alrawabi.png",
            "Dubai", "United Arab Emirates",
            List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2021 – Present")),
            List.of(new CandidateEducationEntry("AUC", "MBA, Finance", "2010 - 2012")),
            List.of("Financial Planning"), List.of("English", "Arabic"),
            new EnrichedPhoto(PHOTO_BYTES, "image/jpeg"));

    @Autowired private RecordingProfileEnricher enricher;
    @Autowired JdbcTemplate db;

    private ApolloUniverse universe;
    private String adminToken;

    @BeforeEach
    void resetTheProvider() {
        enricher.clear();
        // The employer resolution reads the Apollo universe, so this suite owns its contents.
        universe = new ApolloUniverse(db);
        universe.reset();
    }

    @Test
    @DisplayName("an employer the universe carries maps the person to the full market row")
    void anEmployerTheUniverseCarriesMapsToTheMarketRow() throws Exception {
        String projectId = mandate("Apollo Employer Firm");
        universe.company("a42", "Al Rawabi Dairy").industry("food & beverages")
                .country("United Arab Emirates").city("Dubai").employees(1200)
                .linkedin("http://www.linkedin.com/company/alrawabi").insert();
        enricher.answerWith(RESEARCH);

        capture(projectId, "Sample Person", "sample-profile");

        JsonNode researched = firstCandidateOf(projectId);
        assertThat(researched.get("triageCompanyId").isNull()).isFalse();
        assertThat(researched.get("companyName").asText()).isEqualTo("Al Rawabi Dairy");

        JsonNode company = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?status=inUniverse")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("companies").get(0);
        assertThat(company.get("apolloAccountId").asText()).isEqualTo("a42");
        assertThat(company.get("industry").asText()).isEqualTo("food & beverages");
        assertThat(company.get("numEmployees").asInt()).isEqualTo(1200);
        assertThat(company.get("source").asText()).isEqualTo("extension");
    }

    @Test
    @DisplayName("a plugin capture comes back researched, employer filed into the universe")
    void aPluginCaptureComesBackResearched() throws Exception {
        String projectId = mandate("Enriched Capture Firm");
        enricher.answerWith(RESEARCH);

        String candidateId = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Sample Person","source":"extension",
                                 "linkedinUrl":"https://www.linkedin.com/in/sample-profile",
                                 "sourceUrl":"https://www.linkedin.com/in/sample-profile"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        assertThat(enricher.fetchedUrls())
                .containsExactly("https://www.linkedin.com/in/sample-profile");

        JsonNode researched = firstCandidateOf(projectId);
        assertThat(researched.get("title").asText()).isEqualTo("Group CFO");
        assertThat(researched.get("summary").asText()).isEqualTo("Finance leader across GCC retail.");
        assertThat(researched.get("locationCity").asText()).isEqualTo("Dubai");
        assertThat(researched.get("career")).hasSize(1);
        assertThat(researched.get("languages")).hasSize(2);
        assertThat(researched.get("enrichedAt").isNull()).isFalse();

        // The employer went into the universe — logo and all — and the person is mapped at it.
        assertThat(researched.get("companyName").asText()).isEqualTo("Al Rawabi Dairy");
        assertThat(researched.get("triageCompanyId").isNull()).isFalse();
        JsonNode company = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?status=inUniverse")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("companies").get(0);
        assertThat(company.get("companyName").asText()).isEqualTo("Al Rawabi Dairy");
        assertThat(company.get("source").asText()).isEqualTo("extension");
        assertThat(company.get("logoUrl").asText()).isEqualTo("https://media.example.com/alrawabi.png");

        // The downloaded photo is served back under its stored type.
        mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId + "/photo")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentType())
                        .isEqualTo("image/jpeg"))
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray())
                        .isEqualTo(PHOTO_BYTES));
    }

    @Test
    @DisplayName("a second capture at the same employer reuses the company row")
    void aSecondCaptureReusesTheEmployerRow() throws Exception {
        String projectId = mandate("Shared Employer Firm");
        enricher.answerWith(RESEARCH);

        capture(projectId, "Sample Person", "sample-profile");
        capture(projectId, "Second Person", "second-profile");

        JsonNode companies = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?status=inUniverse")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("companies");
        assertThat(companies).hasSize(1);
    }

    @Test
    @DisplayName("only an extension capture with a real profile URL spends a research call")
    void onlyARealProfileCaptureIsSpentOn() throws Exception {
        String projectId = mandate("Unspent Research Firm");
        enricher.answerWith(RESEARCH);

        // A manual add, however good its URL, is a researcher typing what they already know.
        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Typed Person",
                                 "linkedinUrl":"https://www.linkedin.com/in/typed-person"}"""))
                .andExpect(status().isCreated());

        // An extension capture without a URL has nothing to research.
        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Urlless Person","source":"extension"}"""))
                .andExpect(status().isCreated());

        // A URL that is not a linkedin.com profile page is not worth a billed call.
        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Elsewhere Person","source":"extension",
                                 "linkedinUrl":"https://example.com/in/elsewhere"}"""))
                .andExpect(status().isCreated());

        assertThat(enricher.fetchedUrls()).isEmpty();
    }

    @Test
    @DisplayName("a provider failure costs the capture nothing")
    void aProviderFailureCostsTheCaptureNothing() throws Exception {
        String projectId = mandate("Failed Research Firm");
        enricher.failWith(new IllegalStateException("The provider is having a bad minute"));

        capture(projectId, "Sample Person", "sample-profile");

        JsonNode captured = firstCandidateOf(projectId);
        assertThat(captured.get("fullName").asText()).isEqualTo("Sample Person");
        assertThat(captured.get("title").isNull()).isTrue();
        assertThat(captured.get("career")).isEmpty();
        assertThat(captured.get("enrichedAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("research naming no employer leaves the person unmapped, and no photo means 404")
    void researchWithoutAnEmployerLeavesThePersonUnmapped() throws Exception {
        String projectId = mandate("Employerless Research Firm");
        enricher.answerWith(new EnrichedProfile("Advisor", null, null, null, null, null, null,
                List.of(new CandidateCareerEntry("Somewhere", "Advisor", "2020 –")),
                null, null, null, null));

        String candidateId = capture(projectId, "Sample Person", "sample-profile");

        JsonNode researched = firstCandidateOf(projectId);
        assertThat(researched.get("triageCompanyId").isNull()).isTrue();
        assertThat(researched.get("title").asText()).isEqualTo("Advisor");

        mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId + "/photo")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    private String capture(String projectId, String fullName, String slug) throws Exception {
        return body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","source":"extension",
                                 "linkedinUrl":"https://www.linkedin.com/in/%s"}
                                """.formatted(fullName, slug)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private JsonNode firstCandidateOf(String projectId) throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("candidates").get(0);
    }

    private static String candidatesUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/candidates";
    }

    private String mandate(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        adminToken = login(alok);

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Research Client"}"""))
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
