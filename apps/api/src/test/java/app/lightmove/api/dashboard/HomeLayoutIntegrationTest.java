package app.lightmove.api.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;

/** A member's own Home: kept per person and workspace, its tiles and charts checked on save, refused to a client. */
@IntegrationTest
class HomeLayoutIntegrationTest extends FlowTestSupport {

    private static final String HOME = "/api/v1/me/home";
    private static final String NEEDS = "{\"kind\":\"NEEDS\",\"layout\":{\"x\":0,\"y\":0,\"w\":8,\"h\":10}}";

    @Test
    @DisplayName("Home is absent until saved, then kept per person; reset forgets it")
    void savedPerPersonAndReset() throws Exception {
        String admin = firm("Home Layout Firm");
        String colleagueEmail = "maya@" + domain;
        inviteAndAccept(admin, "Maya Saleh", colleagueEmail, "MEMBER");
        String colleague = login(colleagueEmail);

        assertThat(read(admin).get("layout").isNull()).isTrue();

        JsonNode saved = body(save(admin, """
                {"tiles":[%s,
                  {"kind":"TODAY","layout":{"x":8,"y":0,"w":4,"h":5}},
                  {"kind":"CHART","queryId":"positionsBy","chart":"bar","options":{"group":"stage"},"title":"By stage",
                   "layout":{"x":0,"y":10,"w":6,"h":7}}],
                 "needs":{"ATTENTION":false},"landing":"POSITIONS"}""".formatted(NEEDS))
                .andExpect(status().isOk()).andReturn()).get("layout");
        assertThat(saved.get("tiles")).hasSize(3);
        JsonNode chart = saved.get("tiles").get(2);
        assertThat(chart.get("chart").asText()).isEqualTo("bar");
        assertThat(chart.get("options").get("group").asText()).isEqualTo("stage");
        assertThat(chart.get("options").get("top").asText()).isEqualTo("all");
        assertThat(chart.get("layout").get("w").asInt()).isEqualTo(6);
        assertThat(saved.get("needs").get("ATTENTION").asBoolean()).isFalse();
        assertThat(saved.get("needs").get("NEEDS_REPLY").asBoolean()).isTrue();
        assertThat(saved.get("landing").asText()).isEqualTo("POSITIONS");

        assertThat(read(admin).get("layout").get("tiles")).hasSize(3);
        assertThat(read(colleague).get("layout").isNull()).isTrue();

        mvc.perform(delete(HOME).header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());
        assertThat(read(admin).get("layout").isNull()).isTrue();
    }

