package app.lightmove.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

/** Wrong passwords sent at once against one account, as a guesser sends them. */
@IntegrationTest
class ConcurrentLoginIntegrationTest extends FlowTestSupport {

    private static final int ATTEMPTS = 4;

    @Autowired UserRepository users;

    @Test
    @DisplayName("wrong passwords sent at once are each refused as any wrong password is, and each counted")
    void concurrentWrongPasswordsAreEachRefusedAndCounted() throws Exception {
        String email = "racer@" + domain;
        verifiedUser("Racer", email);
        CountDownLatch start = new CountDownLatch(1);
        Callable<Integer> wrongPassword = () -> {
            start.await();
            return mvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(java.util.Map.of("email", email, "password", "wrong-1234"))))
                    .andReturn().getResponse().getStatus();
        };

        ExecutorService pool = Executors.newFixedThreadPool(ATTEMPTS);
        try {
            List<Future<Integer>> answers = new ArrayList<>();
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                answers.add(pool.submit(wrongPassword));
            }
            start.countDown();
            List<Integer> statuses = new ArrayList<>();
            for (Future<Integer> answer : answers) {
                statuses.add(answer.get());
            }
            assertThat(statuses).containsOnly(401);
        } finally {
            pool.shutdownNow();
        }
        assertThat(users.findByEmail(email).orElseThrow().getFailedLoginAttempts()).isEqualTo(ATTEMPTS);
    }
}
