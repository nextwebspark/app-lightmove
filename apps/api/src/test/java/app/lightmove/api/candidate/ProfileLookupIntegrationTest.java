package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingProfileEnricher;
import app.lightmove.api.candidate.constant.EnrichmentVendor;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.candidate.model.EnrichedPhoto;
import app.lightmove.api.candidate.model.EnrichedProfile;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * The Add form's LinkedIn lookup: research answered to the form before anything is saved, then filed by the save that
 * follows — the person researched and their employer in the universe — without a second vendor read.
 */
@IntegrationTest
class ProfileLookupIntegrationTest extends FlowTestSupport {

    private static final byte[] PHOTO_BYTES = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0};

    private static final EnrichedProfile RESEARCH = new EnrichedProfile(
            "Group CFO", "Finance leader across GCC retail.", "Al Rawabi Dairy",
            "https://www.linkedin.com/company/alrawabi/", "https://media.example.com/alrawabi.png",
            "Dubai", "United Arab Emirates",
            List.of(new CandidateCareerEntry("Al Rawabi Dairy", "Group CFO", "2021 – Present", null)),
            List.of(new CandidateEducationEntry("AUC", "MBA, Finance", "2010 - 2012")),
            List.of("Financial Planning"), List.of("English", "Arabic"),
            new EnrichedPhoto(PHOTO_BYTES, "image/jpeg"), EnrichmentVendor.BRIGHTDATA, "Layla Haddad");

    @Autowired private RecordingProfileEnricher enricher;
    @Autowired @FixtureJdbc JdbcTemplate db;

    private String adminToken;

    @BeforeEach
    void resetTheProvider() {
        enricher.clear();
        new ApolloUniverse(db).reset();
    }

    @Test
    @DisplayName("a looked-up profile fills the form, and its save files the person researched and the employer in the universe")
    void aLookedUpProfileIsFiledResearchedWithItsEmployer() throws Exception {
        String projectId = mandate("Lookup Firm");
        enricher.answerWith(RESEARCH);

        lookUp(projectId, "linkedin.com/in/Layla-Haddad?trk=share")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("found"))
                .andExpect(jsonPath("$.profile.linkedinUrl").value("https://www.linkedin.com/in/layla-haddad/"))
                .andExpect(jsonPath("$.profile.fullName").value("Layla Haddad"))
                .andExpect(jsonPath("$.profile.title").value("Group CFO"))
                .andExpect(jsonPath("$.profile.employerName").value("Al Rawabi Dairy"))
                .andExpect(jsonPath("$.profile.employerTriageCompanyId").isEmpty())
                .andExpect(jsonPath("$.profile.career[0].company").value("Al Rawabi Dairy"))
                .andExpect(jsonPath("$.profile.photoDataUrl").value("data:image/jpeg;base64,/9j/AA=="));

        String candidateId = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Layla Haddad","title":"Group CFO","employerName":"al rawabi dairy",
                                 "linkedinUrl":"https://www.linkedin.com/in/layla-haddad/"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.triageCompanyId").isNotEmpty())
                .andExpect(jsonPath("$.companyName").value("Al Rawabi Dairy"))
                .andExpect(jsonPath("$.source").value("manual"))
                .andExpect(jsonPath("$.enrichedAt").isNotEmpty())
                .andExpect(jsonPath("$.education[0].school").value("AUC"))
                .andReturn()).get("id").asText();

        // One vendor read: the lookup's. The save filed what it bought.
        assertThat(enricher.fetchedUrls()).containsExactly("https://www.linkedin.com/in/layla-haddad/");

        JsonNode company = body(mvc.perform(get("/api/v1/projects/" + projectId + "/triage?status=inUniverse")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("companies").get(0);
        assertThat(company.get("companyName").asText()).isEqualTo("Al Rawabi Dairy");
        assertThat(company.get("source").asText()).isEqualTo("manual");
        assertThat(company.get("logoUrl").asText()).isEqualTo("https://media.example.com/alrawabi.png");

        mvc.perform(get(candidatesUrl(projectId) + "/" + candidateId + "/photo")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsByteArray()).isEqualTo(PHOTO_BYTES));
    }

    @Test
    @DisplayName("an employer typed over research's stays the stated one, and nothing is filed into the universe")
    void aTypedEmployerOutranksResearch() throws Exception {
        String projectId = mandate("Typed Employer Firm");
        enricher.answerWith(RESEARCH);
        lookUp(projectId, "https://www.linkedin.com/in/typed-employer/").andExpect(status().isOk());

        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Layla Haddad","employerName":"Almarai",
                                 "linkedinUrl":"https://www.linkedin.com/in/typed-employer/"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.triageCompanyId").isEmpty())
                .andExpect(jsonPath("$.companyName").value("Almarai"))
                .andExpect(jsonPath("$.enrichedAt").isNotEmpty());

        mvc.perform(get("/api/v1/projects/" + projectId + "/triage?status=inUniverse")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(jsonPath("$.companies").isEmpty());
    }

    @Test
    @DisplayName("a profile the position already maps answers with that executive, and asks the vendor nothing")
    void aHeldProfileAnswersWithTheExecutive() throws Exception {
        String projectId = mandate("Held Profile Firm");
        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Layla Haddad","linkedinUrl":"https://www.linkedin.com/in/held-profile"}"""))
                .andExpect(status().isCreated());

        lookUp(projectId, "https://linkedin.com/in/held-profile/")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("held"))
                .andExpect(jsonPath("$.held.candidate.fullName").value("Layla Haddad"))
                .andExpect(jsonPath("$.profile").isEmpty());
        assertThat(enricher.fetchedUrls()).isEmpty();
    }

    @Test
    @DisplayName("an address that is not a member's profile is refused before any spend")
    void aNonProfileAddressIsRefused() throws Exception {
        String projectId = mandate("Company Page Firm");

        String code = codeOf(lookUp(projectId, "https://www.linkedin.com/company/alrawabi/")
                .andExpect(status().isBadRequest())
                .andReturn());
        assertThat(code).isEqualTo("PROFILE_LOOKUP_NOT_A_PROFILE");
        assertThat(enricher.fetchedUrls()).isEmpty();
    }

    @Test
    @DisplayName("research that finds nobody says so, and a provider failure is a retryable error")
    void notFoundAndFailureAreTold() throws Exception {
        String projectId = mandate("Missing Profile Firm");

        lookUp(projectId, "https://www.linkedin.com/in/nobody-there/")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.outcome").value("notFound"));

        enricher.failWith(new IllegalStateException("vendor down"));
        String code = codeOf(lookUp(projectId, "https://www.linkedin.com/in/someone-else/")
                .andExpect(status().isBadGateway())
                .andReturn());
        assertThat(code).isEqualTo("PROFILE_LOOKUP_FAILED");
    }

    private ResultActions lookUp(String projectId, String linkedinUrl) throws Exception {
        return mvc.perform(post(candidatesUrl(projectId) + "/profile-lookup")
                .header("Authorization", "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(java.util.Map.of("linkedinUrl", linkedinUrl))));
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
                                {"customName":"Retail Unit"}"""))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}""".formatted(clientId)))
                .andReturn()).get("id").asText();
    }
}
