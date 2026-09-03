package app.lightmove.api.core.stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The live half of a project screen, end to end through Postgres: a publish rides its transaction's
 * commit through {@code NOTIFY}, the listener thread hands it to this instance's emitters, and the
 * bytes land on the open response. What must hold: a commit reaches the stream, a rollback never
 * does, events stay inside their mandate, and holding a stream takes the same WORK_VIEW as reading
 * the mandate.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class ProjectStreamIntegrationTest extends FlowTestSupport {

    private static final long STREAM_WAIT_MS = 10_000;

    @Autowired private ProjectStreamPublisher publisher;
    @Autowired private PlatformTransactionManager transactions;

    private String adminToken;

    @Test
    @DisplayName("a committed publish reaches the mandate's open stream")
    void aCommittedPublishReachesTheStream() throws Exception {
        String projectId = mandate("Streamed Firm");
        MvcResult stream = openStream(projectId, adminToken);
        awaitContent(stream, "event:connected");

        inTransaction(() -> publisher.publish(UUID.fromString(projectId),
                ProjectStreamKind.CANDIDATE_ENRICHED));

        awaitContent(stream, "candidate-enriched");
        assertThat(stream.getResponse().getContentAsString()).contains("event:change");
    }

    @Test
    @DisplayName("a rolled-back publish never reaches it, and a mandate only hears its own events")
    void aRollbackAndAnotherMandateStaySilent() throws Exception {
        String projectId = mandate("Rolled Back Firm");
        String otherProjectId = projectFor(adminToken, "Second Mandate Client");
        MvcResult stream = openStream(projectId, adminToken);
        MvcResult otherStream = openStream(otherProjectId, adminToken);

        // Rolled back: Postgres drops the notification with the transaction.
        TransactionTemplate transaction = new TransactionTemplate(transactions);
        transaction.executeWithoutResult(status -> {
            publisher.publish(UUID.fromString(projectId), ProjectStreamKind.COMPANY_ENRICHED);
            status.setRollbackOnly();
        });
        // Committed afterwards: notifications deliver in commit order, so once this lands the
        // rolled-back one had its chance.
        inTransaction(() -> publisher.publish(UUID.fromString(projectId),
                ProjectStreamKind.CANDIDATE_ENRICHED));

        awaitContent(stream, "candidate-enriched");
        assertThat(stream.getResponse().getContentAsString()).doesNotContain("company-enriched");
        assertThat(otherStream.getResponse().getContentAsString()).doesNotContain("event:change");
    }

    @Test
    @DisplayName("adding a candidate announces itself on the stream")
    void addingACandidateAnnouncesItself() throws Exception {
        String projectId = mandate("Announcing Firm");
        MvcResult stream = openStream(projectId, adminToken);

        mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Typed Person"}"""))
                .andExpect(status().isCreated());

        awaitContent(stream, "candidate-captured");
    }

    @Test
    @DisplayName("a publish outside a transaction is refused rather than delivered early")
    void aPublishOutsideATransactionIsRefused() {
        assertThatThrownBy(() -> publisher.publish(UUID.randomUUID(),
                ProjectStreamKind.CANDIDATE_ENRICHED))
                .isInstanceOf(IllegalTransactionStateException.class);
    }

    @Test
    @DisplayName("someone outside the workspace is not told the mandate exists")
    void anOutsiderCannotHoldTheStream() throws Exception {
        String projectId = mandate("Guarded Stream Firm");
        String outsider = "outsider@" + domain;
        createWorkspace(verifiedUser("Outsider", outsider), "Other Firm");

        // 404 rather than 403, and deliberately: ProjectAccess scopes the id to the caller's own
        // workspace before any seat is considered, so a foreign id is answered as one that does not
        // exist. Relaxing this to 403 would confirm the mandate to someone who may not know of it.
        mvc.perform(get(streamUrl(projectId))
                        .header("Authorization", "Bearer " + login(outsider)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a member of the firm who is not on the mandate's team cannot hold its stream")
    void aMemberOffTheTeamCannotHoldTheStream() throws Exception {
        String projectId = mandate("Seat Gated Stream Firm");
        String colleague = "colleague@" + domain;
        inviteAndAccept(adminToken, "Colleague", colleague, "MEMBER");

        // Inside the workspace, so the mandate is theirs to be refused: this is the WORK_VIEW seat
        // gate rather than tenant scoping, and the one that answers 403.
        mvc.perform(get(streamUrl(projectId))
                        .header("Authorization", "Bearer " + login(colleague)))
                .andExpect(status().isForbidden());
    }

    private MvcResult openStream(String projectId, String token) throws Exception {
        return mvc.perform(get(streamUrl(projectId)).header("Authorization", "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();
    }

    /** The listener hands events over from its own thread; the response fills in shortly after. */
    private void awaitContent(MvcResult stream, String expected) throws Exception {
        long deadline = System.currentTimeMillis() + STREAM_WAIT_MS;
        while (System.currentTimeMillis() < deadline) {
            if (stream.getResponse().getContentAsString().contains(expected)) {
                return;
            }
            Thread.sleep(100);
        }
        assertThat(stream.getResponse().getContentAsString()).contains(expected);
    }

    private void inTransaction(Runnable work) {
        new TransactionTemplate(transactions).executeWithoutResult(status -> work.run());
    }

    private static String streamUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/stream";
    }

    private String mandate(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        adminToken = login(alok);
        return projectFor(adminToken, "Stream Client");
    }

    private String projectFor(String token, String clientName) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s"}""".formatted(clientName)))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();
    }
}
