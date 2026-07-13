package app.lightmove.api.common.audit;

import app.lightmove.api.common.logging.CorrelationId;
import app.lightmove.api.common.security.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;

/**
 * The fluent front door to the security ledger:
 * {@code audit.event(LOGIN_FAILED).actor(id).reason("bad_password").from(request).record()}.
 *
 * <p>The actual write is delegated to {@link AuditEventWriter}, a separate bean — see the note there
 * for why that separation is load-bearing rather than cosmetic.
 */
@Service
public class AuditService {

    private final AuditEventWriter writer;
    private final ClientIpResolver clientIps;

    AuditService(AuditEventWriter writer, ClientIpResolver clientIps) {
        this.writer = writer;
        this.clientIps = clientIps;
    }

    public Builder event(AuditEventType type) {
        return new Builder(this, type);
    }

    /** Package-private: callers go through {@link #event}, which is the only supported entry point. */
    void record(AuditEvent event) {
        writer.write(event);
    }

    /**
     * Collects the request-scoped context (IP, user agent, correlation id) that every event wants and
     * no caller should have to remember to attach.
     */
    public static final class Builder {

        private final AuditService service;
        private final AuditEventType type;
        private final Map<String, Object> metadata = new HashMap<>();

        private AuditOutcome outcome = AuditOutcome.SUCCESS;
        private UUID actorUserId;
        private UUID workspaceId;
        private String targetType;
        private String targetId;
        private String ipAddress;
        private String userAgent;

        private Builder(AuditService service, AuditEventType type) {
            this.service = service;
            this.type = type;
        }

        public Builder actor(UUID userId) {
            this.actorUserId = userId;
            return this;
        }

        public Builder workspace(UUID id) {
            this.workspaceId = id;
            return this;
        }

        public Builder target(String type, Object id) {
            this.targetType = type;
            this.targetId = id == null ? null : id.toString();
            return this;
        }

        public Builder failed() {
            this.outcome = AuditOutcome.FAILURE;
            return this;
        }

        /**
         * Why something failed, in the ledger only. This is the detail the client is deliberately
         * denied — "no_such_user" versus "bad_password" is an enumeration oracle to an attacker and
         * the first thing an investigator needs.
         */
        public Builder reason(String reason) {
            this.metadata.put("reason", reason);
            return this;
        }

        public Builder detail(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /**
         * The IP is resolved by {@link ClientIpResolver}, not read off {@code X-Forwarded-For} here.
         * An audit log an attacker can write the "from" address of is worse than none — it is evidence
         * that points wherever they chose.
         */
        public Builder from(HttpServletRequest request) {
            if (request != null) {
                this.ipAddress = service.clientIps.resolve(request);
                this.userAgent = truncate(request.getHeader("User-Agent"), 512);
            }
            return this;
        }

        public void record() {
            service.record(new AuditEvent(type, outcome, actorUserId, workspaceId, targetType, targetId,
                    ipAddress, userAgent, CorrelationId.current(), Map.copyOf(metadata)));
        }

        private static String truncate(String value, int max) {
            if (value == null) {
                return null;
            }
            return value.length() <= max ? value : value.substring(0, max);
        }
    }
}
