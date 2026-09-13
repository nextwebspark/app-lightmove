package app.lightmove.api.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * The report read: the same gate as the grids it summarises, and four chapters whose every figure is
 * traceable to a row the test itself wrote — a company declined is not in the universe, an executive
 * at no company has no sector, a package in another currency is counted rather than converted.
 */
@IntegrationTest
class ReportIntegrationTest extends FlowTestSupport {

    @Test
    @DisplayName("reading the report follows the seat, exactly as reading the grid does")
    void readGateFollowsTheSeat() throws Exception {
        Fixture f = fixture("Report Gate Firm");

        mvc.perform(get(reportUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk());
        mvc.perform(get(reportUrl(f.projectId)).header("Authorization", "Bearer " + login(f.saraEmail)))
                .andExpect(status().isForbidden());
        mvc.perform(get(reportUrl(UUID.randomUUID().toString())).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isNotFound());

        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        mvc.perform(get(reportUrl(f.projectId)).header("Authorization", "Bearer " + login(f.saraEmail)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a fresh mandate reports an empty map rather than failing on one")
    void freshMandateIsAnEmptyReport() throws Exception {
        Fixture f = fixture("Report Empty Firm");

        mvc.perform(get(reportUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.head.universeCount").value(0))
                .andExpect(jsonPath("$.head.executivesMapped").value(0))
                .andExpect(jsonPath("$.head.truncated").value(false))
                .andExpect(jsonPath("$.progress.companiesCumulative").isArray())
                .andExpect(jsonPath("$.progress.companiesCumulative[0]").value(0))
                .andExpect(jsonPath("$.progress.daysSinceLastCompany").doesNotExist())
                .andExpect(jsonPath("$.market.sectors").isEmpty())
                .andExpect(jsonPath("$.remuneration.fixedBand").doesNotExist())
                .andExpect(jsonPath("$.remuneration.disclosures").isEmpty())
                .andExpect(jsonPath("$.diversity.nationalities").isEmpty());
    }

    @Test
    @DisplayName("every chapter is drawn from the mandate's own rows")
    void chaptersAreDrawnFromTheRows() throws Exception {
        Fixture f = fixture("Report Chapters Firm");
        String almarai = capture(f.admin, f.projectId, "Almarai", "food & beverages", null);
        capture(f.admin, f.projectId, "Panda Retail", "retail", null);
        capture(f.admin, f.projectId, "Gulf Trader", null, null);
        capture(f.admin, f.projectId, "Ruled Out Co", "retail", "declined");
        candidate(f.admin, f.projectId, """
                {"triageCompanyId":"%s","fullName":"Yasmin El-Sayed","title":"CFO","seniority":"C-Suite",
                 "status":"interested","locationCity":"Riyadh","locationCountry":"KSA",
                 "nationality":"saudi arabian",
                 "compensation":{"currency":"USD","baseSalary":300000,"allowances":20000,"bonus":50000}}"""
                .formatted(almarai));
        candidate(f.admin, f.projectId, """
                {"triageCompanyId":"%s","fullName":"Omar Haddad","title":"Finance Director","seniority":"N-1",
                 "locationCity":"Dubai","locationCountry":"UAE","nationality":"Egypt",
                 "compensation":{"currency":"AED","baseSalary":400000}}"""
                .formatted(almarai));
        candidate(f.admin, f.projectId, """
                {"fullName":"Lina Said","employerName":"Somewhere Untriaged","nationality":"Emirati"}""");
        putCompensation(f.admin, f.projectId, """
                {"currency":"USD","salaryMin":20000,"salaryMax":25000,"baseSalaryMode":"MONTHLY",
                 "bonusValue":20,"bonusBasis":"PERCENT_OF_BASE","incentiveType":"LTIP_CASH",
                 "incentiveAmount":100000,"benefits":[]}""");

        JsonNode report = body(mvc.perform(get(reportUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                .andReturn());

        // Head: the declined company is out of the universe; the people are all counted.
        assertThat(report.at("/head/universeCount").asInt()).isEqualTo(3);
        assertThat(report.at("/head/executivesMapped").asInt()).isEqualTo(3);
        assertThat(report.at("/head/truncated").asBoolean()).isFalse();

        // Progress: one company has an executive, filed this week, today.
        JsonNode progress = report.get("progress");
        assertThat(progress.get("targetCompanies").asInt()).isEqualTo(3);
        assertThat(progress.get("companiesCumulative")).hasSize(1);
        assertThat(progress.at("/companiesCumulative/0").asInt()).isEqualTo(1);
        assertThat(progress.at("/weekly/0/identified").asInt()).isEqualTo(3);
        assertThat(progress.get("daily")).hasSize(1);
        assertThat(progress.get("daysSinceLastCompany").asInt()).isZero();

        // Market: sector comes from the company, so Lina has none; the matrix carries every level.
        JsonNode market = report.get("market");
        assertThat(market.get("sectors")).hasSize(1);
        assertThat(market.at("/sectors/0").asText()).isEqualTo("food & beverages");
        assertThat(market.get("withoutSector").asInt()).isEqualTo(1);
        assertThat(market.get("withoutSeniority").asInt()).isEqualTo(1);
        assertThat(cell(market, "food & beverages", "C-Suite")).isEqualTo(1);
        assertThat(cell(market, "food & beverages", "N-2")).isZero();
        assertThat(market.get("slices")).hasSize(2);
        assertThat(market.at("/slices/0/companies/0").asText()).isEqualTo("Almarai");
        assertThat(market.at("/slices/0/executives/0/fullName").asText()).isEqualTo("Yasmin El-Sayed");
        assertThat(market.get("hubs")).hasSize(2);
        assertThat(market.at("/hubs/0/country").asText()).isEqualTo("Saudi Arabia");
        assertThat(market.at("/hubs/0/interested").asInt()).isEqualTo(1);
        assertThat(market.get("unlocated").asInt()).isEqualTo(1);
        assertThat(market.get("companiesBySector")).hasSize(3);

        // Remuneration: the monthly band annualised and widened; the AED package counted, not converted.
        JsonNode remuneration = report.get("remuneration");
        assertThat(remuneration.get("currency").asText()).isEqualTo("USD");
        assertThat(remuneration.at("/fixedBand/low").asLong()).isEqualTo(240_000);
        assertThat(remuneration.at("/packageBand/high").asLong()).isEqualTo(460_000);
        assertThat(remuneration.get("disclosures")).hasSize(1);
        assertThat(remuneration.at("/disclosures/0/fixed").asLong()).isEqualTo(320_000);
        assertThat(remuneration.at("/disclosures/0/totalPackage").asLong()).isEqualTo(370_000);
        assertThat(remuneration.at("/disclosures/0/nationality").asText()).isEqualTo("Saudi");
        assertThat(remuneration.at("/disclosures/0/status").asText()).isEqualTo("interested");
        assertThat(remuneration.get("otherCurrency").asInt()).isEqualTo(1);

        // Diversity: three spellings, three headings, two of them Gulf.
        JsonNode diversity = report.get("diversity");
        assertThat(diversity.get("nationalities")).hasSize(3);
        assertThat(nationality(diversity, "Saudi").get("gcc").asBoolean()).isTrue();
        assertThat(nationality(diversity, "Egyptian").get("gcc").asBoolean()).isFalse();
        assertThat(nationality(diversity, "Emirati").get("unclassified").asInt()).isEqualTo(1);
        assertThat(diversity.get("gccNationals").asInt()).isEqualTo(2);
        assertThat(diversity.get("unknownNationality").asInt()).isZero();
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static String reportUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/report";
    }

    private static int cell(JsonNode market, String sector, String level) {
        for (JsonNode cell : market.get("cells")) {
            if (cell.get("sector").asText().equals(sector) && cell.get("level").asText().equals(level)) {
                return cell.get("count").asInt();
            }
        }
        throw new AssertionError("No cell for " + sector + " × " + level);
    }

    private static JsonNode nationality(JsonNode diversity, String label) {
        for (JsonNode row : diversity.get("nationalities")) {
            if (row.get("nationality").asText().equals(label)) {
                return row;
            }
        }
        throw new AssertionError("No nationality row for " + label);
    }

    private String capture(String token, String projectId, String name, String industry, String status)
            throws Exception {
        String industryField = industry == null ? "" : ",\"industry\":\"" + industry + "\"";
        String statusField = status == null ? "" : ",\"status\":\"" + status + "\"";
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + name + "\"" + industryField + statusField + "}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private void candidate(String token, String projectId, String json) throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());
    }

    private void putCompensation(String token, String projectId, String json) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/position/compensation")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());
    }

    private record Fixture(String admin, String projectId, String saraEmail, String saraId) {}

    private Fixture fixture(String firmName) throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Report Client"}"""))
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Chief Financial Officer"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();

        return new Fixture(admin, projectId, sara, memberIdOf(admin, sara));
    }

    private void seat(String leadToken, String projectId, String memberId, String role) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"%s"}""".formatted(role)))
                .andExpect(status().isOk());
    }
}
