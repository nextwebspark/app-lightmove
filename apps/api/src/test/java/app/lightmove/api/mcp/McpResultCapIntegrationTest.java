package app.lightmove.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** The per-call result cap, made small enough to reach. Its own class: the suite runs with production's. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.mcp.max-result-chars=1400")
class McpResultCapIntegrationTest extends McpFlowSupport {

    private static final List<String> COMPANIES = List.of("Aramco", "SABIC", "Ma'aden", "ACWA Power", "Masdar",
            "Emirates Global Aluminium");

    @Test
    @DisplayName("a page past the cap is cut with a notice, and its cursor reads on from the first row left out")
    void cutPageReadsOn() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        for (String name : COMPANIES) {
            capture(admin, finance, name);
        }
        String token = connect(registerClient(), admin, "companies:read", "candidates:read").get("access_token")
                .asText();

        List<String> seen = new ArrayList<>();
        String cursor = "";
        JsonNode first = null;
        do {
            JsonNode page = resultOf(tool(token, "uncava_list_companies", "{\"positionId\":\"" + finance
                    + "\",\"limit\":6" + (cursor.isEmpty() ? "" : ",\"cursor\":\"" + cursor + "\"") + "}"));
            first = first == null ? page : first;
            page.get("companies").forEach(company -> seen.add(company.get("name").asText()));
            cursor = page.path("nextCursor").asText("");
        } while (!cursor.isEmpty());

        assertThat(first.get("companies").size()).isLessThan(COMPANIES.size());
        assertThat(first.get("notice").asText()).startsWith("Cut to " + first.get("companies").size() + " of 6 rows");
        assertThat(seen).as("nothing skipped, nothing twice").containsExactlyInAnyOrderElementsOf(COMPANIES);

        JsonNode universe = resultOf(tool(token, "uncava_get_universe", "{\"positionId\":\"" + finance + "\"}"));
        assertThat(universe.get("companies").size()).isLessThan(COMPANIES.size());
        assertThat(universe.get("totalCompanies").asInt()).isEqualTo(COMPANIES.size());
        assertThat(universe.get("notice").asText()).contains("uncava_list_companies");
    }
}
