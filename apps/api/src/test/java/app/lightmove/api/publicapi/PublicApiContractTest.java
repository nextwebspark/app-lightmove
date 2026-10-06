package app.lightmove.api.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * The public contract, held still. The spec is committed at {@code docs/public-api/openapi.json} and any
 * difference fails here, so a change to what integrations see is reviewed in the PR that makes it.
 * After a deliberate change, rerun with {@code UPDATE_OPENAPI_SNAPSHOT=true} and commit the file; under
 * {@code CI} that variable fails the test instead, since a rewrite there would make it always pass. Every
 * figure the description states, and the server, is pinned to production's, so the committed file reads true.
 */
@IntegrationTest
@TestPropertySource(properties = {
        "lightmove.public-api.requests-per-minute=60",
        "lightmove.public-api.requests-per-minute-per-ip=300",
        "lightmove.company.list.default-page-size=25",
        "lightmove.company.list.max-page-size=100",
        "lightmove.web.base-url=https://beta.uncava.com"})
class PublicApiContractTest {

    private static final Path SNAPSHOT = repositoryRoot().resolve(Path.of("docs", "public-api", "openapi.json"));

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

    @Test
    @DisplayName("the generated spec matches the committed snapshot")
    void matchesTheSnapshot() throws Exception {
        String generated = json.writerWithDefaultPrettyPrinter().writeValueAsString(spec()) + "\n";

        if ("true".equals(System.getenv("UPDATE_OPENAPI_SNAPSHOT"))) {
            assertThat(System.getenv("CI")).as("UPDATE_OPENAPI_SNAPSHOT is refused under CI").isNull();
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, generated, StandardCharsets.UTF_8);
        }

        assertThat(SNAPSHOT).as("docs/public-api/openapi.json — rerun with UPDATE_OPENAPI_SNAPSHOT=true").exists();
        assertThat(json.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8)))
                .as("the public contract changed; review it, then rerun with UPDATE_OPENAPI_SNAPSHOT=true")
                .isEqualTo(json.readTree(generated));
    }

    @Test
    @DisplayName("the spec is well-formed 3.1: public paths only, every operation named and secured, every $ref resolves")
    void wellFormed() throws Exception {
        JsonNode spec = spec();

        assertThat(spec.get("openapi").asText()).startsWith("3.1.");
        assertThat(spec.at("/info/title").asText()).isNotBlank();
        assertThat(spec.get("paths").propertyNames()).isNotEmpty().allMatch(path -> path.startsWith("/api/v1/public/"));

        List<String> operationIds = new ArrayList<>();
        spec.get("paths").forEach(path -> path.properties().forEach(entry -> {
            JsonNode operation = entry.getValue();
            assertThat(entry.getKey()).isEqualTo("get");
            assertThat(operation.get("operationId").asText()).isNotBlank();
            assertThat(operation.get("summary").asText()).isNotBlank();
            assertThat(operation.get("responses").propertyNames()).contains("200", "401", "429");
            operationIds.add(operation.get("operationId").asText());
        }));
        assertThat(operationIds).doesNotHaveDuplicates();

        List<String> refs = new ArrayList<>();
        collectRefs(spec, refs);
        assertThat(refs).isNotEmpty().allSatisfy(ref -> {
            assertThat(ref).startsWith("#/components/schemas/");
            assertThat(spec.at(ref.substring(1)).isMissingNode()).as(ref).isFalse();
        });
    }

    private JsonNode spec() throws Exception {
        return json.readTree(mvc.perform(get("/api/v1/public/openapi.json"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static Path repositoryRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve(Path.of("apps", "api")))) {
                return dir;
            }
        }
        throw new IllegalStateException("No apps/api above " + Path.of("").toAbsolutePath());
    }

    private static void collectRefs(JsonNode node, List<String> refs) {
        if (node.isObject()) {
            node.properties().forEach(entry -> {
                if ("$ref".equals(entry.getKey())) {
                    refs.add(entry.getValue().asText());
                } else {
                    collectRefs(entry.getValue(), refs);
                }
            });
        } else if (node.isArray()) {
            node.forEach(child -> collectRefs(child, refs));
        }
    }
}
