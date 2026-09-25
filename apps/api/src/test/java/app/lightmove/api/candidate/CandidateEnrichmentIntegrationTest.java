package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingProfileEnricher;
import app.lightmove.api.StubChatModel;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.candidate.model.EnrichedPhoto;
import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
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
 * into the mandate's universe with the person mapped to it. Once the research lands, the AI enrichment
 * worker runs — on {@link StubChatModel}, whose default reply binds to nothing.
 */
@IntegrationTest
class CandidateEnrichmentIntegrationTest extends FlowTestSupport {

    private static final byte[] PHOTO_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};

    private static final EnrichedProfile RESEARCH = new EnrichedProfile(
            "Group CFO", "Finance leader across GCC retail.", "Al Rawabi Dairy",
            "https://www.linkedin.com/company/alrawabi/", "https://media.example.com/alrawabi.png",
            "Dubai", "United Arab Emirates",
            List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2021 – Present")),
            List.of(new CandidateEducationEntry("AUC", "MBA, Finance", "2010 - 2012")),
            List.of("Financial Planning"), List.of("English", "Arabic"),
            new EnrichedPhoto(PHOTO_BYTES, "image/jpeg"), EnrichmentVendor.BRIGHTDATA);

    private static final String AI_ENRICHMENT = """
            {"nationality":"Emirati","gender":"female","yearsExperience":14,
             "summary":"A proven GCC finance leader.",
             "technical":{"score":8,"positives":["Led a dairy IPO"],"negatives":["No energy exposure"]},
             "behavioural":{"score":6,"positives":["Board-facing"],"negatives":[]},
             "sources":[{"url":"https://news.example.com/cfo-profile","title":"CFO profile"},
                        {"url":"https://www.linkedin.com/in/sample-profile","title":"LinkedIn"}]}""";

    @Autowired private RecordingProfileEnricher enricher;
    @Autowired private StubChatModel model;
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

    @AfterEach
    void resetTheModel() {
        model.reset();
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
        assertThat(researched.get("education").get(0).get("school").asText()).isEqualTo("AUC");
        assertThat(researched.get("education").get(0).get("degree").asText()).isEqualTo("MBA, Finance");
        assertThat(researched.get("skills").get(0).asText()).isEqualTo("Financial Planning");
        assertThat(researched.get("enrichedAt").isNull()).isFalse();

        // Which provider answered is recorded, so the dataset's share of the work is countable.
        assertThat(db.queryForObject(
                "select enriched_by from app_lm_project_candidate where id = ?::uuid",
                String.class, candidateId)).isEqualTo("BRIGHTDATA");

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
    @DisplayName("research mapping someone at an already-held company clears its no-executive-found flag too")
    void researchMappingClearsNoExecutiveFoundOnAnAlreadyHeldCompany() throws Exception {
        String projectId = mandate("Already Flagged Employer Firm");
        universe.company("a42", "Al Rawabi Dairy").industry("food & beverages")
                .country("United Arab Emirates").city("Dubai").employees(1200)
                .linkedin("http://www.linkedin.com/company/alrawabi").insert();
        enricher.answerWith(RESEARCH);

        // The mandate already holds this company, having looked and found nobody there — exactly the
        // state `requireCompanyOfProject` clears on a hand-typed mapping. This path is a *researched*
        // one instead: the enrichment worker resolves the employer through `captureFromResearch`, not
        // through `requireCompanyOfProject`, so the two mapping doors must clear it independently.
        String triageCompanyId = body(mvc.perform(post(triageUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountId":"a42"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        mvc.perform(patch(triageUrl(projectId) + "/" + triageCompanyId)
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"noExecutiveFound":true}"""))
                .andExpect(jsonPath("$.noExecutiveFound").value(true));

        capture(projectId, "Sample Person", "sample-profile");

        // The same row: research resolved to the company already held, not a second one.
        JsonNode researched = firstCandidateOf(projectId);
        assertThat(researched.get("triageCompanyId").asText()).isEqualTo(triageCompanyId);
        mvc.perform(get(triageUrl(projectId) + "?status=inUniverse")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.companies[0].id").value(triageCompanyId))
                .andExpect(jsonPath("$.companies[0].noExecutiveFound").value(false));
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
        assertThat(captured.get("education")).isEmpty();
        assertThat(captured.get("skills")).isEmpty();
        assertThat(captured.get("enrichedAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("research naming no employer leaves the person unmapped, and no photo means 404")
    void researchWithoutAnEmployerLeavesThePersonUnmapped() throws Exception {
        String projectId = mandate("Employerless Research Firm");
        enricher.answerWith(new EnrichedProfile("Advisor", null, null, null, null, null, null,
                List.of(new CandidateCareerEntry("Somewhere", "Advisor", "2020 –")),
                null, null, null, null, EnrichmentVendor.HARVESTAPI));

        String candidateId = capture(projectId, "Sample Person", "sample-profile");

        JsonNode researched = firstCandidateOf(projectId);
        assertThat(researched.get("triageCompanyId").isNull()).isTrue();
        assertThat(researched.get("title").asText()).isEqualTo("Advisor");

        mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId + "/photo")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("after research lands, the missing background is inferred and flagged AI")
    void researchIsFollowedByAnInferredBackground() throws Exception {
        String projectId = mandate("Inferred Background Firm");
        enricher.answerWith(RESEARCH);
        model.answerWith(AI_ENRICHMENT);

        capture(projectId, "Sample Person", "sample-profile");

        JsonNode researched = firstCandidateOf(projectId);
        assertThat(researched.get("nationality").asText()).isEqualTo("Emirati");
        assertThat(researched.get("gender").asText()).isEqualTo("female");
        assertThat(researched.get("yearsExperience").asInt()).isEqualTo(14);
        assertThat(researched.get("aiInferredFields")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("nationality", "gender", "yearsExperience");
        assertThat(model.lastPrompt().getUserMessage().getText()).contains("Group CFO at Al Rawabi Dairy");
    }

    @Test
    @DisplayName("years of experience already on the row are kept, and only the rest is inferred")
    void experienceAlreadyOnTheRowIsKept() throws Exception {
        String projectId = mandate("Kept Experience Firm");
        enricher.answerWith(RESEARCH);
        model.answerWith(AI_ENRICHMENT);

        captureWith(projectId, "\"yearsExperience\":20");

        JsonNode researched = firstCandidateOf(projectId);
        assertThat(researched.get("yearsExperience").asInt()).isEqualTo(20);
        assertThat(researched.get("nationality").asText()).isEqualTo("Emirati");
        assertThat(researched.get("aiInferredFields")).extracting(JsonNode::asText)
                .containsExactlyInAnyOrder("nationality", "gender");
    }

    @Test
    @DisplayName("a complete background is left alone while the assessment is still made")
    void aCompleteBackgroundIsLeftAlone() throws Exception {
        String projectId = mandate("Full Background Firm");
        enricher.answerWith(RESEARCH);
        model.answerWith(AI_ENRICHMENT);

        captureWith(projectId, "\"yearsExperience\":20,\"nationality\":\"Saudi\",\"gender\":\"male\"");

        JsonNode researched = firstCandidateOf(projectId);
        assertThat(researched.get("nationality").asText()).isEqualTo("Saudi");
        assertThat(researched.get("aiInferredFields")).isEmpty();
        JsonNode assessment = assessmentOf(projectId, researched.get("id").asText(), adminToken);
        assertThat(assessment.get("technical").get("score").asInt()).isEqualTo(8);
    }

    @Test
    @DisplayName("a capture's enrichment stores the assessment, its sources without LinkedIn, and never on the row")
    void aCaptureStoresTheAssessment() throws Exception {
        String projectId = mandate("Assessed Capture Firm");
        enricher.answerWith(RESEARCH);
        model.answerWith(AI_ENRICHMENT);

        String candidateId = capture(projectId, "Sample Person", "sample-profile");

        JsonNode assessment = assessmentOf(projectId, candidateId, adminToken);
        assertThat(assessment.get("summary").asText()).isEqualTo("A proven GCC finance leader.");
        assertThat(assessment.get("technical").get("positives")).extracting(JsonNode::asText)
                .containsExactly("Led a dairy IPO");
        assertThat(assessment.get("behavioural").get("score").asInt()).isEqualTo(6);
        assertThat(assessment.get("sources")).hasSize(1);
        assertThat(assessment.get("sources").get(0).get("url").asText())
                .isEqualTo("https://news.example.com/cfo-profile");
        assertThat(assessment.get("assessedAt").isNull()).isFalse();
        // The candidate read is also a client's read, so the assessment never rides on it.
        assertThat(firstCandidateOf(projectId).has("aiAssessment")).isFalse();
    }

    @Test
    @DisplayName("the button enriches a hand-added executive, and never sends their contacts or pay")
    void theButtonEnrichesAHandAddedExecutive() throws Exception {
        String projectId = mandate("Button Firm");
        model.answerWith(AI_ENRICHMENT);
        String candidateId = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Hand Typed","title":"Group CFO","note":"private-note-text",
                                 "emails":[{"value":"hand.typed@example.com"}],
                                 "phones":[{"value":"+971 50 555 0101"}],
                                 "compensation":{"currency":"AED","baseSalary":987654}}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        assertThat(model.lastPrompt()).isNull();

        mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId + "/ai-assessment")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isNoContent());
        mvc.perform(post(candidatesUrl(projectId) + "/" + candidateId + "/ai-enrich")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isAccepted());

        String prompt = model.lastPrompt().getUserMessage().getText();
        assertThat(prompt).contains("Hand Typed", "Group CFO")
                .doesNotContain("hand.typed@example.com", "555", "987654", "private-note-text");
        JsonNode enriched = firstCandidateOf(projectId);
        assertThat(enriched.get("nationality").asText()).isEqualTo("Emirati");
        assertThat(assessmentOf(projectId, candidateId, adminToken).get("technical").get("score").asInt())
                .isEqualTo(8);
    }

    @Test
    @DisplayName("a client representative can read the executive but neither run nor read the AI assessment")
    void aClientSeatSeesNoAiAssessment() throws Exception {
        String projectId = mandate("Client Seat Firm");
        String candidateId = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Seen By Client"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String clientEmail = "client@client-" + domain;
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"A Client","position":"Chair","email":"%s"}
                                """.formatted(clientEmail)))
                .andExpect(status().isOk());
        String clientToken = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"A Client","password":"%s"}
                                """.formatted(email.latestTokenFor(clientEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();

        mvc.perform(get(candidatesUrl(projectId)).header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isOk());
        mvc.perform(post(candidatesUrl(projectId) + "/" + candidateId + "/ai-enrich")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
        mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId + "/ai-assessment")
                        .header("Authorization", "Bearer " + clientToken))
                .andExpect(status().isForbidden());
    }

    private JsonNode assessmentOf(String projectId, String candidateId, String token) throws Exception {
        return body(mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId + "/ai-assessment")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
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

    private void captureWith(String projectId, String background) throws Exception {
        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Sample Person","source":"extension",
                                 "linkedinUrl":"https://www.linkedin.com/in/sample-profile",%s}
                                """.formatted(background)))
                .andExpect(status().isCreated());
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

    private static String triageUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/triage";
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
