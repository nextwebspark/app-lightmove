package app.lightmove.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/**
 * Custom dashboards: kept per member and shared read-only, checked against the query library on save, refused to a
 * client seat, and every library query answering in the shape it declares — gender counted only where a researcher
 * recorded it, and held back for a level too small to hide anyone in.
 */
@IntegrationTest
class DashboardIntegrationTest extends FlowTestSupport {

    private static final String WORKSPACE = "/api/v1/dashboards";

    @Autowired @FixtureJdbc JdbcTemplate db;

    private String admin;

    @Test
    @DisplayName("a dashboard is its owner's; shared, teammates open it read-only; a stale save is refused")
    void ownershipSharingAndStaleness() throws Exception {
        firm("Dashboard Share Firm");
        String colleagueEmail = "maya@" + domain;
        inviteAndAccept(admin, "Maya Saleh", colleagueEmail, "MEMBER");
        String colleague = login(colleagueEmail);

        JsonNode created = body(save(admin, post(WORKSPACE), "Pipeline across positions", false, null,
                widget("openPositions", "kpi", "{}", "S"), widget("positionsBy", "donut", "{\"group\":\"stage\"}", "M"))
                .andExpect(status().isCreated()).andReturn());
        String id = created.get("id").asText();
        assertThat(created.get("owned").asBoolean()).isTrue();
        assertThat(created.get("widgets").get(1).get("options").get("group").asText()).isEqualTo("stage");
        assertThat(created.get("widgets").get(1).get("options").get("top").asText()).isEqualTo("all");
        assertThat(list(colleague, WORKSPACE)).isEmpty();

        long version = created.get("version").asLong();
        JsonNode shared = body(save(admin, put(WORKSPACE + "/" + id), "Pipeline across positions", true, version,
                widget("openPositions", "kpi", "{}", "S")).andExpect(status().isOk()).andReturn());
        List<JsonNode> seen = list(colleague, WORKSPACE);
        assertThat(seen).hasSize(1);
        assertThat(seen.getFirst().get("owned").asBoolean()).isFalse();

        assertThat(codeOf(save(colleague, put(WORKSPACE + "/" + id), "Mine now", true, shared.get("version").asLong())
                .andExpect(status().isForbidden()).andReturn())).isEqualTo("DASHBOARD_READ_ONLY");
        assertThat(codeOf(save(admin, put(WORKSPACE + "/" + id), "Renamed", true, version)
                .andExpect(status().isConflict()).andReturn())).isEqualTo("DASHBOARD_STALE");
        mvc.perform(delete(WORKSPACE + "/" + id).header("Authorization", "Bearer " + colleague))
                .andExpect(status().isForbidden());
        mvc.perform(delete(WORKSPACE + "/" + id).header("Authorization", "Bearer " + admin))
                .andExpect(status().isNoContent());
        assertThat(list(admin, WORKSPACE)).isEmpty();
    }

