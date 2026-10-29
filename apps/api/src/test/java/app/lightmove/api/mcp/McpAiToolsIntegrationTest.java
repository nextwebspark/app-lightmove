package app.lightmove.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import app.lightmove.api.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * The task-shaped reads, each tool beside its REST twin: the same data, the same refusals. API keys are held to five
 * calls a minute in the test profile, so a key here serves both surfaces a few times at most.
 */
@IntegrationTest
class McpAiToolsIntegrationTest extends McpFlowSupport {

    private static final String PROJECTS = "/api/v1/public/projects/";
    private static final String[] EVERY_DATA_SCOPE = {"projects:read", "companies:read", "candidates:read",
            "candidates.contacts:read", "candidates.compensation:read", "mcp:use"};

    @Autowired JdbcTemplate db;

    @Test
    @DisplayName("the summary counts companies by stage and executives by status, alike on both surfaces, each audited")
    void summary() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String acwa = capture(admin, finance, "ACWA Power");
        String masdar = capture(admin, finance, "Masdar");
        capture(admin, finance, "Aramco");
        shortlist(admin, finance, masdar);
        map(admin, finance, """
                {"triageCompanyId":"%s","fullName":"Layla Haddad","status":"interested"}""".formatted(acwa));
        map(admin, finance, """
                {"triageCompanyId":"%s","fullName":"Omar Saleh"}""".formatted(masdar));
        map(admin, finance, """
                {"fullName":"Unplaced Person"}""");
        String key = serviceKeyOf(admin, "projects:read", "mcp:use");

        JsonNode rest = rest(key, PROJECTS + finance + "/summary", 200);
        JsonNode tool = resultOf(tool(key, "uncava_get_position_summary", "{\"positionId\":\"" + finance + "\"}"));
        assertThat(withoutNulls(tool)).isEqualTo(withoutNulls(rest));
        assertThat(rest.at("/position/title").asText()).isEqualTo("Chief Financial Officer");
        assertThat(rest.at("/companies/inUniverse").asLong()).isEqualTo(2);
        assertThat(rest.at("/companies/shortlisted").asLong()).isEqualTo(1);
        assertThat(rest.at("/executives/identified").asLong()).isEqualTo(2);
        assertThat(rest.at("/executives/interested").asLong()).isEqualTo(1);
        assertThat(rest.at("/executives/offLimits").asLong()).as("every status, zero included").isZero();
        assertThat(rest.at("/mappedCompanies").asLong()).isEqualTo(2);
        assertThat(rest.at("/timeline").has("deliveryDate")).isTrue();

