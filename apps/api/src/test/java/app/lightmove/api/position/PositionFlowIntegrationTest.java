package app.lightmove.api.position;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.position.repository.PositionRepository;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import tools.jackson.databind.JsonNode;

/**
 * The position brief end to end: template seeding at creation, one write per wizard step, the
 * publication stamp, the attached document, and the lazy seed for pre-V7 mandates.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class PositionFlowIntegrationTest extends FlowTestSupport {

    @Autowired PositionRepository positionRows;

    @Test
    @DisplayName("a CFO mandate arrives drafted from the finance template, located at the client's HQ")
    void cfoProjectSeedsTheFinanceTemplate() throws Exception {
        String admin = adminOf("Seed Firm");
        String projectId = createProject(admin, createClient(admin, "Meridian Energy", "UAE"),
                "Chief Financial Officer");

        mvc.perform(get(positionUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.roleTitle").value("Chief Financial Officer"))
                .andExpect(jsonPath("$.details.location").value("UAE"))
                .andExpect(jsonPath("$.details.seniority").value("C_SUITE"))
                .andExpect(jsonPath("$.details.employmentType").value("FULL_TIME_PERMANENT"))
                .andExpect(jsonPath("$.details.responsibilities[0]").value("Group P&L stewardship"))
                .andExpect(jsonPath("$.reporting.orgChart.length()").value(2))
                .andExpect(jsonPath("$.reporting.orgChart[0].title").value("Group CEO"))
                .andExpect(jsonPath("$.reporting.orgChart[0].mandateSeat").value(false))
                .andExpect(jsonPath("$.reporting.orgChart[1].mandateSeat").value(true))
                .andExpect(jsonPath("$.compensation.currency").value("USD"))
                .andExpect(jsonPath("$.compensation.baseSalaryMode").value("ANNUAL"))
                .andExpect(jsonPath("$.context.hiringUrgency").value("STANDARD"))
                .andExpect(jsonPath("$.assessment.criteria[0].fromBrief").value(true))
                .andExpect(jsonPath("$.assessment.criteria[0].mode").value("REQUIRED"))
                .andExpect(jsonPath("$.assessment.technical[0].name").value("Financial Reporting & Controls"))
                .andExpect(jsonPath("$.assessment.technical[0].description").isNotEmpty())
                .andExpect(jsonPath("$.publication.publishedAt").isEmpty())
                .andExpect(jsonPath("$.document").isEmpty());
    }

    @Test
    @DisplayName("an unrecognised title falls back to the generic executive template")
    void unknownTitleSeedsTheGenericTemplate() throws Exception {
        String admin = adminOf("Generic Firm");
        String projectId = createProject(admin, createClient(admin, "Al Rabie", null),
                "Head of Alchemy");

        JsonNode brief = readBrief(admin, projectId);
        assertThat(brief.get("details").get("location").isNull()).isTrue();
        assertThat(sum(brief.get("assessment").get("technical"))).isEqualTo(100);
        assertThat(sum(brief.get("assessment").get("behavioural"))).isEqualTo(100);
        assertThat(brief.get("assessment").get("criteria").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("step one round-trips, and its role title is the mandate's own")
    void detailsStepRoundTripsAndRenamesTheMandate() throws Exception {
        String admin = adminOf("Details Firm");
        String projectId = createProject(admin, createClient(admin, "Agthia", "UAE"), "CFO");

        putStep(admin, projectId, "details", """
                {"roleTitle":"Group Chief Financial Officer","department":"Group Finance",
                 "location":"Abu Dhabi, UAE","employmentType":"FIXED_TERM_CONTRACT","seniority":"N_MINUS_1",
                 "responsibilities":["Group P&L stewardship","Capital structure & treasury"],
                 "narrative":"A hands-on CFO."}""")
                .andExpect(jsonPath("$.details.department").value("Group Finance"))
                .andExpect(jsonPath("$.details.seniority").value("N_MINUS_1"))
                .andExpect(jsonPath("$.details.responsibilities.length()").value(2));

        // The role title is the mandate's one title — the step writes it there, not to a second copy.
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].positionTitle").value("Group Chief Financial Officer"));
    }

    @Test
    @DisplayName("the remaining steps round-trip, each with the units its figures are quoted in")
    void everyStepRoundTrips() throws Exception {
        String admin = adminOf("Steps Firm");
        String projectId = createProject(admin, createClient(admin, "NMC", "UAE"), "CFO");

        putStep(admin, projectId, "context", """
                {"mandateReason":"GROWTH_EXPANSION","businessDriver":"Carry the next capital phase.",
                 "strategicPriorities":["CAPITAL_DISCIPLINE","GOVERNANCE_AND_CONTROLS"],
                 "hiringUrgency":"URGENT","confidential":true,"internalContext":"Keep discreet"}""")
                .andExpect(jsonPath("$.context.mandateReason").value("GROWTH_EXPANSION"))
                .andExpect(jsonPath("$.context.strategicPriorities.length()").value(2))
                .andExpect(jsonPath("$.context.confidential").value(true));

        String managerId = "11111111-1111-4111-8111-111111111111";
        String seatId = "22222222-2222-4222-8222-222222222222";
        String controllerId = "33333333-3333-4333-8333-333333333333";
        putStep(admin, projectId, "reporting", """
                {"orgChart":[
                   {"nodeId":"%s","parentNodeId":null,"title":"Group CEO","name":"Hassan Al Marri",
                    "mandateSeat":false,"canvasX":null,"canvasY":null},
                   {"nodeId":"%s","parentNodeId":"%s","title":null,"name":null,
                    "mandateSeat":true,"canvasX":120.5,"canvasY":80.0},
                   {"nodeId":"%s","parentNodeId":"%s","title":"Financial Controller","name":"Layla Nasser",
                    "mandateSeat":false,"canvasX":null,"canvasY":null}],
                 "teamSize":"38 across the finance function","targetStart":"2026-09-15",
                 "noticeValue":90,"noticeUnit":"DAYS"}"""
                .formatted(managerId, seatId, managerId, controllerId, seatId))
                .andExpect(jsonPath("$.reporting.orgChart.length()").value(3))
                .andExpect(jsonPath("$.reporting.orgChart[0].name").value("Hassan Al Marri"))
                // The dragged seat keeps where it was put; the others are laid out by the screen.
                .andExpect(jsonPath("$.reporting.orgChart[1].canvasX").value(120.5))
                .andExpect(jsonPath("$.reporting.orgChart[2].parentNodeId").value(seatId))
                .andExpect(jsonPath("$.reporting.teamSize").value("38 across the finance function"))
                .andExpect(jsonPath("$.reporting.noticeUnit").value("DAYS"));

        putStep(admin, projectId, "compensation", """
                {"currency":"AED","salaryMin":90000,"salaryMax":120000,"baseSalaryMode":"MONTHLY",
                 "bonusValue":40.5,"bonusBasis":"PERCENT_OF_BASE",
                 "incentiveType":"LTIP_CASH","incentiveAmount":600000,
                 "incentiveVesting":"3-year vesting, 33% annually",
                 "benefits":[{"name":"Housing allowance","amount":8000,"frequency":"MONTHLY"},
                             {"name":"Annual home leave","amount":null,"frequency":"YEARLY"}]}""")
                .andExpect(jsonPath("$.compensation.baseSalaryMode").value("MONTHLY"))
                .andExpect(jsonPath("$.compensation.bonusValue").value(40.5))
                .andExpect(jsonPath("$.compensation.incentiveType").value("LTIP_CASH"))
                .andExpect(jsonPath("$.compensation.benefits[1].amount").isEmpty())
                .andExpect(jsonPath("$.compensation.benefits[1].frequency").value("YEARLY"));

        // Every step is stored independently: writing four of them leaves all four readable at once.
        JsonNode brief = readBrief(admin, projectId);
        assertThat(brief.get("context").get("hiringUrgency").asString()).isEqualTo("URGENT");
        assertThat(brief.get("reporting").get("teamSize").asString())
                .isEqualTo("38 across the finance function");
        assertThat(brief.get("reporting").get("orgChart").size()).isEqualTo(3);
        assertThat(brief.get("compensation").get("currency").asString()).isEqualTo("AED");

        // "Target start" is the mandate's one target date, not a second field on the brief.
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].targetDate").value("2026-09-15"));
    }

    @Test
    @DisplayName("writing one step leaves the others untouched")
    void oneStepDoesNotDisturbAnother() throws Exception {
        String admin = adminOf("Isolation Firm");
        String projectId = createProject(admin, createClient(admin, "Aldar", "UAE"), "CFO");

        putStep(admin, projectId, "context", """
                {"mandateReason":"BACKFILL","businessDriver":"Incumbent retiring.",
                 "strategicPriorities":["TALENT_DEVELOPMENT"],"hiringUrgency":"PRIORITY",
                 "confidential":false,"internalContext":null}""");
        putStep(admin, projectId, "compensation", """
                {"currency":"SAR","salaryMin":1,"salaryMax":2,"baseSalaryMode":"ANNUAL",
                 "bonusValue":null,"bonusBasis":null,"incentiveType":null,"incentiveAmount":null,
                 "incentiveVesting":null,"benefits":[]}""");

        JsonNode brief = readBrief(admin, projectId);
        assertThat(brief.get("context").get("mandateReason").asString()).isEqualTo("BACKFILL");
        assertThat(brief.get("context").get("businessDriver").asString()).isEqualTo("Incumbent retiring.");
        assertThat(brief.get("compensation").get("currency").asString()).isEqualTo("SAR");
        // The template's own seeding survived both writes — neither step owns the assessment lists.
        assertThat(brief.get("assessment").get("criteria").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("a chart that is not a chart is refused rather than stored and drawn")
    void malformedChartsAreRefused() throws Exception {
        String admin = adminOf("Chart Firm");
        String projectId = createProject(admin, createClient(admin, "Aldar", "UAE"), "CFO");
        String first = "11111111-1111-4111-8111-111111111111";
        String second = "22222222-2222-4222-8222-222222222222";

        // Two seats claiming to be the role, so nothing can answer "who does this report to".
        rejectChart(admin, projectId, """
                [{"nodeId":"%s","parentNodeId":null,"mandateSeat":true},
                 {"nodeId":"%s","parentNodeId":null,"mandateSeat":true}]"""
                .formatted(first, second));

        // No seat for the role at all.
        rejectChart(admin, projectId, """
                [{"nodeId":"%s","parentNodeId":null,"mandateSeat":false}]""".formatted(first));

        // A seat reporting to one that is not on the chart.
        rejectChart(admin, projectId, """
                [{"nodeId":"%s","parentNodeId":"%s","mandateSeat":true}]"""
                .formatted(first, second));

        // A loop, which would make every traversal of the chart run forever.
        rejectChart(admin, projectId, """
                [{"nodeId":"%s","parentNodeId":"%s","mandateSeat":true},
                 {"nodeId":"%s","parentNodeId":"%s","mandateSeat":false}]"""
                .formatted(first, second, second, first));

        // The seeded chart survived every refusal.
        assertThat(readBrief(admin, projectId).get("reporting").get("orgChart").size()).isEqualTo(2);
    }

    @Test
    @DisplayName("criteria and competencies replace as ordered lists")
    void listsReplaceAndKeepOrder() throws Exception {
        String admin = adminOf("Lists Firm");
        String projectId = createProject(admin, createClient(admin, "Emaar", "UAE"), "CFO");

        putStep(admin, projectId, "criteria", """
                {"criteria":[
                  {"text":"Arabic language skills","mode":"PREFERRED","fromBrief":false},
                  {"text":"Board reporting experience","mode":"REQUIRED","fromBrief":true}]}""")
                .andExpect(jsonPath("$.assessment.criteria.length()").value(2))
                .andExpect(jsonPath("$.assessment.criteria[0].text").value("Arabic language skills"))
                .andExpect(jsonPath("$.assessment.criteria[1].mode").value("REQUIRED"));

        putStep(admin, projectId, "competencies", """
                {"technical":[{"name":"Treasury","description":"Debt and liquidity","weight":60},
                              {"name":"Controls","description":null,"weight":30}],
                 "behavioural":[{"name":"Leadership","description":"Sets direction","weight":100}]}""")
                .andExpect(jsonPath("$.assessment.technical[0].name").value("Treasury"))
                .andExpect(jsonPath("$.assessment.technical[0].description").value("Debt and liquidity"))
                .andExpect(jsonPath("$.assessment.technical[1].weight").value(30))
                .andExpect(jsonPath("$.assessment.behavioural.length()").value(1));
    }

    @Test
    @DisplayName("publishing stamps the brief, keeps the first stamp, and freezes nothing")
    void publishingIsAStampNotALock() throws Exception {
        String admin = adminOf("Publish Firm");
        String projectId = createProject(admin, createClient(admin, "Mubadala", "UAE"), "CFO");

        String stampedAt = body(mvc.perform(post(positionUrl(projectId) + "/publish")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publication.publishedBy").value("Alok Kumar"))
                .andReturn())
                .get("publication").get("publishedAt").asString();

        // Publishing again keeps the original stamp: the date can end up on a client-facing document.
        mvc.perform(post(positionUrl(projectId) + "/publish")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.publication.publishedAt").value(stampedAt));

        // V38 retired the lock, and it does not come back: a published brief still accepts every write.
        putStep(admin, projectId, "details", """
                {"roleTitle":"CFO","department":"Finance","location":"Dubai","employmentType":null,
                 "seniority":"C_SUITE","responsibilities":[],"narrative":null}""")
                .andExpect(jsonPath("$.details.department").value("Finance"))
                .andExpect(jsonPath("$.publication.publishedAt").value(stampedAt));

        mvc.perform(delete(positionUrl(projectId) + "/publish")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publication.publishedAt").isEmpty())
                .andExpect(jsonPath("$.publication.publishedBy").isEmpty());
    }

    @Test
    @DisplayName("a position description attaches, replaces, downloads and is removed")
    void documentAttachesAndIsRemoved() throws Exception {
        String admin = adminOf("Document Firm");
        String projectId = createProject(admin, createClient(admin, "Masdar", "UAE"), "CFO");

        attach(admin, projectId, "brief.pdf", "application/pdf", "first draft")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document.fileName").value("brief.pdf"))
                .andExpect(jsonPath("$.document.contentType").value("application/pdf"))
                .andExpect(jsonPath("$.document.fileSize").value("first draft".length()));

        // Replacing keeps one document per position rather than accumulating versions.
        attach(admin, projectId, "brief-v2.pdf", "application/pdf", "second draft")
                .andExpect(jsonPath("$.document.fileName").value("brief-v2.pdf"));

        mvc.perform(get(positionUrl(projectId) + "/document")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(result -> assertThat(result.getResponse().getContentAsString())
                        .isEqualTo("second draft"))
                .andExpect(result -> {
                    assertThat(result.getResponse().getHeader("Content-Disposition"))
                            .contains("attachment").contains("brief-v2.pdf");
                    // Never served as the type it was uploaded as — see PositionDocumentController.
                    assertThat(result.getResponse().getContentType())
                            .isEqualTo(MediaType.APPLICATION_OCTET_STREAM_VALUE);
                    assertThat(result.getResponse().getHeader("X-Content-Type-Options"))
                            .isEqualTo("nosniff");
                });

        mvc.perform(delete(positionUrl(projectId) + "/document")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.document").isEmpty());
    }

    @Test
    @DisplayName("a document type outside the allowlist is refused, whatever the part claims")
    void unsupportedDocumentTypeIsRefused() throws Exception {
        String admin = adminOf("Reject Firm");
        String projectId = createProject(admin, createClient(admin, "Tabreed", "UAE"), "CFO");

        attach(admin, projectId, "payload.exe", "application/x-msdownload", "MZ")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    @DisplayName("a project whose position row is missing gets one seeded lazily on first read")
    void missingPositionRowIsSeededOnRead() throws Exception {
        String admin = adminOf("Legacy Firm");
        String projectId = createProject(admin, createClient(admin, "Fine Hygienic", "Jordan"), "CEO");

        // Simulate a pre-V7 project: drop its seeded brief outright.
        positionRows.findByProjectId(UUID.fromString(projectId)).ifPresent(positionRows::delete);

        mvc.perform(get(positionUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.details.location").value("Jordan"))
                .andExpect(jsonPath("$.assessment.criteria.length()").value(3));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static String positionUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/position";
    }

    private org.springframework.test.web.servlet.ResultActions putStep(
            String token, String projectId, String step, String bodyJson) throws Exception {
        return mvc.perform(put(positionUrl(projectId) + "/" + step)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyJson))
                .andExpect(status().isOk());
    }

    private org.springframework.test.web.servlet.ResultActions attach(
            String token, String projectId, String fileName, String contentType, String content)
            throws Exception {
        return mvc.perform(multipart(positionUrl(projectId) + "/document")
                .file(new MockMultipartFile("file", fileName, contentType,
                        content.getBytes(StandardCharsets.UTF_8)))
                .header("Authorization", "Bearer " + token));
    }

    private void rejectChart(String token, String projectId, String chartJson) throws Exception {
        mvc.perform(put(positionUrl(projectId) + "/reporting")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orgChart":%s,"teamSize":null,"targetStart":null,
                                 "noticeValue":null,"noticeUnit":null}""".formatted(chartJson)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private JsonNode readBrief(String token, String projectId) throws Exception {
        return body(mvc.perform(get(positionUrl(projectId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static int sum(JsonNode panel) {
        int total = 0;
        for (JsonNode row : panel) {
            total += row.get("weight").asInt();
        }
        return total;
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private String createClient(String token, String name, String hqCountry) throws Exception {
        String hq = hqCountry == null ? "null" : "\"%s\"".formatted(hqCountry);
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s","hqCountry":%s}
                                """.formatted(name, hq)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String createProject(String token, String clientId, String position) throws Exception {
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}
                                """.formatted(clientId, position)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