    @Test
    @DisplayName("a save keeps Needs you, one of each section at a readable size, workspace charts that fit, twelve at most")
    void tilesAreChecked() throws Exception {
        String admin = firm("Home Rules Firm");
        String today = "{\"kind\":\"TODAY\",\"layout\":{\"x\":8,\"y\":0,\"w\":4,\"h\":5}}";

        assertThat(codeOf(save(admin, "{\"tiles\":[%s],\"landing\":\"HOME\"}".formatted(today))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(save(admin, "{\"tiles\":[%s,%s,%s],\"landing\":\"HOME\"}".formatted(NEEDS, today, today))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(save(admin, "{\"tiles\":[%s,{\"kind\":\"INBOX\",\"layout\":{\"x\":0,\"y\":0,\"w\":4,\"h\":4}}],\"landing\":\"HOME\"}"
                .formatted(NEEDS)).andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(save(admin, "{\"tiles\":[%s,{\"kind\":\"TODAY\",\"layout\":{\"x\":10,\"y\":0,\"w\":4,\"h\":4}}],\"landing\":\"HOME\"}"
                .formatted(NEEDS)).andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(save(admin, "{\"tiles\":[{\"kind\":\"NEEDS\",\"layout\":{\"x\":0,\"y\":0,\"w\":1,\"h\":1}}],\"landing\":\"HOME\"}")
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(save(admin, "{\"tiles\":[%s],\"needs\":{\"GOSSIP\":true},\"landing\":\"HOME\"}".formatted(NEEDS))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(save(admin, "{\"tiles\":[%s],\"landing\":\"TASKS\"}".formatted(NEEDS))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");
        assertThat(codeOf(save(admin, "{\"tiles\":[%s,%s],\"landing\":\"HOME\"}".formatted(NEEDS,
                        chart("executivesIdentified", "kpi"))) // a position's query, not the workspace's
                .andExpect(status().isNotFound()).andReturn())).isEqualTo("CHART_QUERY_NOT_FOUND");
        assertThat(codeOf(save(admin, "{\"tiles\":[%s,%s],\"landing\":\"HOME\"}".formatted(NEEDS,
                        chart("openPositions", "pie")))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("CHART_TYPE_NOT_ALLOWED");
        String thirteen = String.join(",", java.util.Collections.nCopies(13, chart("openPositions", "kpi")));
        assertThat(codeOf(save(admin, "{\"tiles\":[%s,%s],\"landing\":\"HOME\"}".formatted(NEEDS, thirteen))
                .andExpect(status().isBadRequest()).andReturn())).isEqualTo("VALIDATION_FAILED");

        save(admin, "{\"tiles\":[%s,%s],\"landing\":\"HOME\"}".formatted(NEEDS, chart("openPositions", "kpi")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a save opened before another window's save, or before a reset, is refused rather than lost")
    void aStaleWindowCannotOverwrite() throws Exception {
        String admin = firm("Home Stale Firm");
        String body = "{\"tiles\":[%s],\"landing\":\"%s\",\"version\":%s}";

        long first = body(save(admin, body.formatted(NEEDS, "HOME", "null")).andExpect(status().isOk()).andReturn())
                .get("layout").get("version").asLong();
        assertThat(codeOf(save(admin, body.formatted(NEEDS, "POSITIONS", "null"))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("HOME_LAYOUT_STALE");

        long second = body(save(admin, body.formatted(NEEDS, "POSITIONS", first)).andExpect(status().isOk()).andReturn())
                .get("layout").get("version").asLong();
        assertThat(second).isGreaterThan(first);
        assertThat(codeOf(save(admin, body.formatted(NEEDS, "HOME", first))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("HOME_LAYOUT_STALE");
        assertThat(read(admin).get("layout").get("landing").asText()).isEqualTo("POSITIONS");

        mvc.perform(delete(HOME).header("Authorization", "Bearer " + admin)).andExpect(status().isNoContent());
        assertThat(codeOf(save(admin, body.formatted(NEEDS, "HOME", second))
                .andExpect(status().isConflict()).andReturn())).isEqualTo("HOME_LAYOUT_STALE");
        save(admin, body.formatted(NEEDS, "HOME", "null")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a pure client has no Home layout to read or save, and a layout is never another firm's")
    void clientsAndOtherFirmsAreRefused() throws Exception {
        String admin = firm("Home Gate Firm");
        save(admin, "{\"tiles\":[%s],\"landing\":\"HOME\"}".formatted(NEEDS)).andExpect(status().isOk());

        String pureClient = clientRepresentative(admin, "Sami Haddad", "sami@client-" + domain);
        mvc.perform(get(HOME).header("Authorization", "Bearer " + pureClient)).andExpect(status().isNotFound());
        save(pureClient, "{\"tiles\":[%s],\"landing\":\"HOME\"}".formatted(NEEDS)).andExpect(status().isNotFound());

        String outsiderEmail = "omar@other-" + domain;
        createWorkspace(verifiedUser("Omar Khoury", outsiderEmail), "Another Firm");
        assertThat(read(login(outsiderEmail)).get("layout").isNull()).isTrue();
    }

    private String firm(String name) throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), name);
        return login(address);
    }

    private static String chart(String queryId, String chart) {
        return "{\"kind\":\"CHART\",\"queryId\":\"%s\",\"chart\":\"%s\",\"layout\":{\"x\":0,\"y\":10,\"w\":4,\"h\":3}}"
                .formatted(queryId, chart);
    }

    private JsonNode read(String token) throws Exception {
        return body(mvc.perform(get(HOME).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andReturn());
    }

    private ResultActions save(String token, String json) throws Exception {
        return mvc.perform(put(HOME).header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }
}
