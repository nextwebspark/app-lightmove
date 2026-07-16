package app.lightmove.api.core.audit.repository;
import app.lightmove.api.core.audit.model.AuditEvent;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Insert-and-read only. There is intentionally no update or delete method — and the database would
 * refuse one anyway.
 */
public interface AuditEventRepository extends JpaRepository<AuditEvent, Long> {
}
