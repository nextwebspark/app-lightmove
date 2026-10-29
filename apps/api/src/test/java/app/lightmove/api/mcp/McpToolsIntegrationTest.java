package app.lightmove.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.IntegrationTest;
import io.modelcontextprotocol.json.schema.jackson3.DefaultJsonSchemaValidator;
import io.modelcontextprotocol.json.schema.JsonSchemaValidator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/**
 * The parity tools over the MCP transport: what each reads, for whom, with which scopes, a page at a time. API keys
 * are held to five calls a minute in the test profile, so each key here is spent with that in mind.
 */
@IntegrationTest
class McpToolsIntegrationTest extends McpFlowSupport {

    @Autowired JdbcTemplate db;

    @Test
    @DisplayName("positions: an OAuth user reads those they are seated on, a workspace key every one, another workspace none")
    void positionsFollowTheSeat() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String retail = project(admin, "Head of Retail");
        String sara = seatedResearcher(admin, finance);
        String saraToken = connect(registerClient(), sara, "projects:read").get("access_token").asText();

        JsonNode saraPage = resultOf(tool(saraToken, "uncava_search_positions", "{}"));
        assertThat(titlesOf(saraPage.get("positions"))).containsExactly("Chief Financial Officer");
        assertThat(saraPage.get("totalCount").asLong()).isEqualTo(1);
        assertThat(saraPage.at("/positions/0/detail").isNull()).as("concise by default").isTrue();
        assertThat(refusalOf(tool(saraToken, "uncava_get_position", "{\"positionId\":\"" + retail + "\"}")))
                .isEqualTo("No position " + retail + " is readable through this connection. Find the positions it "
                        + "can read with uncava_search_positions.");
        JsonNode detailed = resultOf(tool(saraToken, "uncava_get_position",
                "{\"positionId\":\"" + finance + "\",\"response_format\":\"detailed\"}"));
        assertThat(detailed.at("/detail/title").asText()).isEqualTo("Chief Financial Officer");
        assertThat(detailed.at("/detail/clientName").asText()).startsWith("Client ");

        String serviceKey = serviceKeyOf(admin, "projects:read", "mcp:use");
        assertThat(titlesOf(resultOf(tool(serviceKey, "uncava_search_positions", "{}")).get("positions")))
                .containsExactlyInAnyOrder("Chief Financial Officer", "Head of Retail");
        assertThat(titlesOf(resultOf(tool(serviceKey, "uncava_search_positions", "{\"title\":\"retail\"}"))
                .get("positions"))).containsExactly("Head of Retail");

