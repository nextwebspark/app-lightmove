package app.lightmove.api;

import java.util.concurrent.Executor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.scheduling.annotation.AsyncConfigurer;

/**
 * Runs {@code @Async} work on the calling thread, so a test can assert on an audit row the request it
 * just made was supposed to write.
 *
 * <p>{@link app.lightmove.api.core.audit.service.AuditEventWriter} is deliberately {@code @Async} —
 * auditing must never add latency to the request it observes. That leaves any test asserting on
 * {@code app_lm_audit_event} racing a write on another thread: it reads zero rows, and the failure
 * looks exactly like the event never firing. {@code replayOfARotatedTokenIsStillTheft} lost that race
 * on CI and passed on the same commit an hour earlier.
 *
 * <p>Polling until the row appears would fix only the positive assertion. Its counterpart,
 * {@code replayAfterLogoutIsNotReportedAsTheft}, asserts that <i>no</i> alert fired — and no amount of
 * waiting proves a pending write will not land. Running inline is what makes both sides real.
 *
 * <p>{@code REQUIRES_NEW} still suspends the caller's transaction and commits on its own, so what that
 * annotation is there for — an audit row surviving the rollback of the thing it recorded — is
 * unchanged. Only the thread hop is gone, and nothing asserts on the thread hop.
 */
@TestConfiguration(proxyBeanMethods = false)
public class SynchronousAuditWrites implements AsyncConfigurer {

    @Override
    public Executor getAsyncExecutor() {
        return new SyncTaskExecutor();
    }
}
