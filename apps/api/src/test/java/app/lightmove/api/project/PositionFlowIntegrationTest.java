package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import app.lightmove.api.project.repository.PositionRepository;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * The position brief end to end: template seeding at creation, snapshot writes, the lock gate,
 * whole-brief freezing, admin unlock, and the lazy seed for pre-V7 projects.
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
                .andExpect(jsonPath("$.locked").value(false))
                .andExpect(jsonPath("$.location").value("UAE"))
                .andExpect(jsonPath("$.reportsTo").value("Group CEO"))
                .andExpect(jsonPath("$.employmentType").value("FULL_TIME_PERMANENT"))
                .andExpect(jsonPath("$.currency").value("USD"))
                .andExpect(jsonPath("$.criteria[0].fromBrief").value(true))
                .andExpect(jsonPath("$.criteria[0].mode").value("REQUIRED"))
                .andExpect(jsonPath("$.technical[0].name").value("Financial Reporting & Controls"));
    }

    @Test
    @DisplayName("an unrecognised title falls back to the generic executive template, still lockable")
    void unknownTitleSeedsTheGenericTemplate() throws Exception {
        String admin = adminOf("Generic Firm");
        String projectId = createProject(admin, createClient(admin, "Al Rabie", null),
                "Head of Alchemy");

        JsonNode position = body(mvc.perform(get(positionUrl(projectId))
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(position.get("location").isNull()).isTrue();
        assertThat(sum(position.get("technical"))).isEqualTo(100);
        assertThat(sum(position.get("behavioural"))).isEqualTo(100);
        assertThat(position.get("criteria").size()).isGreaterThan(0);
    }

    @Test
    @DisplayName("the scalar snapshot PUT round-trips, benefits included")
    void scalarSnapshotRoundTrips() throws Exception {
        String admin = adminOf("Snapshot Firm");
        String projectId = createProject(admin, createClient(admin, "Agthia", "UAE"), "CFO");

        mvc.perform(put(positionUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandateReason":"SUCCESSION","internalContext":"Keep discreet",
                                 "narrative":"A hands-on CFO.","reportsTo":"Group CEO",
                                 "directReports":4,"teamSize":38,
                                 "location":"Abu Dhabi, UAE","employmentType":"FIXED_TERM_CONTRACT",
                                 "startTarget":"2026-09-15","salaryMin":450000,"salaryMax":550000,
                                 "currency":"AED","noticeValue":3,"noticeUnit":"MONTHS","bonusTargetPct":40,
                                 "ltip":"3-year vesting","benefits":["Housing allowance","Annual home leave"],
                                 "confidential":true}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mandateReason").value("SUCCESSION"))
                .andExpect(jsonPath("$.employmentType").value("FIXED_TERM_CONTRACT"))
                .andExpect(jsonPath("$.confidential").value(true));

        mvc.perform(get(positionUrl(projectId)).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.salaryMax").value(550000))
                .andExpect(jsonPath("$.currency").value("AED"))
                .andExpect(jsonPath("$.directReports").value(4))
                .andExpect(jsonPath("$.teamSize").value(38))
                .andExpect(jsonPath("$.noticeValue").value(3))
                .andExpect(jsonPath("$.noticeUnit").value("MONTHS"))
                .andExpect(jsonPath("$.bonusTargetPct").value(40))
                .andExpect(jsonPath("$.startTarget").value("2026-09-15"))
                .andExpect(jsonPath("$.benefits[0]").value("Housing allowance"))
                .andExpect(jsonPath("$.benefits[1]").value("Annual home leave"));

        // The "Target start" the position PUT set is the PROJECT's one target date, not a separate field.
        mvc.perform(get("/api/v1/projects").header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$[0].targetDate").value("2026-09-15"));
    }

    @Test
    @DisplayName("criteria and competencies replace as ordered lists")
    void listsReplaceAndKeepOrder() throws Exception {
        String admin = adminOf("Lists Firm");
        String projectId = createProject(admin, createClient(admin, "NMC", "UAE"), "CFO");

        mvc.perform(put(positionUrl(projectId) + "/criteria")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"criteria":[
                                  {"text":"Arabic language skills","mode":"PREFERRED","fromBrief":false},
                                  {"text":"Board reporting experience","mode":"REQUIRED","fromBrief":true}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.criteria.length()").value(2))
                .andExpect(jsonPath("$.criteria[0].text").value("Arabic language skills"))
                .andExpect(jsonPath("$.criteria[1].mode").value("REQUIRED"));

        mvc.perform(put(positionUrl(projectId) + "/competencies")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"technical":[{"name":"Treasury","weight":60},{"name":"Controls","weight":30}],
                                 "behavioural":[{"name":"Leadership","weight":100}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.technical[0].name").value("Treasury"))
                .andExpect(jsonPath("$.technical[1].weight").value(30))
                .andExpect(jsonPath("$.behavioural.length()").value(1));
    }

    @Test
    @DisplayName("locking demands balanced panels and a required criterion — then freezes the whole brief")
    void lockGateThenWholeBriefFreezes() throws Exception {
        String admin = adminOf("Lock Firm");
        String projectId = createProject(admin, createClient(admin, "ADQ", "UAE"), "CFO");

        // Unbalance one panel: the gate refuses.
        mvc.perform(put(positionUrl(projectId) + "/competencies")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"technical":[{"name":"Treasury","weight":90}],
                                 "behavioural":[{"name":"Leadership","weight":100}]}"""))
                .andExpect(status().isOk());
        MvcResult notReady = mvc.perform(post(positionUrl(projectId) + "/lock")
                        .header("Authorization", "Bearer " + admin))
                .andReturn();
        assertThat(notReady.getResponse().getStatus()).isEqualTo(409);
        assertThat(codeOf(notReady)).isEqualTo("POSITION_NOT_READY");

        // No required criterion: still refused.
        mvc.perform(put(positionUrl(projectId) + "/competencies")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"technical":[{"name":"Treasury","weight":100}],
                                 "behavioural":[{"name":"Leadership","weight":100}]}"""))
                .andExpect(status().isOk());
        mvc.perform(put(positionUrl(projectId) + "/criteria")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"criteria":[{"text":"Nice to have","mode":"PREFERRED","fromBrief":false}]}"""))
                .andExpect(status().isOk());
        assertThat(codeOf(mvc.perform(post(positionUrl(projectId) + "/lock")
                .header("Authorization", "Bearer " + admin)).andReturn()))
                .isEqualTo("POSITION_NOT_READY");

        // Ready: lock succeeds, and every write is now refused.
        mvc.perform(put(positionUrl(projectId) + "/criteria")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"criteria":[{"text":"Board experience","mode":"REQUIRED","fromBrief":false}]}"""))
                .andExpect(status().isOk());
        mvc.perform(post(positionUrl(projectId) + "/lock")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));

        assertThat(codeOf(mvc.perform(put(positionUrl(projectId))
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"mandateReason":"NEW_ROLE","currency":"USD","confidential":false}"""))
                .andReturn())).isEqualTo("POSITION_LOCKED");
        assertThat(codeOf(mvc.perform(put(positionUrl(projectId) + "/criteria")
                .header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"criteria":[]}"""))
                .andReturn())).isEqualTo("POSITION_LOCKED");
        assertThat(codeOf(mvc.perform(post(positionUrl(projectId) + "/lock")
                .header("Authorization", "Bearer " + admin))
                .andReturn())).isEqualTo("POSITION_LOCKED");

        // Unlock reopens the brief for editing.
        mvc.perform(post(positionUrl(projectId) + "/unlock")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));
        mvc.perform(put(positionUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"mandateReason":"NEW_ROLE","currency":"USD","confidential":false}"""))
                .andExpect(status().isOk());
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
                .andExpect(jsonPath("$.location").value("Jordan"))
                .andExpect(jsonPath("$.criteria.length()").value(3));
    }

    private static String positionUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/position";
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
                                {"name":"%s","hqCountry":%s}
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
