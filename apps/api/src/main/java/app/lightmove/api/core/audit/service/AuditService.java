package app.lightmove.api.core.audit.service;
import app.lightmove.api.core.audit.constant.AuditEventType;
import app.lightmove.api.core.audit.constant.AuditOutcome;
import app.lightmove.api.core.audit.model.AuditEvent;
import app.lightmove.api.core.logging.service.CorrelationId;
import app.lightmove.api.core.security.service.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * The fluent front door to the security ledger. The write is delegated to {@link AuditEventWriter}, a
 * separate bean, and that separation is load-bearing (see there).
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AuditService {

    /** The target type the project's activity feed reads by. */
    public static final String PROJECT_TARGET = "project";

    private final AuditEventWriter writer;
    private final ClientIpResolver clientIpResolver;

    public Builder event(AuditEventType type) {
        return new Builder(this, type);
    }

    public Builder projectEvent(AuditEventType type, UUID userId, UUID workspaceId, UUID projectId,
                                HttpServletRequest request) {
        return event(type).actor(userId).workspace(workspaceId).target(PROJECT_TARGET, projectId).from(request);
    }

    void record(AuditEvent event) {
        writer.write(event);
    }

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

        public Builder workspace(UUID workspaceId) {
            this.workspaceId = workspaceId;
            return this;
        }

        public Builder target(String targetType, Object id) {
            this.targetType = targetType;
            this.targetId = id == null ? null : id.toString();
            return this;
        }

        public Builder failed() {
            this.outcome = AuditOutcome.FAILURE;
            return this;
        }

        /** In the ledger only: the client is deliberately denied it, being an enumeration oracle. */
        public Builder reason(String reason) {
            this.metadata.put("reason", reason);
            return this;
        }

        public Builder detail(String key, Object value) {
            this.metadata.put(key, value);
            return this;
        }

        /** For a nullable value: {@code record()} seals with {@code Map.copyOf}, which throws on a null {@link #detail}. */
        public Builder detailIfPresent(String key, Object value) {
            if (value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        /**
         * For a background worker, whose request has ended: the values must come from columns the
         * accepting request stored via {@link ClientIpResolver}, never from a header.
         */
        public Builder origin(String ipAddress, String userAgent) {
            this.ipAddress = ipAddress;
            this.userAgent = truncate(userAgent, 512);
            return this;
        }

        /** The IP comes from {@link ClientIpResolver}, never raw {@code X-Forwarded-For}: forged evidence is worse than none. */
        public Builder from(HttpServletRequest request) {
            if (request != null) {
                this.ipAddress = service.clientIpResolver.resolve(request);
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
