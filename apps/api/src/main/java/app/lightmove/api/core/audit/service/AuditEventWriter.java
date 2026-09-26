package app.lightmove.api.core.audit.service;
import app.lightmove.api.core.audit.repository.AuditEventRepository;
import app.lightmove.api.core.audit.model.AuditEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists audit events off the caller's thread and transaction. <b>Must stay a separate bean from
 * {@link AuditService}:</b> as a self-call the {@code @Async}/{@code @Transactional} proxies were
 * inert, and a failed insert marked the caller's transaction rollback-only, destroying a signup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class AuditEventWriter {

    private final AuditEventRepository repository;

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
            // Swallowed: losing an audit row must never fail the request it observed. ERROR should page.
            log.error("Failed to write audit event {}", event.getEventType(), ex);
        }
    }
}
