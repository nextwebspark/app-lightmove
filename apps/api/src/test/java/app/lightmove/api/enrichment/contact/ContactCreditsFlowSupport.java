package app.lightmove.api.enrichment.contact;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.RecordingContactFinder;
import app.lightmove.api.billing.BillingFlowSupport;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

/** A workspace with one mandate, whose executives' contacts are bought with its credits. */
abstract class ContactCreditsFlowSupport extends BillingFlowSupport {

    @Autowired protected RecordingContactFinder finder;

    protected UUID workspaceId;
    protected UUID userId;
    protected String projectId;
    protected String adminToken;

    @BeforeEach
    void openAMandate() throws Exception {
        finder.clear();
        String admin = "buyer@" + domain;
        workspaceId = UUID.fromString(createWorkspace(verifiedUser("Yara Haddad", admin), "Credit Buying Firm"));
        adminToken = login(admin);
        userId = db.queryForObject("SELECT id FROM app_lm_user WHERE email = ?", UUID.class, admin);
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Credit Client"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Group CFO"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    protected String executive(String slug) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Sample Person","linkedinUrl":"https://www.linkedin.com/in/%s"}
                                """.formatted(slug)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    protected ResultActions press(String candidateId, String channel) throws Exception {
        return mvc.perform(post("/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/contact/" + channel)
                .header("Authorization", "Bearer " + adminToken));
    }

    protected long holdsOf(String status) {
        return db.queryForObject("SELECT count(*) FROM app_lm_credit_hold WHERE workspace_id = ? AND status = ?",
                Long.class, workspaceId, status);
    }
}
