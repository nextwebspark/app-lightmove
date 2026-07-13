package app.lightmove.api.common.audit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists audit events, off the caller's thread and outside the caller's transaction.
 *
 * <p><b>This must be a separate bean from {@link AuditService}, and that is not organisational
 * tidiness.</b> {@code @Async} and {@code @Transactional} are implemented with proxies, and a proxy
 * only intercepts calls that arrive from <i>outside</i> the bean. When these annotations lived on
 * {@code AuditService} itself, its own builder called {@code this.record(...)} internally — the call
 * never left the object, never crossed the proxy, and both annotations were silently inert. The audit
 * insert then ran inside the caller's transaction, and the first failed insert marked that transaction
 * rollback-only, destroying the signup it was supposed to be quietly recording.
 *
 * <p>Crossing a bean boundary is what makes the annotations real.
 */
@Component
class AuditEventWriter {

    private static final Logger log = LoggerFactory.getLogger(AuditEventWriter.class);

    private final AuditEventRepository repository;

    AuditEventWriter(AuditEventRepository repository) {
        this.repository = repository;
    }

    /**
     * {@code REQUIRES_NEW} because the events that matter most are attached to failures. A rejected
     * signup rolls its transaction back — and if the audit row shared that transaction, the record of
     * the rejection would roll back with it.
     *
     * <p>{@code @Async} because auditing must never add latency to, or fail, the request it observes.
     */
    @Async
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void write(AuditEvent event) {
        try {
            repository.save(event);
        } catch (RuntimeException ex) {
            // Swallowed deliberately: by now the caller has already returned to the user. Losing an
            // audit row is bad and this ERROR should alert someone; failing a user's login because the
            // ledger hiccuped is worse.
            log.error("Failed to write audit event {}", event.getEventType(), ex);
        }
    }
}
