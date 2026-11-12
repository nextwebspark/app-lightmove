package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/** Two admins stepping each other down at once, as a double-booked handover sends it. */
@IntegrationTest
class ConcurrentAdminChangeIntegrationTest extends FlowTestSupport {

    @RepeatedTest(3)
    @DisplayName("two admins demoting each other at once leave the workspace with one admin, never none")
    void concurrentDemotionsKeepAnAdmin() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Handover Firm");
        String alokToken = login(alok);
        inviteAndAccept(alokToken, "Sara Al-Mansour", sara, "ADMIN");
        String saraToken = login(sara);
        String alokMemberId = memberIdOf(alokToken, alok);
        String saraMemberId = memberIdOf(alokToken, sara);

        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Integer>> answers = new ArrayList<>();
            answers.add(pool.submit(demote(start, alokToken, saraMemberId)));
            answers.add(pool.submit(demote(start, saraToken, alokMemberId)));
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> answer : answers) {
                statuses.add(answer.get());
            }
            // The loser is refused either at the gate (no longer an admin) or by the last-admin rule.
            assertThat(statuses).containsOnlyOnce(200);
            assertThat(statuses).anyMatch(status -> status == 403 || status == 409);
        } finally {
            pool.shutdownNow();
        }

        // Both are still members, whoever kept the admin role, so either reads the roster.
        JsonNode roster = body(mvc.perform(get("/api/v1/members").header("Authorization", "Bearer " + alokToken))
                .andReturn());
        int admins = 0;
        for (JsonNode member : roster) {
            for (JsonNode role : member.get("roles")) {
                admins += role.asText().equals("ADMIN") ? 1 : 0;
            }
        }
        assertThat(admins).isEqualTo(1);
    }

    private Callable<Integer> demote(CountDownLatch start, String bearerToken, String memberId) {
        return () -> {
            start.await();
            return mvc.perform(patch("/api/v1/members/" + memberId)
                            .header("Authorization", "Bearer " + bearerToken)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"roles":["MEMBER"]}"""))
                    .andReturn().getResponse().getStatus();
        };
    }
}
