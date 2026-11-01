package app.lightmove.api.workspace;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

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
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

/**
 * Signup's organisation step submitted at once founds one workspace. What holds it is the account row, locked before
 * the membership is read, so each later submit waits for the first and is refused by the check it then passes through.
 */
@IntegrationTest
class FirstWorkspaceRaceIntegrationTest extends FlowTestSupport {

    /** Under the per-account creation budget, so every refusal is the membership check's. */
    private static final int SUBMITS = 4;

    @Test
    @DisplayName("first-workspace submits racing found one workspace; the rest are refused")
    void racingSubmitsFoundOneWorkspace() throws Exception {
        String bearer = verifiedUser("Alok Kumar", "alok@" + domain);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> submit = () -> {
            start.await();
            return mvc.perform(post("/api/v1/onboarding/workspace")
                            .header("Authorization", "Bearer " + bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"mode":"COMPANY","name":"Double Click Firm"}
                                    """))
                    .andReturn().getResponse().getStatus();
        };

        ExecutorService pool = Executors.newFixedThreadPool(SUBMITS);
        try {
            List<Future<Integer>> answers = new ArrayList<>();
            for (int submitted = 0; submitted < SUBMITS; submitted++) {
                answers.add(pool.submit(submit));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> answer : answers) {
                statuses.add(answer.get());
            }
            assertThat(statuses).containsOnlyOnce(201).containsOnly(201, 409);
            mvc.perform(get("/api/v1/auth/me").header("Authorization", "Bearer " + login("alok@" + domain)))
                    .andExpect(jsonPath("$.workspaces.length()").value(1));
        } finally {
            pool.shutdownNow();
        }
    }
}
