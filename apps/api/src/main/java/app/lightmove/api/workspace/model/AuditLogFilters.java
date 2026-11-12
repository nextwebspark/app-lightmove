package app.lightmove.api.workspace.model;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.AuditOutcome;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.workspace.constant.AuditLogGroup;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

/** The audit log's filter row; every part is optional, and {@code to} is exclusive. */
public record AuditLogFilters(UUID actor, AuditLogGroup group, AuditOutcome outcome, Instant from, Instant to) {

    public static AuditLogFilters read(UUID actor, String group, String outcome, Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "The start date must be before the end date",
                    "Audit log range " + from + " to " + to);
        }
        return new AuditLogFilters(actor, ApiValueEnum.parse(AuditLogGroup.class, group, null, "category"),
                outcomeOf(outcome), from, to);
    }

    private static AuditOutcome outcomeOf(String token) {
        if (token == null || token.isBlank()) {
            return null;
        }
        return switch (token.toUpperCase(Locale.ROOT)) {
            case "SUCCESS" -> AuditOutcome.SUCCESS;
            case "FAILURE" -> AuditOutcome.FAILURE;
            default -> throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "That is not a valid outcome",
                    "Unknown audit outcome: " + token);
        };
    }
}
