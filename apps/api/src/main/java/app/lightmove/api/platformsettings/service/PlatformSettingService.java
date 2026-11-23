package app.lightmove.api.platformsettings.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ApplicationVariable;
import app.lightmove.api.core.config.ApplicationVariableChange;
import app.lightmove.api.core.config.ApplicationVariableStore;
import app.lightmove.api.core.config.ApplicationVariables;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.reload.PlatformConfigReload;
import app.lightmove.api.platformsettings.dto.ChangePlatformSettingRequest;
import app.lightmove.api.platformsettings.dto.PlatformSettingChangeResponse;
import app.lightmove.api.platformsettings.dto.PlatformSettingResponse;
import app.lightmove.api.platformsettings.dto.ResetPlatformSettingRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The application variables a super admin reads, changes and resets. A change, its record and the reload are one
 * transaction, so no instance reloads before the value is committed. A change to the default itself removes the
 * override, as a reset does: no row always means the yml value.
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingService {

    private final ApplicationVariables variables;
    private final ApplicationVariableStore store;
    private final LightMoveProperties properties;
    private final PlatformConfigReload reload;
    private final TransactionTemplate transactions;
    private final AuditService audit;

    public List<PlatformSettingResponse> list() {
        Map<ApplicationVariable, Integer> overrides = variables.storedOverrides();
        Map<String, ApplicationVariableChange> latest = store.latestChanges();
        return Arrays.stream(ApplicationVariable.values())
                .map(variable -> responseOf(variable, overrides.get(variable), latest.get(variable.getKey())))
                .toList();
    }

    public List<PlatformSettingChangeResponse> history(String key) {
        return store.changesOf(require(key).getKey()).stream().map(PlatformSettingChangeResponse::of).toList();
    }

    public PlatformSettingResponse change(UUID userId, String key, ChangePlatformSettingRequest request,
                                          HttpServletRequest httpRequest) {
        ApplicationVariable variable = require(key);
        int value = variable.read(request.value()).orElseThrow(() -> outOfRange(variable));
        String note = request.note().strip();
        int shipped = variable.shippedDefault(properties);

        int before = transactions.execute(status -> {
            store.lock(variable.getKey());
            int current = currentOf(variable, shipped);
            if (current == value) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "value", "That is its value now.");
            }
            if (value == shipped) {
                store.removeOverride(variable.getKey());
            } else {
                store.override(variable.getKey(), Integer.toString(value), userId);
            }
            store.recordChange(variable.getKey(), Integer.toString(current), Integer.toString(value), note, userId);
            reload.bump(ApplicationVariables.SCOPE);
            return current;
        });
        record(PlatformEventType.PLATFORM_SETTING_CHANGED, userId, variable, before, value, note, httpRequest);
        return get(variable);
    }

    public PlatformSettingResponse reset(UUID userId, String key, ResetPlatformSettingRequest request,
                                         HttpServletRequest httpRequest) {
        ApplicationVariable variable = require(key);
        String note = request.note().strip();
        int shipped = variable.shippedDefault(properties);

        int before = transactions.execute(status -> {
            store.lock(variable.getKey());
            if (store.overrideOf(variable.getKey()).isEmpty()) {
                throw ApiException.of(ErrorCode.PLATFORM_SETTING_NOT_CHANGED);
            }
            int current = currentOf(variable, shipped);
            store.removeOverride(variable.getKey());
            // A stored value that could not serve was already answered by the default: nothing changed to record.
            if (current != shipped) {
                store.recordChange(variable.getKey(), Integer.toString(current), Integer.toString(shipped), note,
                        userId);
            }
            reload.bump(ApplicationVariables.SCOPE);
            return current;
        });
        record(PlatformEventType.PLATFORM_SETTING_RESET, userId, variable, before, shipped, note, httpRequest);
        return get(variable);
    }

    /** A key off the allowlist is answered as any unknown route is: the list is not advertised by its refusals. */
    private static ApplicationVariable require(String key) {
        return ApplicationVariable.ofKey(key)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No application variable " + key));
    }

    private int currentOf(ApplicationVariable variable, int shipped) {
        return store.overrideOf(variable.getKey())
                .map(variable::read)
                .filter(OptionalInt::isPresent)
                .map(OptionalInt::getAsInt)
                .orElse(shipped);
    }

    private PlatformSettingResponse get(ApplicationVariable variable) {
        return responseOf(variable, variables.storedOverrides().get(variable),
                store.latestChanges().get(variable.getKey()));
    }

    private PlatformSettingResponse responseOf(ApplicationVariable variable, Integer override,
                                               ApplicationVariableChange lastChange) {
        int shipped = variable.shippedDefault(properties);
        int value = override != null ? override : shipped;
        return new PlatformSettingResponse(variable.getKey(), variable.getGroup().getLabel(),
                variable.getGroup().getKeyPrefix(), variable.getLabel(), variable.getDescription(), value, shipped,
                variable.getMin(), variable.getMax(), variable.getUnit(), value != shipped,
                lastChange == null ? null : lastChange.changedBy(),
                lastChange == null ? null : lastChange.changedAt());
    }

    private static ApiException outOfRange(ApplicationVariable variable) {
        return ApiException.withField(ErrorCode.VALIDATION_FAILED, "value",
                "Enter a whole number from " + variable.getMin() + " to " + variable.getMax() + ".");
    }

    private void record(PlatformEventType type, UUID userId, ApplicationVariable variable, int before, int after,
                        String note, HttpServletRequest httpRequest) {
        audit.event(type)
                .actor(userId)
                .target("setting", variable.getKey())
                .detail("from", before)
                .detail("to", after)
                .detail("note", note)
                .from(httpRequest)
                .record();
    }
}
