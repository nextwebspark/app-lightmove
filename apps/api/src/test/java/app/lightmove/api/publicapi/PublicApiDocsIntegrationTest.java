package app.lightmove.api.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
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
                        + "\"candidates.contacts:read\",\"candidates.compensation:read\"]");
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
