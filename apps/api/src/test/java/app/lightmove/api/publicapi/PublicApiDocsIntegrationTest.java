package app.lightmove.api.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** The OpenAPI document and Swagger UI open without a key, and describe the public API alone. */
@IntegrationTest
class PublicApiDocsIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @DisplayName("the spec is OpenAPI 3.1, lists only public routes, and asks for a bearer key on each")
    void spec() throws Exception {
        JsonNode spec = json.readTree(mvc.perform(get("/api/v1/public/openapi.json"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(spec.get("openapi").asText()).startsWith("3.1");
        assertThat(spec.at("/info/title").asText()).isEqualTo("Uncava Public API");
        assertThat(spec.get("paths").propertyNames()).isNotEmpty().allMatch(path -> path.startsWith("/api/v1/public/"));
        assertThat(spec.at("/components/securitySchemes/apiKey/scheme").asText()).isEqualTo("bearer");
        assertThat(spec.at("/security/0").has("apiKey")).isTrue();
        assertThat(spec.at("/paths/~1api~1v1~1public~1me/get/responses").propertyNames())
                .contains("200", "401", "429");
        assertThat(spec.at("/components/schemas/CallingKey/properties/scopes/items/enum").toString())
                .isEqualTo("[\"projects:read\",\"companies:read\",\"candidates:read\","
                        + "\"candidates.contacts:read\",\"candidates.compensation:read\",\"mcp:use\"]");
    }

    @Test
    @DisplayName("each read route names its scope, answers JSON, lists its errors as problems, and offers the wire values a filter takes")
    void readRoutes() throws Exception {
        JsonNode paths = json.readTree(mvc.perform(get("/api/v1/public/openapi.json"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("paths");
        Map<String, String> scopeByRoute = Map.of(
                "/api/v1/public/projects", "projects:read",
                "/api/v1/public/projects/{projectId}", "projects:read",
                "/api/v1/public/projects/{projectId}/companies", "companies:read",
                "/api/v1/public/projects/{projectId}/candidates", "candidates:read",
                "/api/v1/public/projects/{projectId}/universe", "candidates:read");

        scopeByRoute.forEach((route, scope) -> {
            JsonNode operation = paths.at("/" + route.replace("/", "~1") + "/get");
            assertThat(operation.get("description").asText()).contains("`" + scope + "`");
            assertThat(operation.get("responses").propertyNames()).contains("200", "400", "401", "403", "429");
            assertThat(operation.at("/responses/200/content").propertyNames()).containsExactly("application/json");
            assertThat(operation.at("/responses/403/content/application~1problem+json/schema/$ref").asText())
                    .endsWith("/Problem");
        });
        JsonNode companies = paths.at("/~1api~1v1~1public~1projects~1{projectId}~1companies/get");
        JsonNode candidates = paths.at("/~1api~1v1~1public~1projects~1{projectId}~1candidates/get");
        assertThat(companies.get("responses").has("404")).isTrue();
        assertThat(paths.at("/~1api~1v1~1public~1projects~1{projectId}~1universe/get/description").asText())
                .contains("`companies:read`").contains("PUBLIC_API_UNIVERSE_TOO_LARGE");
        assertThat(enumOf(companies, "stage")).containsExactlyElementsOf(
                Arrays.stream(TriageCompanyStatus.values()).map(TriageCompanyStatus::value).toList());
        assertThat(enumOf(candidates, "status")).containsExactlyElementsOf(
                Arrays.stream(CandidateStatus.values()).map(CandidateStatus::value).toList());
    }

    private static List<String> enumOf(JsonNode operation, String parameter) {
        return operation.get("parameters").valueStream()
                .filter(candidate -> parameter.equals(candidate.get("name").asText()))
                .findFirst().orElseThrow()
                .at("/schema/enum").valueStream().map(JsonNode::asText).toList();
    }

    @Test
    @DisplayName("Swagger UI opens at /docs, its assets are served under it, and no other address serves the spec or the UI")
    void ui() throws Exception {
        mvc.perform(get("/api/v1/public/docs"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/api/v1/public/docs/swagger-ui/index.html"));
        mvc.perform(get("/api/v1/public/docs/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Security-Policy", containsString("script-src 'self'")));
        assertThat(mvc.perform(get("/api/v1/public/docs/swagger-ui/swagger-initializer.js"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .contains("/api/v1/public/openapi.json");

        assertThat(mvc.perform(get("/v3/api-docs")).andReturn().getResponse().getContentAsString())
                .doesNotContain("\"openapi\"");
        for (String elsewhere : new String[] {"/swagger-ui/index.html", "/webjars/swagger-ui/index.html"}) {
            assertThat(mvc.perform(get(elsewhere)).andReturn().getResponse().getContentAsString())
                    .doesNotContain("swagger-ui");
        }
    }
}
