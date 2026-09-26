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
 * The fluent front door to the security ledger:
 * {@code audit.event(LOGIN_FAILED).actor(id).reason("bad_password").from(request).record()}.
 *
 * <p>The actual write is delegated to {@link AuditEventWriter}, a separate bean — see the note there
 * for why that separation is load-bearing rather than cosmetic.
 */
@Service
@RequiredArgsConstructor(access = AccessLevel.PACKAGE)
public class AuditService {

    /** The target type of every event about one project — the key the project's activity feed reads by. */
    public static final String PROJECT_TARGET = "project";

    private final AuditEventWriter writer;
    private final ClientIpResolver clientIpResolver;

    public Builder event(AuditEventType type) {
        return new Builder(this, type);
    }

    /** An event a user caused on one project, from the request that caused it. */
    public Builder projectEvent(AuditEventType type, UUID userId, UUID workspaceId, UUID projectId,
                                HttpServletRequest request) {
        return event(type).actor(userId).workspace(workspaceId).target(PROJECT_TARGET, projectId).from(request);
    }

    /** Package-private: callers go through {@link #event}, which is the only supported entry point. */
    void record(AuditEvent event) {
        writer.write(event);
    }

    /**
     * Collects the request-scoped context (IP, user agent, correlation id) every event wants.
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
         * A detail that may legitimately be absent — a token count nobody measured, a model name the
         * provider did not name.
         *
         * <p>Not the same as passing null to {@link #detail}: {@code record()} seals the map with
         * {@code Map.copyOf}, which rejects null values, so an absent detail passed there throws a
         * {@code NullPointerException} from inside the caller's own request — long after the work it
         * was recording succeeded.
         */
        public Builder detailIfPresent(String key, Object value) {
            if (value != null) {
                this.metadata.put(key, value);
            }
            return this;
        }

        /**
         * The origin of a request that has already ended.
         *
         * <p>For a background worker, which has no {@code HttpServletRequest} — Tomcat recycles it
         * as soon as the response is written. The values come from columns the accepting request
         * stored, where {@link ClientIpResolver} had already decided what was trustworthy; they must
         * never be taken from a header on the worker's side, because there is no request to have a
         * header and anything supplied there would be the caller's own choice.
         */
        public Builder origin(String ipAddress, String userAgent) {
            this.ipAddress = ipAddress;
            this.userAgent = truncate(userAgent, 512);
            return this;
        }

        /**
         * The IP is resolved by {@link ClientIpResolver}, not read off {@code X-Forwarded-For} here.
         * An audit log an attacker can write the "from" address of is worse than none — it is evidence
         * that points wherever they chose.
         */
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
