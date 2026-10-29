package app.lightmove.api.mcp;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.core.security.oauth.OAuthFlowSupport;
import java.util.List;
import java.util.UUID;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;

/** Calls the MCP endpoint as a client does, and seeds the workspace its tools read. */
public abstract class McpFlowSupport extends OAuthFlowSupport {

    protected static final String MCP = "/api/v1/mcp";
    protected static final String METADATA = "/.well-known/oauth-protected-resource";
    protected static final String LIST_TOOLS = """
            {"jsonrpc":"2.0","id":2,"method":"tools/list"}""";

    protected MockHttpServletRequestBuilder call(String bearer, String jsonRpc) {
        MockHttpServletRequestBuilder request = post(MCP)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON, MediaType.TEXT_EVENT_STREAM)
                .content(jsonRpc);
        return bearer == null ? request : request.header("Authorization", "Bearer " + bearer);
    }

    protected int statusOf(MockHttpServletRequestBuilder request) throws Exception {
        return mvc.perform(request).andReturn().getResponse().getStatus();
    }

    protected JsonNode rpc(String bearer, String jsonRpc) throws Exception {
        MvcResult answered = mvc.perform(call(bearer, jsonRpc)).andReturn();
        assertThat(answered.getResponse().getStatus()).as(answered.getResponse().getContentAsString()).isEqualTo(200);
        return body(answered);
    }

    /** One {@code tools/call}, answering the whole JSON-RPC response. */
    protected JsonNode tool(String bearer, String name, String argumentsJson) throws Exception {
        return rpc(bearer, """
                {"jsonrpc":"2.0","id":7,"method":"tools/call","params":{"name":"%s","arguments":%s}}"""
                .formatted(name, argumentsJson));
    }

    protected JsonNode resultOf(JsonNode response) throws Exception {
        assertThat(response.at("/result/isError").asBoolean(false)).as(response.toString()).isFalse();
        JsonNode structured = response.at("/result/structuredContent");
        return structured.isMissingNode() || structured.isNull()
                ? json.readTree(response.at("/result/content/0/text").asText())
                : structured;
    }

    /** The sentence an {@code isError} result answers with. */
    protected static String refusalOf(JsonNode response) {
        assertThat(response.at("/result/isError").asBoolean(false)).as(response.toString()).isTrue();
        return response.at("/result/content/0/text").asText();
    }

    protected String adminOf(String emailDomain) throws Exception {
        String alok = "alok@" + emailDomain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "MCP Firm");
        return login(alok);
    }

    protected String keyOf(String admin, String... scopes) throws Exception {
        return keyOfKind(admin, "PERSONAL", scopes);
    }

    protected String serviceKeyOf(String admin, String... scopes) throws Exception {
        return keyOfKind(admin, "SERVICE", scopes);
    }

    private String keyOfKind(String admin, String kind, String... scopes) throws Exception {
        String request = """
                {"name":"MCP test","kind":"%s","scopes":[%s]}""".formatted(kind,
                String.join(",", List.of(scopes).stream().map(scope -> "\"" + scope + "\"").toList()));
        MvcResult created = mvc.perform(post("/api/v1/workspace/api-keys").header("Authorization", "Bearer " + admin)
                .contentType(MediaType.APPLICATION_JSON).content(request)).andReturn();
        assertThat(created.getResponse().getStatus()).as(created.getResponse().getContentAsString()).isEqualTo(201);
        return body(created).get("secret").asText();
    }

    protected String project(String admin, String title) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Client %s"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, title)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    protected String capture(String admin, String projectId, String companyName) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s"}""".formatted(companyName)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    protected void shortlist(String admin, String projectId, String companyId) throws Exception {
        mvc.perform(patch("/api/v1/projects/" + projectId + "/triage/" + companyId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"shortlisted"}"""))
                .andExpect(status().isOk());
    }

    protected void map(String admin, String projectId, String candidateJson) throws Exception {
        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidateJson))
                .andExpect(status().isCreated());
    }

    /** A member seated as a researcher on one position, signed in. */
    protected String seatedResearcher(String admin, String projectId) throws Exception {
        String sara = "sara@" + domain;
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, sara))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isOk());
        return login(sara);
    }
}
