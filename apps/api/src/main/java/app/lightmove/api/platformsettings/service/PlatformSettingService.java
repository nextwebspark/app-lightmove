package app.lightmove.api.platformsettings.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.ApplicationVariable;
import app.lightmove.api.core.config.ApplicationVariables;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.reload.PlatformConfigReload;
import app.lightmove.api.platformsettings.dto.ChangePlatformSettingRequest;
import app.lightmove.api.platformsettings.dto.PlatformSettingChangeResponse;
import app.lightmove.api.platformsettings.dto.PlatformSettingResponse;
import app.lightmove.api.platformsettings.dto.ResetPlatformSettingRequest;
import app.lightmove.api.platformsettings.model.PlatformSettingChange;
import app.lightmove.api.platformsettings.repository.PlatformSettingRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The application variables a super admin reads, changes and resets. A change, its record and the reload are one
 * transaction, so no instance reloads before the value is committed. A change to the default itself removes the
 * override, as a reset does: no row always means the yml value. History records what served, so clearing a stored
 * value that could not serve writes no history line; its audit event names what was discarded.
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingService {

    private final ApplicationVariables variables;
    private final PlatformSettingRepository settings;
    private final LightMoveProperties properties;
    private final PlatformConfigReload reload;
    private final TransactionTemplate transactions;
    private final AuditService audit;

    public List<PlatformSettingResponse> list() {
        Map<ApplicationVariable, Integer> overrides = variables.storedOverrides();
        Map<String, PlatformSettingChange> latest = settings.latestChangeByKey();
        return Arrays.stream(ApplicationVariable.values())
                .map(variable -> responseOf(variable, overrides.get(variable), latest.get(variable.getKey())))
                .toList();
    }

    public List<PlatformSettingChangeResponse> history(String key) {
        return settings.newestFirstChangesOf(require(key).getKey()).stream()
                .map(PlatformSettingChangeResponse::of)
                .toList();
    }

    public PlatformSettingResponse change(UUID userId, String key, ChangePlatformSettingRequest request,
                                          HttpServletRequest httpRequest) {
        ApplicationVariable variable = require(key);
        int value = variable.parseInRange(request.value()).orElseThrow(() -> outOfRange(variable));
        String note = request.note().strip();
        int configured = variable.configuredDefault(properties);

        Served before = transactions.execute(status -> {
            settings.lock(variable.getKey());
            Served current = servedBy(variable, configured);
            // An unusable row is cleared by naming the default: refused, it could only ever be reset.
            if (current.value() == value && current.discarded() == null) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "value", "That is its value now.");
            }
            if (value == configured) {
                settings.removeOverride(variable.getKey());
            } else {
                settings.override(variable.getKey(), Integer.toString(value), userId);
            }
            recordWhatServedChanged(variable, current.value(), value, note, userId);
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
        int configured = variable.configuredDefault(properties);

        Served before = transactions.execute(status -> {
            settings.lock(variable.getKey());
            Served current = servedBy(variable, configured);
            if (!current.overridden()) {
                throw ApiException.of(ErrorCode.PLATFORM_SETTING_NOT_CHANGED);
            }
            settings.removeOverride(variable.getKey());
            recordWhatServedChanged(variable, current.value(), configured, note, userId);
            reload.bump(ApplicationVariables.SCOPE);
            return current;
        });
        record(PlatformEventType.PLATFORM_SETTING_RESET, userId, variable, before, configured, note, httpRequest);
        return get(variable);
    }

    /** A key off the allowlist is answered as any unknown route is: the list is not advertised by its refusals. */
    private static ApplicationVariable require(String key) {
        return ApplicationVariable.ofKey(key).orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private Served servedBy(ApplicationVariable variable, int configured) {
        Optional<String> stored = settings.overrideOf(variable.getKey());
        OptionalInt usable = stored.map(variable::parseInRange).orElse(OptionalInt.empty());
        return new Served(usable.orElse(configured), stored.isPresent(),
                stored.isPresent() && usable.isEmpty() ? stored.get() : null);
    }

    private void recordWhatServedChanged(ApplicationVariable variable, int from, int to, String note, UUID userId) {
        if (from != to) {
            settings.recordChange(variable.getKey(), Integer.toString(from), Integer.toString(to), note, userId);
        }
    }

    private PlatformSettingResponse get(ApplicationVariable variable) {
        return responseOf(variable, variables.storedOverrides().get(variable),
                settings.latestChangeByKey().get(variable.getKey()));
    }

    private PlatformSettingResponse responseOf(ApplicationVariable variable, Integer override,
                                               PlatformSettingChange lastChange) {
        int configured = variable.configuredDefault(properties);
        int value = override != null ? override : configured;
        return new PlatformSettingResponse(variable.getKey(), variable.getGroup().getLabel(),
                variable.getGroup().getKeyPrefix(), variable.getLabel(), variable.getDescription(), value, configured,
                variable.getMin(), variable.getMax(), variable.getUnit(), value != configured,
                lastChange == null ? null : lastChange.changedBy(),
                lastChange == null ? null : lastChange.changedAt());
    }

    private static ApiException outOfRange(ApplicationVariable variable) {
        return ApiException.withField(ErrorCode.VALIDATION_FAILED, "value",
                "Enter a whole number from " + variable.getMin() + " to " + variable.getMax() + ".");
    }

    private void record(PlatformEventType type, UUID userId, ApplicationVariable variable, Served before, int after,
                        String note, HttpServletRequest httpRequest) {
        AuditService.Builder event = audit.event(type)
                .actor(userId)
                .target("setting", variable.getKey())
                .detail("from", before.value())
                .detail("to", after)
                .detail("note", note)
                .from(httpRequest);
        if (before.discarded() != null) {
            event.detail("discarded", before.discarded());
        }
        event.record();
    }

    /** What a variable answered before a write: {@code discarded} is a stored value that could not serve, else null. */
    private record Served(int value, boolean overridden, String discarded) {
    }
}
