package app.lightmove.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/**
 * What every MCP client is told about our tools, held still: names, descriptions, annotations and schemas are committed
 * at {@code docs/mcp/tools.json}, so a change to what a model reads — a description above all, the one place an
 * instruction could be slipped in — is reviewed in the PR that makes it. After a deliberate change, rerun with
 * {@code UPDATE_MCP_TOOLS_SNAPSHOT=true} and commit the file; under {@code CI} that variable fails the test instead.
 */
@IntegrationTest
class McpToolContractTest extends McpFlowSupport {

    private static final Path SNAPSHOT = repositoryRoot().resolve(Path.of("docs", "mcp", "tools.json"));

    @Test
    @DisplayName("tools/list matches the committed snapshot")
    void matchesTheSnapshot() throws Exception {
        String key = keyOf(adminOf(domain), "projects:read", "mcp:use");
        List<JsonNode> tools = rpc(key, LIST_TOOLS).at("/result/tools").valueStream()
                .sorted(Comparator.comparing(tool -> tool.get("name").asText()))
                .toList();
        String generated = json.writerWithDefaultPrettyPrinter().writeValueAsString(tools) + "\n";

        if ("true".equals(System.getenv("UPDATE_MCP_TOOLS_SNAPSHOT"))) {
            assertThat(System.getenv("CI")).as("UPDATE_MCP_TOOLS_SNAPSHOT is refused under CI").isNull();
            Files.createDirectories(SNAPSHOT.getParent());
            Files.writeString(SNAPSHOT, generated, StandardCharsets.UTF_8);
        }

        assertThat(SNAPSHOT).as("docs/mcp/tools.json — rerun with UPDATE_MCP_TOOLS_SNAPSHOT=true").exists();
        assertThat(json.readTree(Files.readString(SNAPSHOT, StandardCharsets.UTF_8)))
                .as("what MCP clients are told changed; review it, then rerun with UPDATE_MCP_TOOLS_SNAPSHOT=true")
                .isEqualTo(json.readTree(generated));
    }

    private static Path repositoryRoot() {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            if (Files.isDirectory(dir.resolve(Path.of("apps", "api")))) {
                return dir;
            }
        }
        throw new IllegalStateException("No apps/api above " + Path.of("").toAbsolutePath());
    }
}
