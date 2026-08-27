package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * Mapping executives for a mandate — the people half of a talent map, and the half the Companies grid
 * grew its Executive, Title and Status columns for.
 *
 * <p>The cases that matter here are all about the optional company link: a candidate belongs to the
 * project unconditionally and to one of its companies only if their employer happens to be in the
 * universe. Everything else — the duplicate guard, the full-replace edit, what survives a company
 * being removed from the mandate — follows from that.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class CandidateFlowIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("a fresh mandate has nobody mapped")
    void freshMandateMapsNobody() throws Exception {
        String projectId = mandate("Empty Mapping Firm");

        mvc.perform(get(candidatesUrl(projectId)).header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.candidates").isEmpty())
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("an executive mapped to a company snapshots that company's name")
    void mappingSnapshotsTheEmployer() throws Exception {
        String projectId = mandate("Mapping Snapshot Firm");
        String companyId = captureCompany(projectId, "Al Rawabi Dairy");

        JsonNode mapped = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"triageCompanyId":"%s","fullName":"Yasmin El-Sayed",
                                 "title":"VP Finance","seniority":"N-1","status":"interested",
                                 "employerName":"Somewhere Else Entirely"}
                                """.formatted(companyId)))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(mapped.get("companyName").asText()).isEqualTo("Al Rawabi Dairy");
        assertThat(mapped.get("triageCompanyId").asText()).isEqualTo(companyId);
        assertThat(mapped.get("seniority").asText()).isEqualTo("N-1");
        assertThat(mapped.get("status").asText()).isEqualTo("interested");
    }

    @Test
    @DisplayName("an executive whose employer is not in the universe is mapped to the project alone")
    void unmappedExecutiveKeepsTheirTypedEmployer() throws Exception {
        String projectId = mandate("Unmapped Executive Firm");

        JsonNode mapped = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Haddad","employerName":"A Company We Never Triaged"}"""))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(mapped.get("triageCompanyId").isNull()).isTrue();
        assertThat(mapped.get("companyName").asText()).isEqualTo("A Company We Never Triaged");
        // Nobody named a status, and identified is where a profile starts.
        assertThat(mapped.get("status").asText()).isEqualTo("identified");
        assertThat(mapped.get("source").asText()).isEqualTo("manual");
    }

    @Test
    @DisplayName("the whole profile round-trips, career history and languages included")
    void theWholeProfileRoundTrips() throws Exception {
        String projectId = mandate("Full Profile Firm");

        String candidateId = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Yasmin El-Sayed","title":"VP Finance","seniority":"N-1",
                                 "status":"engaged","email":"yasmin@example.com","phone":"+971 50 000 0000",
                                 "linkedinUrl":"linkedin.com/in/yasmin","locationCountry":"UAE",
                                 "locationCity":"Dubai","nationality":"Egyptian","yearsExperience":18,
                                 "summary":"Regional FMCG finance leadership.","note":"Met at a conference.",
                                 "compensation":{"currency":"aed","baseSalary":420000,"bonus":80000,
                                                 "allowances":40000,"longTermIncentive":0,
                                                 "noticePeriod":"3 months"},
                                 "career":[{"company":"Al Rawabi Dairy","title":"VP Finance","period":"2021-Present"},
                                           {"company":"Regional Foods Co.","title":"Finance Director","period":"2017-2021"}],
                                 "languages":["English","Arabic"]}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        JsonNode read = body(mvc.perform(get(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andReturn()).get("candidates").get(0);

        assertThat(read.get("id").asText()).isEqualTo(candidateId);
        assertThat(read.get("yearsExperience").asInt()).isEqualTo(18);
        assertThat(read.get("nationality").asText()).isEqualTo("Egyptian");
        // The currency is stored as the code it is, whatever case it arrived in.
        assertThat(read.at("/compensation/currency").asText()).isEqualTo("AED");
        assertThat(read.at("/compensation/noticePeriod").asText()).isEqualTo("3 months");
        assertThat(read.get("career")).hasSize(2);
        assertThat(read.at("/career/0/company").asText()).isEqualTo("Al Rawabi Dairy");
        assertThat(read.get("languages")).hasSize(2);
        // A bare host is a relative href inside the SPA, so it is promoted rather than stored as typed.
        assertThat(read.get("linkedinUrl").asText()).isEqualTo("https://linkedin.com/in/yasmin");
    }

    @Test
    @DisplayName("a profile URL that is not http(s) is dropped rather than stored")
    void aHostileProfileUrlIsDropped() throws Exception {
        String projectId = mandate("Profile Url Firm");

        JsonNode mapped = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Haddad","linkedinUrl":"javascript:alert(1)"}"""))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(mapped.get("linkedinUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("blank career rows are dropped rather than stored as empty posts")
    void blankCareerRowsAreDropped() throws Exception {
        String projectId = mandate("Blank Career Firm");

        JsonNode mapped = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Haddad",
                                 "career":[{"company":"Almarai","title":"CFO","period":"2019-"},
                                           {"company":" ","title":"","period":null}]}"""))
                .andExpect(status().isCreated())
                .andReturn());

        assertThat(mapped.get("career")).hasSize(1);
    }

    @Test
    @DisplayName("the same name at the same company is refused")
    void duplicateNameAtOneCompanyIsRefused() throws Exception {
        String projectId = mandate("Duplicate At Company Firm");
        String companyId = captureCompany(projectId, "Almarai");

        mapTo(projectId, companyId, "Omar Haddad");

        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"triageCompanyId":"%s","fullName":"omar haddad"}
                                """.formatted(companyId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CANDIDATE_ALREADY_MAPPED"));
    }

    @Test
    @DisplayName("the same name at two different companies is two different people")
    void theSameNameAtTwoCompaniesIsAllowed() throws Exception {
        String projectId = mandate("Same Name Two Companies Firm");

        mapTo(projectId, captureCompany(projectId, "Almarai"), "Omar Haddad");
        mapTo(projectId, captureCompany(projectId, "Savola Group"), "Omar Haddad");

        mvc.perform(get(candidatesUrl(projectId)).header("Authorization", "Bearer " + admin()))
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("saving an edit without renaming does not collide with the row being edited")
    void anEditDoesNotCollideWithItself() throws Exception {
        String projectId = mandate("Self Collision Firm");
        String companyId = captureCompany(projectId, "Agthia Group");
        String candidateId = mapTo(projectId, companyId, "Omar Haddad");

        mvc.perform(put(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"triageCompanyId":"%s","fullName":"Omar Haddad","title":"CFO"}
                                """.formatted(companyId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("CFO"));
    }

    @Test
    @DisplayName("an edit replaces the whole profile, clearing what it omits")
    void anEditReplacesTheWholeProfile() throws Exception {
        String projectId = mandate("Full Replace Firm");

        String candidateId = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Haddad","title":"CFO","note":"Worth a call.",
                                 "compensation":{"currency":"USD","baseSalary":300000},
                                 "languages":["English"]}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        JsonNode replaced = body(mvc.perform(put(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Haddad","status":"notInterested"}"""))
                .andExpect(status().isOk())
                .andReturn());

        // The point of PUT over PATCH: a field the drawer cleared is a field the row loses.
        assertThat(replaced.get("title").isNull()).isTrue();
        assertThat(replaced.get("note").isNull()).isTrue();
        assertThat(replaced.at("/compensation/baseSalary").isNull()).isTrue();
        assertThat(replaced.get("languages")).isEmpty();
        assertThat(replaced.get("status").asText()).isEqualTo("notInterested");
    }

    @Test
    @DisplayName("an edit can move someone to another company, or off the universe")
    void anEditCanRemapTheEmployer() throws Exception {
        String projectId = mandate("Remap Firm");
        String almarai = captureCompany(projectId, "Almarai");
        String nadec = captureCompany(projectId, "NADEC");
        String candidateId = mapTo(projectId, almarai, "Omar Haddad");

        JsonNode moved = body(mvc.perform(put(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"triageCompanyId":"%s","fullName":"Omar Haddad"}
                                """.formatted(nadec)))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(moved.get("companyName").asText()).isEqualTo("NADEC");

        JsonNode unmapped = body(mvc.perform(put(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Haddad","employerName":"An Unlisted Holding"}"""))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(unmapped.get("triageCompanyId").isNull()).isTrue();
        assertThat(unmapped.get("companyName").asText()).isEqualTo("An Unlisted Holding");
    }

    @Test
    @DisplayName("removing a company from the mandate unmaps its people rather than deleting them")
    void removingACompanyLeavesItsPeopleUnmapped() throws Exception {
        String projectId = mandate("Company Removal Firm");
        String companyId = captureCompany(projectId, "Spinneys Group");
        mapTo(projectId, companyId, "Wei Ling Tan");

        mvc.perform(delete("/api/v1/projects/" + projectId + "/triage/" + companyId)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNoContent());

        JsonNode survivor = body(mvc.perform(get(candidatesUrl(projectId) + "?unmapped=true")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andReturn()).get("candidates").get(0);

        assertThat(survivor.get("fullName").asText()).isEqualTo("Wei Ling Tan");
        assertThat(survivor.get("triageCompanyId").isNull()).isTrue();
        // The snapshot is why the row still says where they worked.
        assertThat(survivor.get("companyName").asText()).isEqualTo("Spinneys Group");
    }

    @Test
    @DisplayName("the grid reads only the people at the companies on its page")
    void theCompanyFilterNarrowsToThePageBeingRendered() throws Exception {
        String projectId = mandate("Company Filter Firm");
        String onThePage = captureCompany(projectId, "Almarai");
        String elsewhere = captureCompany(projectId, "Savola Group");
        mapTo(projectId, onThePage, "Omar Haddad");
        mapTo(projectId, elsewhere, "Yasmin El-Sayed");
        mapTo(projectId, null, "Wei Ling Tan");

        mvc.perform(get(candidatesUrl(projectId) + "?triageCompanyId=" + onThePage)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.candidates[0].fullName").value("Omar Haddad"));

        mvc.perform(get(candidatesUrl(projectId) + "?unmapped=true")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.candidates[0].fullName").value("Wei Ling Tan"));

        // No filter at all is the whole mandate, which is what a Candidates screen will want.
        mvc.perform(get(candidatesUrl(projectId)).header("Authorization", "Bearer " + admin()))
                .andExpect(jsonPath("$.totalCount").value(3));
    }

    @Test
    @DisplayName("a company-filtered read with no size named is sized at the ceiling, not the default")
    void aCompanyFilteredReadIsSizedAtTheCeiling() throws Exception {
        String projectId = mandate("Read Sizing Firm");
        String companyId = captureCompany(projectId, "Almarai");
        mapTo(projectId, companyId, "Omar Haddad");

        // The grid asks "who is at these companies?" and has no pager behind it, so naming no size
        // means everything this endpoint will return. The SPA used to compute the number itself and
        // landed exactly on the ceiling, which made lowering the deployment knob a 400 on every page.
        JsonNode filtered = body(mvc.perform(get(candidatesUrl(projectId) + "?triageCompanyId=" + companyId)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(filtered.get("size").asInt()).isEqualTo(100);

        JsonNode unmapped = body(mvc.perform(get(candidatesUrl(projectId) + "?unmapped=true")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(unmapped.get("size").asInt()).isEqualTo(100);

        // No filter is a plain list and keeps the ordinary page.
        JsonNode everyone = body(mvc.perform(get(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(everyone.get("size").asInt()).isEqualTo(25);
    }

    @Test
    @DisplayName("an explicitly oversized page is still refused")
    void anExplicitOversizedPageIsRefused() throws Exception {
        String projectId = mandate("Read Sizing Refusal Firm");

        // A caller that names a number is a caller that can be told the number is wrong — the same
        // contract the companies list keeps. Only the omission is interpreted generously.
        mvc.perform(get(candidatesUrl(projectId) + "?size=101")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the search box matches the person's name")
    void searchMatchesTheName() throws Exception {
        String projectId = mandate("Candidate Search Firm");
        mapTo(projectId, null, "Yasmin El-Sayed");
        mapTo(projectId, null, "Omar Haddad");

        mvc.perform(get(candidatesUrl(projectId) + "?q=sayed")
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.candidates[0].fullName").value("Yasmin El-Sayed"));
    }

    @Test
    @DisplayName("a company belonging to another mandate cannot be mapped against")
    void anotherMandatesCompanyIsNotFound() throws Exception {
        String theirs = mandate("Cross Mandate Firm");
        String theirCompanyId = captureCompany(theirs, "Americana Group");
        String ours = secondMandate(theirs);

        mvc.perform(post(candidatesUrl(ours))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"triageCompanyId":"%s","fullName":"Omar Haddad"}
                                """.formatted(theirCompanyId)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("another mandate's candidate is not found from this one")
    void anotherMandatesCandidateIsNotFound() throws Exception {
        String theirs = mandate("Cross Mandate Candidate Firm");
        String theirCandidateId = mapTo(theirs, null, "Omar Haddad");
        String ours = secondMandate(theirs);

        mvc.perform(delete(candidatesUrl(ours) + "/" + theirCandidateId)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an unknown status or seniority token is refused rather than stored")
    void unknownTokensAreRefused() throws Exception {
        String projectId = mandate("Unknown Token Firm");

        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Haddad","status":"keen"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Haddad","seniority":"N-9"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a nameless candidate is refused")
    void anExecutiveNeedsAName() throws Exception {
        String projectId = mandate("Nameless Firm");

        mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"  ","title":"CFO"}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a status change touches the status and nothing else")
    void statusChangeTouchesOnlyTheStatus() throws Exception {
        String projectId = mandate("Status Patch Firm");
        String candidateId = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Omar Haddad","title":"CFO","note":"Worth a call.",
                                 "compensation":{"currency":"USD","baseSalary":300000},
                                 "languages":["English"]}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        JsonNode moved = body(mvc.perform(patch(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"contacted"}"""))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(moved.get("status").asText()).isEqualTo("contacted");
        // The whole point of the PATCH beside the PUT: a pill flicked while reading must not
        // re-submit a profile that has been on screen for a while.
        assertThat(moved.get("title").asText()).isEqualTo("CFO");
        assertThat(moved.get("note").asText()).isEqualTo("Worth a call.");
        assertThat(moved.at("/compensation/baseSalary").asLong()).isEqualTo(300_000L);
        assertThat(moved.get("languages")).hasSize(1);
    }

    @Test
    @DisplayName("a status change refuses an unknown token and an empty one")
    void statusChangeRefusesRubbish() throws Exception {
        String projectId = mandate("Status Patch Refusal Firm");
        String candidateId = mapTo(projectId, null, "Omar Haddad");

        mvc.perform(patch(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"keen"}"""))
                .andExpect(status().isBadRequest());

        mvc.perform(patch(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"  "}"""))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("another mandate's candidate cannot have their status changed from this one")
    void statusChangeIsProjectScoped() throws Exception {
        String theirs = mandate("Status Patch Scope Firm");
        String theirCandidateId = mapTo(theirs, null, "Omar Haddad");
        String ours = secondMandate(theirs);

        mvc.perform(patch(candidatesUrl(ours) + "/" + theirCandidateId)
                        .header("Authorization", "Bearer " + admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"contacted"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("deleting a candidate removes only that mandate's research on them")
    void deleteRemovesOnlyThisMandatesRow() throws Exception {
        String projectId = mandate("Candidate Delete Firm");
        String candidateId = mapTo(projectId, null, "Omar Haddad");

        mvc.perform(delete(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin()))
                .andExpect(status().isNoContent());

        mvc.perform(get(candidatesUrl(projectId)).header("Authorization", "Bearer " + admin()))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private String adminToken;

    private static String candidatesUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/candidates";
    }

    private String admin() {
        return adminToken;
    }

    /** A workspace, a client and a mandate, with this test's admin logged in against it. */
    private String mandate(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        adminToken = login(alok);
        return projectIn("Mapping Client", "Head of Retail");
    }

    /** A second mandate in the same workspace, so cross-mandate scoping is not cross-tenant scoping. */
    private String secondMandate(String firstProjectId) throws Exception {
        assertThat(firstProjectId).isNotBlank();
        return projectIn("Second Mapping Client", "Chief Financial Officer");
    }

    private String projectIn(String clientName, String positionTitle) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s"}""".formatted(clientName)))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}
                                """.formatted(clientId, positionTitle)))
                .andReturn()).get("id").asText();
    }

    /** A company in the mandate's universe without going near Apollo — the capture door does. */
    private String captureCompany(String projectId, String companyName) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s"}""".formatted(companyName)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String mapTo(String projectId, String triageCompanyId, String fullName) throws Exception {
        String companyClause = triageCompanyId == null ? "" : "\"triageCompanyId\":\"%s\","
                .formatted(triageCompanyId);
        return body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{%s\"fullName\":\"%s\"}".formatted(companyClause, fullName)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
