package app.lightmove.api.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/** {@code lightmove.public-api.enabled: false} takes the whole surface down, the docs with it. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.public-api.enabled=false")
class PublicApiSwitchedOffIntegrationTest {

    @Autowired MockMvc mvc;

    @Test
    @DisplayName("every public route, the spec and Swagger UI answer 404, and springdoc serves the UI nowhere else")
    void everythingIsNotFound() throws Exception {
        for (String path : new String[] {"/api/v1/public/me", "/api/v1/public/openapi.json", "/api/v1/public/docs",
                "/api/v1/public/docs/swagger-ui/index.html"}) {
            assertThat(mvc.perform(get(path).header("Authorization", "Bearer uncava_pat_whatever"))
                    .andExpect(status().isNotFound()).andReturn().getResponse().getContentAsString())
                    .contains("\"code\":\"NOT_FOUND\"");
        }
        assertThat(mvc.perform(get("/webjars/swagger-ui/index.html")).andReturn().getResponse().getContentAsString())
                .doesNotContain("swagger-ui");
    }
}