        assertThat(db.queryForObject("""
                select count(*) from app_lm_audit_event where target_id = ?
                  and (event_type = 'PUBLIC_API_READ' or (event_type = 'MCP_TOOL_CALL'
                       and metadata ->> 'tool' = 'uncava_get_position_summary'))""", Integer.class, finance))
                .as("one audit line per surface").isEqualTo(2);
    }

    @Test
    @DisplayName("one executive reads alike on both surfaces, contacts only with their scope; another position's is not found")
    void candidate() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String acwa = capture(admin, finance, "ACWA Power");
        map(admin, finance, """
                {"triageCompanyId":"%s","fullName":"Layla Haddad","title":"CFO",
                 "emails":[{"value":"layla@acwa.example","kind":"work"}]}""".formatted(acwa));
        String retail = project(admin, "Head of Retail");
        map(admin, retail, """
                {"fullName":"Elsewhere Person"}""");
        String layla = candidateIdOf(finance, "Layla Haddad");
        String elsewhere = candidateIdOf(retail, "Elsewhere Person");

        String full = serviceKeyOf(admin, EVERY_DATA_SCOPE);
        JsonNode rest = rest(full, PROJECTS + finance + "/candidates/" + layla, 200);
        JsonNode tool = resultOf(tool(full, "uncava_get_candidate", "{\"positionId\":\"" + finance
                + "\",\"candidateId\":\"" + layla + "\",\"response_format\":\"detailed\"}"));
        assertThat(withoutNulls(tool.get("detail"))).isEqualTo(withoutNulls(rest));
        assertThat(rest.at("/contacts/emails/0/address").asText()).isEqualTo("layla@acwa.example");

        assertThat(rest(full, PROJECTS + finance + "/candidates/" + elsewhere, 404).get("code").asText())
                .isEqualTo("NOT_FOUND");
        assertThat(refusalOf(tool(full, "uncava_get_candidate", "{\"positionId\":\"" + finance
                + "\",\"candidateId\":\"" + elsewhere + "\"}")))
                .isEqualTo("No executive " + elsewhere + " is on this position. Find them with "
                        + "uncava_search_candidates or uncava_list_candidates.");

        String plain = serviceKeyOf(admin, "candidates:read", "mcp:use");
        assertThat(rest(plain, PROJECTS + finance + "/candidates/" + layla, 200).get("contacts").isNull()).isTrue();
        JsonNode plainTool = resultOf(tool(plain, "uncava_get_candidate", "{\"positionId\":\"" + finance
                + "\",\"candidateId\":\"" + layla + "\",\"response_format\":\"detailed\"}"));
        assertThat(plainTool.at("/detail/contacts").isNull() || plainTool.at("/detail/contacts").isMissingNode())
                .isTrue();
    }

    @Test
    @DisplayName("one company reads with its executives alike on both surfaces; without candidates:read they are left out")
    void company() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String acwa = capture(admin, finance, "ACWA Power");
        capture(admin, finance, "Masdar");
        for (String name : List.of("Layla Haddad", "Omar Saleh", "Rania Aziz")) {
            map(admin, finance, """
                    {"triageCompanyId":"%s","fullName":"%s"}""".formatted(acwa, name));
        }
        String retail = project(admin, "Head of Retail");
        String elsewhere = capture(admin, retail, "Elsewhere Co");

        String key = serviceKeyOf(admin, "companies:read", "candidates:read", "mcp:use");
        JsonNode rest = rest(key, PROJECTS + finance + "/companies/" + acwa + "?size=2", 200);
        assertThat(rest.at("/company/name").asText()).isEqualTo("ACWA Power");
        assertThat(rest.at("/executives/data")).hasSize(2);
        assertThat(rest.at("/executives/totalCount").asLong()).isEqualTo(3);
        JsonNode tool = resultOf(tool(key, "uncava_get_company", "{\"positionId\":\"" + finance
                + "\",\"companyId\":\"" + acwa + "\",\"limit\":2,\"response_format\":\"detailed\"}"));
        assertThat(withoutNulls(tool.at("/company/detail"))).isEqualTo(withoutNulls(rest.get("company")));
        assertThat(names(tool.get("executives"))).isEqualTo(names(rest.at("/executives/data")));
        assertThat(tool.get("totalExecutives").asLong()).isEqualTo(3);
        assertThat(tool.get("nextCursor").asText()).isNotBlank();

        assertThat(rest(key, PROJECTS + finance + "/companies/" + elsewhere, 404).get("code").asText())
                .isEqualTo("NOT_FOUND");
        assertThat(refusalOf(tool(key, "uncava_get_company", "{\"positionId\":\"" + finance + "\",\"companyId\":\""
                + elsewhere + "\"}"))).startsWith("No company " + elsewhere + " is on this position.");

        String companiesOnly = serviceKeyOf(admin, "companies:read", "mcp:use");
        assertThat(rest(companiesOnly, PROJECTS + finance + "/companies/" + acwa, 200).get("executives").isNull())
                .isTrue();
        JsonNode bare = resultOf(tool(companiesOnly, "uncava_get_company", "{\"positionId\":\"" + finance
                + "\",\"companyId\":\"" + acwa + "\"}"));
        assertThat(bare.get("executives") == null || bare.get("executives").isNull()).isTrue();
        assertThat(bare.at("/company/name").asText()).isEqualTo("ACWA Power");
    }

    @Test
    @DisplayName("a search matches a name, a title or an employer, narrows by stage and status, alike on both surfaces")
    void search() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String acwa = capture(admin, finance, "ACWA Power");
        String masdar = capture(admin, finance, "Masdar");
        shortlist(admin, finance, masdar);
        map(admin, finance, """
                {"triageCompanyId":"%s","fullName":"Layla Haddad","title":"Group CFO"}""".formatted(acwa));
        map(admin, finance, """
                {"triageCompanyId":"%s","fullName":"Omar Saleh","title":"CFO","status":"contacted"}"""
                .formatted(masdar));
        map(admin, finance, """
                {"triageCompanyId":"%s","fullName":"Rania Cfoza","title":"Treasurer"}""".formatted(acwa));
        map(admin, finance, """
                {"fullName":"Sami Nasser","title":"VP Strategy"}""");

        String key = serviceKeyOf(admin, "candidates:read", "mcp:use");
        assertThat(names(rest(key, PROJECTS + finance + "/candidates?q=cfo", 200).get("data")))
                .as("a title or a name").containsExactlyInAnyOrder("Layla Haddad", "Omar Saleh", "Rania Cfoza");
        assertThat(names(resultOf(tool(key, "uncava_search_candidates", "{\"positionId\":\"" + finance
                + "\",\"query\":\"masdar\"}")).get("candidates"))).as("an employer").containsExactly("Omar Saleh");
        assertThat(names(resultOf(tool(key, "uncava_search_candidates", "{\"positionId\":\"" + finance
                + "\",\"query\":\"cfo\",\"stage\":\"shortlisted\"}")).get("candidates")))
                .as("held to the shortlist").containsExactly("Omar Saleh");
        assertThat(names(rest(key, PROJECTS + finance + "/candidates?q=cfo&stage=inUniverse&status=identified", 200)
                .get("data"))).containsExactlyInAnyOrder("Layla Haddad", "Rania Cfoza");
        assertThat(refusalOf(tool(key, "uncava_search_candidates", "{\"positionId\":\"" + finance
                + "\",\"query\":\" \"}"))).isEqualTo("query is the text to search for, and cannot be empty.");
    }

    @Test
    @DisplayName("an unseated personal key is refused alike on every tool and its twin; an OAuth token is stepped up")
    void refusals() throws Exception {
        String admin = adminOf(domain);
        String finance = project(admin, "Chief Financial Officer");
        String acwa = capture(admin, finance, "ACWA Power");
        String sara = seatedResearcher(admin, project(admin, "Head of Retail"));
        String refusal = "No position " + finance + " is readable through this connection. Find the positions it "
                + "can read with uncava_search_positions.";

        String saraKey = keyOf(sara, "projects:read", "companies:read", "candidates:read", "mcp:use");
        assertThat(refusalOf(tool(saraKey, "uncava_get_position_summary", "{\"positionId\":\"" + finance + "\"}")))
                .isEqualTo(refusal);
        assertThat(refusalOf(tool(saraKey, "uncava_get_company", "{\"positionId\":\"" + finance
                + "\",\"companyId\":\"" + acwa + "\"}"))).isEqualTo(refusal);
        assertThat(refusalOf(tool(saraKey, "uncava_search_candidates", "{\"positionId\":\"" + finance
                + "\",\"query\":\"cfo\"}"))).isEqualTo(refusal);
        assertThat(rest(saraKey, PROJECTS + finance + "/summary", 403).get("code").asText()).isEqualTo("FORBIDDEN");
        assertThat(rest(saraKey, PROJECTS + finance + "/companies/" + acwa, 403).get("code").asText())
                .isEqualTo("FORBIDDEN");

        String token = connect(registerClient(), admin, "projects:read").get("access_token").asText();
        MvcResult stepUp = mvc.perform(call(token, """
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"uncava_get_candidate",
                 "arguments":{"positionId":"%s","candidateId":"%s"}}}""".formatted(finance, acwa))).andReturn();
        assertThat(stepUp.getResponse().getStatus()).isEqualTo(403);
        assertThat(stepUp.getResponse().getHeader("WWW-Authenticate")).contains("scope=\"candidates:read\"");
    }

    private JsonNode rest(String key, String path, int status) throws Exception {
        MvcResult answered = mvc.perform(get(path).header("Authorization", "Bearer " + key)).andReturn();
        assertThat(answered.getResponse().getStatus()).as(answered.getResponse().getContentAsString())
                .isEqualTo(status);
        return body(answered);
    }

    private String candidateIdOf(String projectId, String fullName) {
        return db.queryForObject("""
                select c.id::text from app_lm_project_candidate c join app_lm_person p on p.id = c.person_id
                where c.project_id = ?::uuid and p.full_name = ?""", String.class, projectId, fullName);
    }

    /** The transport's mapper leaves nulls out where REST writes them; the fields that carry a value must agree. */
    private static JsonNode withoutNulls(JsonNode node) {
        if (node.isObject()) {
            ObjectNode copy = ((ObjectNode) node).objectNode();
            node.properties().forEach(entry -> {
                if (!entry.getValue().isNull()) {
                    copy.set(entry.getKey(), withoutNulls(entry.getValue()));
                }
            });
            return copy;
        }
        if (node.isArray()) {
            var copy = ((tools.jackson.databind.node.ArrayNode) node).arrayNode();
            node.forEach(element -> copy.add(withoutNulls(element)));
            return copy;
        }
        return node;
    }

    private static List<String> names(JsonNode rows) {
        return rows.valueStream().map(row -> row.get("fullName").asText()).toList();
    }
}
