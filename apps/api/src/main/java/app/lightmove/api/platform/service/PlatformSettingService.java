package app.lightmove.api.platform.service;

import app.lightmove.api.core.audit.constant.PlatformEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.platformconfig.constant.PlatformSetting;
import app.lightmove.api.core.platformconfig.service.PlatformConfigChanges;
import app.lightmove.api.platform.dto.PlatformSettingResponse;
import app.lightmove.api.platform.dto.PlatformSettingsResponse;
import app.lightmove.api.platform.model.StoredPlatformSetting;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Settings → Platform → Variables. Every write is decided against the stored rows after
 * {@link PlatformConfigChanges#announce} has locked the version row, so two saves racing on the two sourcing keys
 * cannot together leave more people filed than asked.
 */
@Service
@RequiredArgsConstructor
public class PlatformSettingService {

    private final PlatformSettingStore store;
    private final PlatformConfigChanges changes;
    private final PlatformConfigService config;
    private final LightMoveProperties properties;
    private final AuditService audit;

    @Transactional(readOnly = true)
    public PlatformSettingsResponse list() {
        Map<String, StoredPlatformSetting> stored = store.all().stream()
                .collect(Collectors.toMap(StoredPlatformSetting::key, Function.identity()));
        return new PlatformSettingsResponse(
                Arrays.stream(PlatformSetting.values()).map(setting -> responseOf(setting, stored.get(setting.key())))
                        .toList(),
                config.version());
    }

    @Transactional
    public PlatformSettingResponse change(UUID userId, String key, String rawValue, HttpServletRequest request) {
        PlatformSetting setting = require(key);
        Object typed = setting.parse(rawValue);
        String value = setting.format(typed);
        long version = changes.announce();
        String before = valueInForce(setting);
        refuseFilingMoreThanAsked(setting, typed);
        store.write(key, value, userId);
        audit.event(PlatformEventType.PLATFORM_SETTING_CHANGED).actor(userId).target("platform_setting", key)
                .detail("from", before).detail("to", value).detail("version", version).from(request).record();
        return responseOf(setting, store.find(key).orElseThrow());
    }

    @Transactional
    public PlatformSettingResponse reset(UUID userId, String key, HttpServletRequest request) {
        PlatformSetting setting = require(key);
        if (store.find(key).isEmpty()) {
            return responseOf(setting, null);
        }
        long version = changes.announce();
        String before = valueInForce(setting);
        refuseFilingMoreThanAsked(setting, setting.defaultIn(properties));
        store.delete(key);
        audit.event(PlatformEventType.PLATFORM_SETTING_RESET).actor(userId).target("platform_setting", key)
                .detail("from", before).detail("to", setting.format(setting.defaultIn(properties)))
                .detail("version", version).from(request).record();
        return responseOf(setting, null);
    }

    /** {@code ExecutiveSourcingSettings} holds the boot values to the same rule; an override must not break it. */
    private void refuseFilingMoreThanAsked(PlatformSetting changed, Object typed) {
        long picks = changed == PlatformSetting.SOURCING_PICKS_PER_COMPANY
                ? (Long) typed : storedOrDefault(PlatformSetting.SOURCING_PICKS_PER_COMPANY);
        long hits = changed == PlatformSetting.SOURCING_HITS_PER_COMPANY
                ? (Long) typed : storedOrDefault(PlatformSetting.SOURCING_HITS_PER_COMPANY);
        if (picks <= hits) {
            return;
        }
        if (changed == PlatformSetting.SOURCING_PICKS_PER_COMPANY) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, changed.key(),
                    "Must not be more than the people asked per company");
        }
        if (changed == PlatformSetting.SOURCING_HITS_PER_COMPANY) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, changed.key(),
                    "Must not be fewer than the people filed per company");
        }
    }

    private long storedOrDefault(PlatformSetting setting) {
        Object value = store.find(setting.key()).map(row -> setting.parse(row.value()))
                .orElseGet(() -> setting.defaultIn(properties));
        return ((Number) value).longValue();
    }

    private String valueInForce(PlatformSetting setting) {
        return store.find(setting.key()).map(StoredPlatformSetting::value)
                .orElseGet(() -> setting.format(setting.defaultIn(properties)));
    }

    private PlatformSettingResponse responseOf(PlatformSetting setting, StoredPlatformSetting stored) {
        String defaultValue = setting.format(setting.defaultIn(properties));
        return new PlatformSettingResponse(setting.key(), setting.group(), setting.type(), setting.label(),
                setting.description(), setting.min(), setting.max(), defaultValue,
                stored == null ? defaultValue : stored.value(), stored != null,
                stored == null ? null : stored.updatedByName(), stored == null ? null : stored.updatedAt());
    }

    private static PlatformSetting require(String key) {
        return PlatformSetting.byKey(key)
                .orElseThrow(() -> new ApiException(ErrorCode.NOT_FOUND, "No allowlisted platform setting " + key));
    }
}