        String outsider = serviceKeyOf(adminOf("other-" + domain), "projects:read", "mcp:use");
        assertThat(refusalOf(tool(outsider, "uncava_get_position", "{\"positionId\":\"" + finance + "\"}")))
                .startsWith("No position " + finance + " is readable");
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event
                where event_type = 'MCP_TOOL_CALL' and metadata ->> 'tool' = 'uncava_get_position'
                  and target_type = 'project' and target_id = ?""", Integer.class, finance))
                .as("each read of a position is audited against it, refusals included").isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"uncava_list_companies", "uncava_list_candidates", "uncava_get_universe"})
    @DisplayName("a position tool refuses alike an unseated OAuth user, an unseated personal key and another workspace,"
            + " and audits each refusal against the position")
    void positionToolsFollowTheSeat(String tool) throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String sara = seatedResearcher(admin, project(admin, "Head of Retail"));
        String arguments = "{\"positionId\":\"" + finance + "\"}";
        String refusal = "No position " + finance + " is readable through this connection. Find the positions it "
                + "can read with uncava_search_positions.";

        String saraToken = connect(registerClient(), sara, "companies:read", "candidates:read").get("access_token")
                .asText();
        assertThat(refusalOf(tool(saraToken, tool, arguments))).as("unseated OAuth user").isEqualTo(refusal);
        String saraKey = keyOf(sara, "companies:read", "candidates:read", "mcp:use");
        assertThat(refusalOf(tool(saraKey, tool, arguments))).as("unseated personal key").isEqualTo(refusal);
        String outsider = serviceKeyOf(adminOf("other-" + domain), "companies:read", "candidates:read", "mcp:use");
        assertThat(refusalOf(tool(outsider, tool, arguments))).as("another workspace").isEqualTo(refusal);

        assertThat(resultOf(tool(serviceKeyOf(admin, "companies:read", "candidates:read", "mcp:use"), tool,
                arguments))).as("the position's own workspace reads it").isNotNull();
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event
                where event_type = 'MCP_TOOL_CALL' and metadata ->> 'tool' = ? and outcome = 'FAILURE'
                  and target_type = 'project' and target_id = ?""", Integer.class, tool, finance))
                .as("each refusal is audited against the position").isEqualTo(3);
    }

    @Test
    @DisplayName("a refused response_format is answered as a result, and audited like any other refusal")
    void badResponseFormatIsAudited() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String key = keyOf(admin, "projects:read", "mcp:use");

        assertThat(refusalOf(tool(key, "uncava_get_position",
                "{\"positionId\":\"" + finance + "\",\"response_format\":\"verbose\"}")))
                .isEqualTo("response_format is concise or detailed.");
        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event
                where event_type = 'MCP_TOOL_CALL' and metadata ->> 'tool' = 'uncava_get_position'
                  and outcome = 'FAILURE' and target_id = ?""", Integer.class, finance)).isEqualTo(1);
    }

    @Test
    @DisplayName("a missing scope: an OAuth token is stepped up with 403 insufficient_scope, a key is told in a result")
    void missingScope() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String token = connect(registerClient(), admin, "projects:read").get("access_token").asText();
        String metadata = identity.issuer() + METADATA + MCP;

        MvcResult companies = mvc.perform(call(token, toolCall("uncava_list_companies", finance))).andReturn();
        assertThat(companies.getResponse().getStatus()).isEqualTo(403);
        assertThat(companies.getResponse().getHeader("WWW-Authenticate")).isEqualTo(
                "Bearer error=\"insufficient_scope\", scope=\"companies:read\", resource_metadata=\"" + metadata + "\"");
        assertThat(body(companies).get("code").asText()).isEqualTo("MCP_SCOPE_INSUFFICIENT");
        MvcResult universe = mvc.perform(call(token, toolCall("uncava_get_universe", finance))).andReturn();
        assertThat(universe.getResponse().getHeader("WWW-Authenticate"))
                .contains("scope=\"candidates:read companies:read\"");
        assertThat(statusOf(call(token, toolCall("uncava_get_position", finance)))).isEqualTo(200);

        String key = keyOf(admin, "projects:read", "mcp:use");
        assertThat(refusalOf(tool(key, "uncava_list_candidates", "{\"positionId\":\"" + finance + "\"}")))
                .startsWith("This connection lacks candidates:read.");
        assertThat(refusalOf(tool(key, "uncava_get_universe", "{\"positionId\":\"" + finance + "\"}")))
                .startsWith("This connection lacks candidates:read and companies:read.");
    }

    @Test
    @DisplayName("companies and executives: by stage and status, contacts and compensation only with their scopes, never a note")
    void companiesAndExecutives() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String acwa = capture(admin, finance, "ACWA Power");
        String masdar = capture(admin, finance, "Masdar");
        shortlist(admin, finance, masdar);
        map(admin, finance, """
                {"triageCompanyId":"%s","fullName":"Layla Haddad","title":"CFO","status":"interested",
                 "emails":[{"value":"layla@acwa.example","kind":"work"}],
                 "compensation":{"currency":"AED","baseSalary":1200000},
                 "note":"Prefers a call after six"}""".formatted(acwa));
        map(admin, finance, """
                {"triageCompanyId":"%s","fullName":"Omar Saleh","title":"Finance Director"}""".formatted(masdar));

        String plain = keyOf(admin, "companies:read", "candidates:read", "mcp:use");
        JsonNode shortlisted = resultOf(tool(plain, "uncava_list_companies",
                "{\"positionId\":\"" + finance + "\",\"stage\":\"shortlisted\"}"));
        assertThat(shortlisted.at("/companies/0/name").asText()).isEqualTo("Masdar");
        assertThat(shortlisted.get("totalCount").asLong()).isEqualTo(1);
        JsonNode interested = resultOf(tool(plain, "uncava_list_candidates",
                "{\"positionId\":\"" + finance + "\",\"status\":\"interested\",\"response_format\":\"detailed\"}"));
        assertThat(interested.at("/candidates/0/fullName").asText()).isEqualTo("Layla Haddad");
        assertThat(interested.at("/candidates/0/companyId").asText()).isEqualTo(acwa);
        assertThat(interested.at("/candidates/0/detail/contacts").isMissingNode()
                || interested.at("/candidates/0/detail/contacts").isNull()).isTrue();
        assertThat(interested.toString()).doesNotContain("Prefers a call");
        JsonNode atMasdar = resultOf(tool(plain, "uncava_list_candidates",
                "{\"positionId\":\"" + finance + "\",\"companyId\":\"" + masdar + "\"}"));
        assertThat(atMasdar.at("/candidates/0/fullName").asText()).isEqualTo("Omar Saleh");
        assertThat(atMasdar.get("candidates")).hasSize(1);

        String full = keyOf(admin, "companies:read", "candidates:read", "candidates.contacts:read",
                "candidates.compensation:read", "mcp:use");
        JsonNode universe = resultOf(tool(full, "uncava_get_universe",
                "{\"positionId\":\"" + finance + "\",\"response_format\":\"detailed\"}"));
        assertThat(universe.get("stage").asText()).isEqualTo("inUniverse");
        JsonNode layla = universe.get("companies").valueStream()
                .filter(company -> company.at("/company/name").asText().equals("ACWA Power"))
                .findFirst().orElseThrow().at("/executives/0");
        assertThat(layla.at("/detail/contacts/emails/0/address").asText()).isEqualTo("layla@acwa.example");
        assertThat(layla.at("/detail/compensation/baseSalary").asLong()).isEqualTo(1_200_000L);
    }

    @Test
    @DisplayName("a cursor reads on from where the last page stopped; a cursor or limit the server would not issue is refused")
    void cursorPaging() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        for (String name : List.of("Aramco", "SABIC", "Ma'aden")) {
            capture(admin, finance, name);
        }
        String key = keyOf(admin, "companies:read", "mcp:use");

        JsonNode first = resultOf(tool(key, "uncava_list_companies",
                "{\"positionId\":\"" + finance + "\",\"limit\":2}"));
        assertThat(first.get("companies")).hasSize(2);
        assertThat(first.get("totalCount").asLong()).isEqualTo(3);
        JsonNode second = resultOf(tool(key, "uncava_list_companies", "{\"positionId\":\"" + finance
                + "\",\"limit\":2,\"cursor\":\"" + first.get("nextCursor").asText() + "\"}"));
        assertThat(second.get("companies")).hasSize(1);
        assertThat(second.has("nextCursor") && !second.get("nextCursor").isNull()).isFalse();
        Set<String> seen = new HashSet<>(namesOf(first.get("companies")));
        seen.addAll(namesOf(second.get("companies")));
        assertThat(seen).containsExactlyInAnyOrder("Aramco", "SABIC", "Ma'aden");

        assertThat(refusalOf(tool(key, "uncava_list_companies",
                "{\"positionId\":\"" + finance + "\",\"cursor\":\"bm90LW91cnM\"}")))
                .startsWith("That cursor is not one this server issued.");
        assertThat(refusalOf(tool(key, "uncava_list_companies",
                "{\"positionId\":\"" + finance + "\",\"limit\":101}"))).isEqualTo("limit is between 1 and 100.");
    }

    @Test
    @DisplayName("every tool's structured answer validates against the output schema tools/list publishes")
    void structuredContentMatchesTheSchema() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String acwa = capture(admin, finance, "ACWA Power");
        map(admin, finance, """
                {"triageCompanyId":"%s","fullName":"Layla Haddad"}""".formatted(acwa));
        map(admin, finance, """
                {"fullName":"Unplaced Person"}""");
        String token = connect(registerClient(), admin, "projects:read", "companies:read", "candidates:read",
                "candidates.contacts:read", "candidates.compensation:read").get("access_token").asText();
        Map<String, JsonNode> schemas = new java.util.HashMap<>();
        rpc(token, LIST_TOOLS).at("/result/tools").forEach(listed ->
                schemas.put(listed.get("name").asText(), listed.get("outputSchema")));
        JsonSchemaValidator validator = new DefaultJsonSchemaValidator();

        for (String format : List.of("concise", "detailed")) {
            String position = "{\"positionId\":\"" + finance + "\",\"response_format\":\"" + format + "\"}";
            Map<String, String> calls = Map.of(
                    "uncava_whoami", "{}",
                    "uncava_search_positions", "{\"response_format\":\"" + format + "\"}",
                    "uncava_get_position", position,
                    "uncava_list_companies", position,
                    "uncava_list_candidates", position,
                    "uncava_get_universe", position);
            for (Map.Entry<String, String> toolCall : calls.entrySet()) {
                JsonNode response = tool(token, toolCall.getKey(), toolCall.getValue());
                JsonNode structured = response.at("/result/structuredContent");
                assertThat(structured.isObject()).as(toolCall.getKey() + " " + response).isTrue();
                assertThat(response.at("/result/content/0/text").asText()).as("a text copy for older clients")
                        .isNotBlank();
                JsonSchemaValidator.ValidationResponse validation = validator.validate(
                        asMap(schemas.get(toolCall.getKey())), asMap(structured));
                assertThat(validation.valid()).as(toolCall.getKey() + " " + format + ": " + validation.errorMessage())
                        .isTrue();
            }
        }
    }

    private static String toolCall(String name, String positionId) {
        return """
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"%s","arguments":{"positionId":"%s"}}}"""
                .formatted(name, positionId);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> asMap(JsonNode node) {
        return json.convertValue(node, Map.class);
    }

    private static List<String> titlesOf(JsonNode positions) {
        return positions.valueStream().map(position -> position.get("title").asText()).toList();
    }

    private static List<String> namesOf(JsonNode companies) {
        return companies.valueStream().map(company -> company.get("name").asText()).toList();
    }
}
