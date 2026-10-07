package app.lightmove.api.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

/** The ledger with enforcement on, as #741 will run it: a spend the credits cannot cover is refused. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.billing.enforce=true")
class CreditLedgerEnforcementIntegrationTest extends BillingFlowSupport {

    @Test
    @DisplayName("twenty concurrent holds against ten credits: exactly ten succeed and the balance never goes negative")
    void concurrentHoldsNeverOverdraw() throws Exception {
        UUID workspace = newWorkspace();
        grant(workspace, CreditGrantSource.MANUAL, 10, null);

        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Boolean>> attempts = new ArrayList<>();
        for (int attempt = 0; attempt < 20; attempt++) {
            String key = "concurrent-" + attempt;
            attempts.add(() -> {
                start.await();
                try {
                    ledger.hold(emailFound(workspace, key));
                    return true;
                } catch (ApiException refused) {
                    assertThat(refused.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_CREDITS);
                    return false;
                }
            });
        }
        ExecutorService pool = Executors.newFixedThreadPool(20);
        try {
            List<Future<Boolean>> outcomes = attempts.stream().map(pool::submit).toList();
            start.countDown();
            long succeeded = 0;
            for (Future<Boolean> outcome : outcomes) {
                succeeded += outcome.get() ? 1 : 0;
            }
            assertThat(succeeded).isEqualTo(10);
        } finally {
            pool.shutdownNow();
        }

        assertThat(ledger.balanceOf(workspace).available()).isZero();
        assertThat(ledger.balanceOf(workspace).held()).isEqualTo(10);
        assertLedgerAddsUp(workspace);
    }

    @Test
    @DisplayName("a refused spend names what it needed and what was left, and writes nothing")
    void refusalCarriesRequiredAndAvailable() throws Exception {
        UUID workspace = newWorkspace();
        grant(workspace, CreditGrantSource.MANUAL, 3, null);
        long written = entriesOf(workspace);

        assertThatThrownBy(() -> ledger.charge(phoneFound(workspace, "phone-refused")))
                .isInstanceOfSatisfying(ApiException.class, refused -> {
                    assertThat(refused.getCode()).isEqualTo(ErrorCode.INSUFFICIENT_CREDITS);
                    assertThat(refused.getProperties()).containsEntry("required", 5L).containsEntry("available", 3L);
                });

        assertThat(entriesOf(workspace)).isEqualTo(written);
        assertThat(ledger.balanceOf(workspace).available()).isEqualTo(3);
        assertLedgerAddsUp(workspace);
    }
}
