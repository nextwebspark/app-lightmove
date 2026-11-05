package app.lightmove.api.triagecompany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.lightmove.api.FixtureJdbc;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.TestWorkspaceScopes;
import app.lightmove.api.triagecompany.dto.TriageCompanyResponse;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.service.TriageCompanyService;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Two research results naming one employer the market does not carry, landing together: the second waits for the
 * first and is answered with its row, rather than both inserting and the loser's whole research rolling back.
 */
@IntegrationTest
class CompanyCaptureRaceIntegrationTest extends FlowTestSupport {

    @Autowired @FixtureJdbc JdbcTemplate db;
    @Autowired TriageCompanyService triage;
    @Autowired TransactionTemplate transactions;

    @Test
    @DisplayName("a capture racing another of the same name waits for it and answers with its row")
    void twoCapturesOfOneNameShareOneRow() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Capture Race Firm");
        String token = login(alok);
        UUID projectId = UUID.fromString(projectOf(token));
        UUID userId = db.queryForObject("select id from app_lm_user where email = ?", UUID.class, alok);
        CapturedCompanyDetails employer = CapturedCompanyDetails.employerNamed("Outside Holdings", null, null);

        CountDownLatch firstFiled = new CountDownLatch(1);
        CountDownLatch firstMayCommit = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Future<UUID> first = pool.submit(() -> TestWorkspaceScopes.inProjectsWorkspace(db, projectId,
                    () -> transactions.execute(status -> {
                        UUID id = triage.captureFromResearch(projectId, userId, employer).id();
                        firstFiled.countDown();
                        awaitQuietly(firstMayCommit);
                        return id;
                    })));
            assertThat(firstFiled.await(10, TimeUnit.SECONDS)).isTrue();
            Future<TriageCompanyResponse> second = pool.submit(() -> TestWorkspaceScopes.inProjectsWorkspace(db,
                    projectId, () -> triage.captureFromResearch(projectId, userId, employer)));
            Thread.sleep(300);
            assertThat(second.isDone()).isFalse();
            firstMayCommit.countDown();

            assertThat(second.get(10, TimeUnit.SECONDS).id()).isEqualTo(first.get(10, TimeUnit.SECONDS));
        } finally {
            firstMayCommit.countDown();
            pool.shutdown();
        }
        assertThat(db.queryForObject("""
                select count(*) from app_lm_project_triage_company
                where project_id = ? and lower(company_name) = 'outside holdings'""", Integer.class, projectId))
                .isEqualTo(1);
    }

    private String projectOf(String token) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Race Client"}"""))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}""".formatted(clientId)))
                .andReturn()).get("id").asText();
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
