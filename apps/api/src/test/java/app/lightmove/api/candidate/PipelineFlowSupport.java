package app.lightmove.api.candidate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.core.tenant.WorkspaceScope;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;

/** A firm, its positions, its people at each seat, and the pipeline routes they call, for the pipeline's tests. */
public abstract class PipelineFlowSupport extends FlowTestSupport {

    protected static final String PIPELINE = "/api/v1/workspace/pipeline";

    @Autowired @FixtureJdbc protected JdbcTemplate db;

    protected String admin;
    protected String workspaceId;

    /** A call straight to the application's beans, as the firm: no request carries the workspace for it. */
    protected void inWorkspace(Runnable work) {
        WorkspaceScope.run(UUID.fromString(workspaceId), work);
    }

    protected <T> T inWorkspace(Supplier<T> work) {
        return WorkspaceScope.call(UUID.fromString(workspaceId), work);
    }

    protected void firm(String name) throws Exception {
        String address = "alok@" + domain;
        workspaceId = createWorkspace(verifiedUser("Alok Kumar", address), name);
        admin = login(address);
    }

    protected String mandate(String positionTitle) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s Unit"}""".formatted(positionTitle)))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, positionTitle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    protected String member(String name) throws Exception {
        String address = name + "@" + domain;
        inviteAndAccept(admin, name, address, "MEMBER");
        return login(address);
    }

    protected String researcherOn(String projectId, String name) throws Exception {
        String address = name + "@" + domain;
        inviteAndAccept(admin, name, address, "MEMBER");
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberIdOf(admin, address))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"RESEARCHER"}"""))
                .andExpect(status().isOk());
        return login(address);
    }

    protected String clientOn(String projectId) throws Exception {
        String clientEmail = "client@pipeline-client-" + domain;
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"A Client","position":"Chair","email":"%s"}
                                """.formatted(clientEmail)))
                .andExpect(status().isOk());
        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"A Client","password":"%s"}
                                """.formatted(email.latestTokenFor(clientEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }

    protected JsonNode add(String projectId, String json) throws Exception {
        return body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn());
    }

    protected MvcResult moveTo(String projectId, String candidateId, String statusKey) throws Exception {
        return mvc.perform(patch(candidatesUrl(projectId) + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"%s"}""".formatted(statusKey)))
                .andReturn();
    }

    protected String capture(String projectId, String companyName) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyName":"%s"}""".formatted(companyName)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    protected JsonNode catalog() throws Exception {
        return body(mvc.perform(get(PIPELINE).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    /**
     * Gives the firm a stage of ways out alone, last, as a workspace seeded before V163 keeps its old closed stage:
     * the frozen kind still CLOSED, every status out of process and none of them its default.
     */
    protected void formerClosedStage(String label, String... statusKeys) throws Exception {
        catalog();
        String stageId = db.queryForObject("""
                INSERT INTO app_lm_workspace_candidate_stage (workspace_id, key, label, colour, kind, position)
                SELECT ?::uuid, 'declined', ?, 'RED', 'CLOSED', max(position) + 1
                FROM app_lm_workspace_candidate_stage WHERE workspace_id = ?::uuid
                RETURNING id""", String.class, workspaceId, label, workspaceId);
        for (int index = 0; index < statusKeys.length; index++) {
            db.update("""
                    INSERT INTO app_lm_workspace_candidate_status
                        (workspace_id, stage_id, key, label, colour, position, is_default, out_of_process)
                    VALUES (?::uuid, ?::uuid, ?, ?, 'NEUTRAL', ?, false, true)""",
                    workspaceId, stageId, statusKeys[index], statusKeys[index], index + 1);
        }
    }

    protected MvcResult write(String token, String method, String url, String json) throws Exception {
        var request = switch (method) {
            case "POST" -> post(url);
            case "PATCH" -> patch(url);
            case "PUT" -> put(url);
            default -> throw new IllegalArgumentException(method);
        };
        return mvc.perform(request.header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andReturn();
    }

    protected static JsonNode stageIn(JsonNode catalog, String stageKey) {
        for (JsonNode stage : catalog.get("stages")) {
            if (stage.get("key").asText().equals(stageKey)) {
                return stage;
            }
        }
        throw new AssertionError("no stage " + stageKey + " in " + catalog);
    }

    protected static JsonNode statusIn(JsonNode catalog, String statusKey) {
        for (JsonNode stage : catalog.get("stages")) {
            for (JsonNode status : stage.get("statuses")) {
                if (status.get("key").asText().equals(statusKey)) {
                    return status;
                }
            }
        }
        throw new AssertionError("no status " + statusKey + " in " + catalog);
    }

    protected static String candidatesUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/candidates";
    }
}
