package app.lightmove.api.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;

/**
 * The report read: the same gate as the grids it summarises, and four chapters whose every figure is
 * traceable to a row the test itself wrote — a company declined is not in the universe, an executive
 * at no company has no sector, a package in another currency is counted rather than converted.
 */
@IntegrationTest
class ReportIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

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
    @DisplayName("reading the report drafts no brief: a mandate without one reports no band and still has none")
    void readingWritesNoBrief() throws Exception {
        Fixture f = fixture("Report Read Only Firm");
        UUID projectId = UUID.fromString(f.projectId);
        db.update("DELETE FROM app_lm_position WHERE project_id = ?", projectId);

        mvc.perform(get(reportUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.remuneration.currency").value("USD"))
                .andExpect(jsonPath("$.remuneration.fixedBand").doesNotExist());

        assertThat(db.queryForObject("SELECT count(*) FROM app_lm_position WHERE project_id = ?",
                Integer.class, projectId)).isZero();
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
                .andExpect(jsonPath("$.progress.daysSinceLastExecutive").doesNotExist())
                .andExpect(jsonPath("$.market.sectors").isEmpty())
                .andExpect(jsonPath("$.remuneration.fixedBand").doesNotExist())
                .andExpect(jsonPath("$.remuneration.disclosures").isEmpty())
                .andExpect(jsonPath("$.diversity.nationalities").isEmpty())
                .andExpect(jsonPath("$.diversity.genderUnrecorded").value(0))
                .andExpect(jsonPath("$.diversity.genderByLevel[4].level").value("N-3"))
                .andExpect(jsonPath("$.diversity.genderByLevel[0].female").value(0))
                .andExpect(jsonPath("$.diversity.genderWithoutLevel.female").value(0));
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
                 "nationality":"saudi arabian","gender":"female",
                 "compensation":{"currency":"USD","baseSalary":300000,"allowances":20000,"bonus":50000}}"""
                .formatted(almarai));
        candidate(f.admin, f.projectId, """
                {"triageCompanyId":"%s","fullName":"Omar Haddad","title":"Finance Director","seniority":"N-1",
                 "locationCity":"Dubai","locationCountry":"UAE","nationality":"Egypt","gender":"male",
                 "compensation":{"currency":"AED","baseSalary":400000}}"""
                .formatted(almarai));
        candidate(f.admin, f.projectId, """
                {"fullName":"Lina Said","employerName":"Somewhere Untriaged","nationality":"Emirati",
                 "locationCountry":"Oman"}""");
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
        assertThat(progress.get("daysSinceLastExecutive").asInt()).isZero();

        // Market: sector comes from the company, so Lina, mapped at none, has no sector.
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
        // A hub is a country, so the executive with a country and no city is a market of their own
        // rather than unlocated — which is the whole reason the grouping is not by city.
        assertThat(market.get("hubs")).hasSize(3);
        assertThat(market.at("/hubs/0/country").asText()).isEqualTo("Saudi Arabia");
        assertThat(market.at("/hubs/0/interested").asInt()).isEqualTo(1);
        assertThat(market.at("/hubs/2/country").asText()).isEqualTo("Oman");
        assertThat(market.at("/hubs/2/count").asInt()).isEqualTo(1);
        assertThat(market.get("unlocated").asInt()).isZero();
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
        assertThat(nationality(diversity, "Arab expat, non-GCC").get("gcc").asBoolean()).isFalse();
        assertThat(nationality(diversity, "Emirati").get("unclassified").asInt()).isEqualTo(1);
        assertThat(diversity.get("gccNationals").asInt()).isEqualTo(2);
        assertThat(diversity.get("unknownNationality").asInt()).isZero();
        // Two of the three recorded a gender; the third is unrecorded and is not counted as a man.
        assertThat(level(diversity, "C-Suite").get("female").asInt()).isEqualTo(1);
        assertThat(level(diversity, "C-Suite").get("male").asInt()).isZero();
        assertThat(level(diversity, "N-1").get("male").asInt()).isEqualTo(1);
        assertThat(diversity.get("genderUnrecorded").asInt()).isEqualTo(1);
    }

    @Test
    @DisplayName("a gender recorded on an executive with no seniority is still counted, apart from the levels")
    void genderWithoutALevelIsCounted() throws Exception {
        Fixture f = fixture("Report Unlevelled Firm");
        candidate(f.admin, f.projectId, """
                {"fullName":"Huda Al-Mansoori","employerName":"Somewhere Untriaged","gender":"female"}""");
        candidate(f.admin, f.projectId, """
                {"fullName":"Karim Aziz","employerName":"Somewhere Untriaged"}""");

        mvc.perform(get(reportUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.diversity.genderWithoutLevel.female").value(1))
                .andExpect(jsonPath("$.diversity.genderWithoutLevel.male").value(0))
                .andExpect(jsonPath("$.diversity.genderUnrecorded").value(1))
                .andExpect(jsonPath("$.diversity.genderByLevel[1].female").value(0));
    }

    @Test
    @DisplayName("researcher performance is staff-only: a client representative reads the report but not the team")
    void teamReadIsStaffOnly() throws Exception {
        Fixture f = fixture("Report Team Gate Firm");
        String repEmail = "ext@report-client.example";
        JsonNode representative = body(mvc.perform(post("/api/v1/clients/" + f.clientId + "/representatives")
                        .header("Authorization", "Bearer " + f.admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ext Rep","position":"Chair","email":"%s"}
                                """.formatted(repEmail)))
                .andExpect(status().isCreated())
                .andReturn());
        String rep = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Ext Rep","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
        mvc.perform(post("/api/v1/projects/" + f.projectId + "/representatives")
                        .header("Authorization", "Bearer " + f.admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representativeId":"%s"}
                                """.formatted(representative.get("id").asText())))
                .andExpect(status().isOk());

        mvc.perform(get(reportUrl(f.projectId)).header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk());
        mvc.perform(get(teamUrl(f.projectId)).header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden());
        mvc.perform(get(teamUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.researchers[0].role").value("LEAD"))
                .andExpect(jsonPath("$.researchers[0].executives").value(0))
                .andExpect(jsonPath("$.researchers[0].quality").doesNotExist())
                .andExpect(jsonPath("$.researchers.length()").value(1));
    }

    @Test
    @DisplayName("each executive is credited to whoever filed it, and a company to whoever filed its first")
    void teamIsAttributedByWhoFiled() throws Exception {
        Fixture f = fixture("Report Team Firm");
        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        String sara = login(f.saraEmail);
        String almarai = capture(f.admin, f.projectId, "Almarai", "food & beverages", null);
        String panda = capture(f.admin, f.projectId, "Panda Retail", "retail", null);
        capture(f.admin, f.projectId, "Savola", "food & beverages", null);
        candidate(f.admin, f.projectId, """
                {"triageCompanyId":"%s","fullName":"Yasmin El-Sayed","status":"interested",
                 "emails":[{"value":"yasmin@almarai.example","verified":true}],
                 "compensation":{"currency":"USD","baseSalary":300000}}""".formatted(almarai));
        candidate(sara, f.projectId, """
                {"triageCompanyId":"%s","fullName":"Omar Farouk"}""".formatted(almarai));
        candidate(sara, f.projectId, """
                {"triageCompanyId":"%s","fullName":"Hind Al Suwaidi"}""".formatted(panda));

        JsonNode team = body(mvc.perform(get(teamUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                .andReturn());

        assertThat(team.at("/kpis/executivesInRange").asInt()).isEqualTo(3);
        assertThat(team.at("/kpis/coveredCompanies").asInt()).isEqualTo(2);
        assertThat(team.at("/kpis/targetCompanies").asInt()).isEqualTo(3);
        assertThat(team.at("/kpis/lastAddedBy").asText()).isEqualTo("Sara Al-Mansour");

        JsonNode first = team.at("/researchers/0");
        assertThat(first.get("name").asText()).isEqualTo("Sara Al-Mansour");
        assertThat(first.get("role").asText()).isEqualTo("RESEARCHER");
        assertThat(first.get("executives").asInt()).isEqualTo(2);
        assertThat(first.get("companies").asInt()).isEqualTo(2);
        assertThat(first.get("sharePct").asInt()).isEqualTo(67);
        assertThat(first.at("/quality/level").asText()).isEqualTo("ATTENTION");

        JsonNode lead = team.at("/researchers/1");
        assertThat(lead.get("name").asText()).isEqualTo("Alok Kumar");
        assertThat(lead.at("/quality/contactPct").asInt()).isEqualTo(100);
        assertThat(lead.at("/quality/verifiedPct").asInt()).isEqualTo(100);
        assertThat(lead.at("/quality/compPct").asInt()).isEqualTo(100);
        assertThat(lead.at("/quality/level").asText()).isEqualTo("GOOD");
        assertThat(lead.at("/statusMix/0/status").asText()).isEqualTo("interested");

        // Almarai's first executive was the lead's, so the lead holds it although Sara filed there too.
        assertThat(team.at("/coverage/0/companies").asInt()).isEqualTo(1);
        assertThat(team.at("/coverage/1/companies").asInt()).isEqualTo(1);
        assertThat(team.at("/companiesTotal").asInt()).isEqualTo(2);
        assertThat(team.at("/companies/0/name").asText()).isEqualTo("Almarai");
        assertThat(team.at("/companies/0/contributors").asInt()).isEqualTo(2);
        assertThat(team.at("/companies/0/mappedExecutives/0/addedByName").asText()).isEqualTo("Sara Al-Mansour");
    }

    @Test
    @DisplayName("an admin with no seat who files is a lead; a researcher unseated since is a former member")
    void unseatedFilersAreNamedByWhatTheyStillAre() throws Exception {
        Fixture f = fixture("Report Team Unseated Firm");
        String nadia = "nadia@" + domain;
        inviteAndAccept(f.admin, "Nadia Salloum", nadia, "ADMIN");
        candidate(login(nadia), f.projectId, """
                {"fullName":"Filed By An Admin"}""");
        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        candidate(login(f.saraEmail), f.projectId, """
                {"fullName":"Filed By Sara"}""");
        mvc.perform(delete("/api/v1/projects/" + f.projectId + "/members/" + f.saraId)
                        .header("Authorization", "Bearer " + f.admin))
                .andExpect(status().is2xxSuccessful());

        JsonNode researchers = body(mvc.perform(get(teamUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                .andReturn()).get("researchers");

        assertThat(roleOf(researchers, "Nadia Salloum")).isEqualTo("LEAD");
        assertThat(roleOf(researchers, "Sara Al-Mansour")).isEqualTo("FORMER");
    }

    @Test
    @DisplayName("a range that starts after it ends is refused")
    void backwardsRangeIsRefused() throws Exception {
        Fixture f = fixture("Report Team Range Firm");
        mvc.perform(get(teamUrl(f.projectId)).param("from", "2030-01-02").param("to", "2020-01-01")
                        .header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isBadRequest());
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static String reportUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/report";
    }

    private static String roleOf(JsonNode researchers, String name) {
        for (JsonNode researcher : researchers) {
            if (researcher.get("name").asText().equals(name)) {
                return researcher.get("role").asText();
            }
        }
        throw new AssertionError("No researcher named " + name);
    }

    private static String teamUrl(String projectId) {
        return reportUrl(projectId) + "/team";
    }

    private static int cell(JsonNode market, String sector, String level) {
        for (JsonNode cell : market.get("cells")) {
            if (cell.get("sector").asText().equals(sector) && cell.get("level").asText().equals(level)) {
                return cell.get("count").asInt();
            }
        }
        throw new AssertionError("No cell for " + sector + " × " + level);
    }

    private static JsonNode level(JsonNode diversity, String label) {
        for (JsonNode row : diversity.get("genderByLevel")) {
            if (row.get("level").asText().equals(label)) {
                return row;
            }
        }
        throw new AssertionError("No gender row for " + label);
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

    private record Fixture(String admin, String clientId, String projectId, String saraEmail, String saraId) {}

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

        return new Fixture(admin, clientId, projectId, sara, memberIdOf(admin, sara));
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
