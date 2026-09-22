package app.lightmove.api.core.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.ratelimit.service.WorkspaceDailySpend;
import app.lightmove.api.core.ratelimit.service.WorkspaceSpendMeter;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The one ceiling in this application that holds across instances, because it lives in a row rather
 * than in a heap. What matters is that the claim is atomic: two requests arriving together must not
 * both pass the last slot.
 */
@IntegrationTest
class WorkspaceDailySpendIntegrationTest extends FlowTestSupport {

    private static final WorkspaceSpendMeter METER = WorkspaceSpendMeter.COMPANY_DISCOVERY;

    private static final AtomicInteger OWNERS = new AtomicInteger();

    @Autowired WorkspaceDailySpend spend;
    @Autowired JdbcTemplate db;

    @Test
    @DisplayName("the ceiling holds, and the count says how much of it is gone")
    void theCeilingHolds() throws Exception {
        UUID workspaceId = workspace("Ceiling Firm");

        assertThat(spend.spend(METER, workspaceId, 3)).contains(1);
        assertThat(spend.spend(METER, workspaceId, 3)).contains(2);
        assertThat(spend.spend(METER, workspaceId, 3)).contains(3);
        // No row back is the refusal, and nothing was counted for it.
        assertThat(spend.spend(METER, workspaceId, 3)).isEmpty();
        assertThat(spend.spentToday(METER, workspaceId)).isEqualTo(3);
    }

    @Test
    @DisplayName("two callers racing on the last slot do not both get it")
    void aRaceDoesNotOverspend() throws Exception {
        UUID workspaceId = workspace("Race Firm");
        assertThat(spend.spend(METER, workspaceId, 2)).contains(1);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Callable<Optional<Integer>>> both = List.of(
                    () -> spend.spend(METER, workspaceId, 2),
                    () -> spend.spend(METER, workspaceId, 2));
            long granted = pool.invokeAll(both).stream().filter(WorkspaceDailySpendIntegrationTest::won).count();
            assertThat(granted).isEqualTo(1);
        } finally {
            pool.shutdownNow();
        }
        assertThat(spend.spentToday(METER, workspaceId)).isEqualTo(2);
    }

    @Test
    @DisplayName("each meter and each workspace has its own day")
    void metersAndWorkspacesDoNotShareABudget() throws Exception {
        UUID one = workspace("Budget Firm One");
        UUID two = workspace("Budget Firm Two");

        assertThat(spend.spend(METER, one, 1)).contains(1);
        assertThat(spend.spend(METER, one, 1)).isEmpty();
        // A second firm is untouched by the first spending its day.
        assertThat(spend.spend(METER, two, 1)).contains(1);
    }

    @Test
    @DisplayName("a new UTC day starts the count again")
    void theDayResets() throws Exception {
        UUID workspaceId = workspace("Rollover Firm");
        assertThat(spend.spend(METER, workspaceId, 1)).contains(1);
        assertThat(spend.spend(METER, workspaceId, 1)).isEmpty();

        // Yesterday's row is a different key, so it neither blocks today nor is read as today's.
        db.update("UPDATE app_lm_workspace_daily_spend SET spend_date = spend_date - 1"
                + " WHERE workspace_id = ?", workspaceId);

        assertThat(spend.spentToday(METER, workspaceId)).isZero();
        assertThat(spend.spend(METER, workspaceId, 1)).contains(1);
    }

    @Test
    @DisplayName("a ceiling of zero is refused rather than admitting the day's first call")
    void aZeroCeilingIsRefused() throws Exception {
        UUID workspaceId = workspace("Zero Cap Firm");

        // The upsert's WHERE guards only the DO UPDATE branch, so zero would let the first call
        // insert. Turning a feature off is `enabled: false`, not a cap it cannot express.
        assertThatThrownBy(() -> spend.spend(METER, workspaceId, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("first call");
        assertThat(spend.spentToday(METER, workspaceId)).isZero();
    }

    private static boolean won(Future<Optional<Integer>> attempt) {
        try {
            return attempt.get().isPresent();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** A user each, because one user holds at most one active workspace. */
    private UUID workspace(String firmName) throws Exception {
        String owner = "owner%d@%s".formatted(OWNERS.incrementAndGet(), domain);
        return UUID.fromString(createWorkspace(verifiedUser("Alok Kumar", owner), firmName));
    }
}