    @Test
    @DisplayName("a chart is checked on save: its query, a chart type that fits, its options, and at most thirty")
    void widgetsAreCheckedAgainstTheLibrary() throws Exception {
        firm("Dashboard Rules Firm");
        String cfo = mandate();
        String url = projectUrl(cfo);

        assertThat(codeOf(save(admin, post(url), "Board pack", false, null, widget("noSuchQuery", "bar", "{}", "M"))
                .andExpect(status().isNotFound()).andReturn())).isEqualTo("CHART_QUERY_NOT_FOUND");
        assertThat(codeOf(save(admin, post(url), "Board pack", false, null, widget("openPositions", "kpi", "{}", "S"))
                .andExpect(status().isNotFound()).andReturn())).isEqualTo("CHART_QUERY_NOT_FOUND");
        assertThat(codeOf(save(admin, post(url), "Board pack", false, null, widget("executivesPerWeek", "pie", "{}", "M"))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("CHART_TYPE_NOT_ALLOWED");
        assertThat(codeOf(save(admin, post(url), "Board pack", false, null,
                widget("medianPackageBy", "donut", "{}", "M"))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("CHART_TYPE_NOT_ALLOWED");
        assertThat(codeOf(save(admin, post(url), "Board pack", false, null,
                widget("executivesBy", "bar", "{\"group\":\"salary\"}", "M"))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("CHART_OPTION_INVALID");
        String[] many = new String[31];
        java.util.Arrays.fill(many, widget("executivesIdentified", "kpi", "{}", "S"));
        assertThat(codeOf(save(admin, post(url), "Board pack", false, null, many)
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("DASHBOARD_TOO_MANY_WIDGETS");
        save(admin, post(url), " ", false, null).andExpect(status().isBadRequest());

        save(admin, post(url), "Board pack", false, null, widget("executivesBy", "pie", "{\"group\":\"level\"}", "M"))
                .andExpect(status().isCreated());
        assertThat(list(admin, url)).hasSize(1);
        assertThat(list(admin, WORKSPACE)).isEmpty();
    }

    @Test
    @DisplayName("a client seat reaches no position dashboard, a pure client no workspace one, another firm nothing")
    void clientsAndOtherFirmsAreRefused() throws Exception {
        firm("Dashboard Gate Firm");
        String cfo = mandate();
        String url = projectUrl(cfo);
        String id = body(save(admin, post(url), "Ours", true, null).andExpect(status().isCreated()).andReturn())
                .get("id").asText();

        String seat = clientOn(cfo);
        mvc.perform(get(url).header("Authorization", "Bearer " + seat)).andExpect(status().isForbidden());
        mvc.perform(get(url + "/library").header("Authorization", "Bearer " + seat)).andExpect(status().isForbidden());
        run(seat, url, "executivesIdentified", "{}").andExpect(status().isForbidden());

        String pureClient = clientRepresentative(admin, "Sami Haddad", "sami@client-" + domain);
        mvc.perform(get(WORKSPACE).header("Authorization", "Bearer " + pureClient)).andExpect(status().isNotFound());
        run(pureClient, WORKSPACE, "openPositions", "{}").andExpect(status().isNotFound());

        String outsider = otherFirm();
        mvc.perform(put(url + "/" + id).header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"shared\":false,\"widgets\":[],\"version\":0}"))
                .andExpect(status().isNotFound());
        mvc.perform(put(WORKSPACE + "/" + id).header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"x\",\"shared\":false,\"widgets\":[],\"version\":0}"))
                .andExpect(status().isNotFound());
        assertThat(list(outsider, WORKSPACE)).isEmpty();
    }

    @Test
    @DisplayName("every library query answers in its shape; gender is researcher-recorded only and small levels hidden")
    void everyQueryAnswersInItsShape() throws Exception {
        firm("Dashboard Query Firm");
        String cfo = mandate();
        String url = projectUrl(cfo);
        add(cfo, "Fatima Al Mazrouei", "C-Suite", "female");
        add(cfo, "Noura Al Qasimi", "C-Suite", "female");
        add(cfo, "Khalid Al Harbi", "C-Suite", "male");
        add(cfo, "Reem Al Nuaimi", "C-Suite", "female");
        add(cfo, "Hamad Al Suwaidi", "N-1", "male");
        db.update("UPDATE app_lm_person SET ai_inferred_fields = '[\"gender\"]'::jsonb WHERE full_name = ?",
                "Reem Al Nuaimi");

        for (JsonNode query : list(admin, url + "/library")) {
            JsonNode result = body(run(admin, url, query.get("id").asText(), "{}")
                    .andExpect(status().isOk()).andReturn());
            assertThat(result.get("shape").asText()).as(query.get("id").asText())
                    .isEqualTo(query.get("shape").asText());
            assertThat(result.get("allowedCharts").size()).isPositive();
        }

        JsonNode byLevel = body(run(admin, url, "executivesBy", "{\"group\":\"level\"}")
                .andExpect(status().isOk()).andReturn());
        assertThat(byLevel.get("labels").valueStream().map(JsonNode::asText).toList()).containsExactly("C-Suite", "N-1");
        assertThat(byLevel.get("values").get(0).asDouble()).isEqualTo(4d);

        JsonNode gender = body(run(admin, url, "genderByLevel", "{}").andExpect(status().isOk()).andReturn());
        assertThat(gender.get("labels").valueStream().map(JsonNode::asText).toList()).containsExactly("C-Suite", "N-1");
        assertThat(gender.get("suppressed").valueStream().map(JsonNode::asBoolean).toList()).containsExactly(false, true);
        assertThat(gender.get("series").get(0).get("name").asText()).isEqualTo("Female");
        assertThat(gender.get("series").get(0).get("values").get(0).asDouble()).isEqualTo(2d);
        assertThat(gender.get("series").get(1).get("values").get(1).asDouble()).isZero();

        assertThat(codeOf(run(admin, url, "executivesBy", "{\"top\":\"7\"}")
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("CHART_OPTION_INVALID");
        assertThat(codeOf(run(admin, url, "openPositions", "{}")
                .andExpect(status().isNotFound()).andReturn())).isEqualTo("CHART_QUERY_NOT_FOUND");

        for (JsonNode query : list(admin, WORKSPACE + "/library")) {
            JsonNode result = body(run(admin, WORKSPACE, query.get("id").asText(), "{}")
                    .andExpect(status().isOk()).andReturn());
            assertThat(result.get("shape").asText()).as(query.get("id").asText())
                    .isEqualTo(query.get("shape").asText());
        }
        JsonNode pipeline = body(run(admin, WORKSPACE, "pipelineByStage", "{}").andExpect(status().isOk()).andReturn());
        assertThat(pipeline.get("values").valueStream().mapToDouble(JsonNode::asDouble).sum()).isEqualTo(5d);
        JsonNode open = body(run(admin, WORKSPACE, "openPositions", "{}").andExpect(status().isOk()).andReturn());
        assertThat(open.get("kpi").get("value").asText()).isEqualTo("1");
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static String widget(String queryId, String chart, String options, String size) {
        return """
                {"queryId":"%s","chart":"%s","options":%s,"title":null,"size":"%s"}""".formatted(queryId, chart,
                options, size);
    }

    private ResultActions save(String token,
                               org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request,
                               String name, boolean shared, Long version, String... widgets) throws Exception {
        return mvc.perform(request.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"%s","shared":%s,"widgets":[%s],"version":%s}"""
                        .formatted(name, shared, String.join(",", widgets), version)));
    }

    private ResultActions run(String token, String base, String queryId, String options) throws Exception {
        return mvc.perform(post(base + "/query/" + queryId).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"options\":" + options + "}"));
    }

    private List<JsonNode> list(String token, String url) throws Exception {
        JsonNode found = body(mvc.perform(get(url).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
        List<JsonNode> items = new ArrayList<>();
        found.forEach(items::add);
        return items;
    }

    private void firm(String name) throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), name);
        admin = login(address);
    }

    private String otherFirm() throws Exception {
        String address = "omar@other-" + domain;
        createWorkspace(verifiedUser("Omar Khoury", address), "Another Firm");
        return login(address);
    }

    private String mandate() throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Finance Unit"}"""))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Chief Financial Officer"}""".formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String clientOn(String projectId) throws Exception {
        String clientEmail = "client@dashboard-client-" + domain;
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"A Client","position":"Chair","email":"%s"}
                                """.formatted(clientEmail)))
                .andExpect(status().isOk());
        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"A Client","password":"%s","termsAccepted":true}
                                """.formatted(email.latestTokenFor(clientEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }

    private void add(String projectId, String fullName, String seniority, String gender) throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s","seniority":"%s","gender":"%s"}""".formatted(fullName, seniority,
                                gender)))
                .andExpect(status().isCreated());
    }

    private static String projectUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/dashboards";
    }

    @SuppressWarnings("unused")
    private static String randomId() {
        return UUID.randomUUID().toString();
    }
}
