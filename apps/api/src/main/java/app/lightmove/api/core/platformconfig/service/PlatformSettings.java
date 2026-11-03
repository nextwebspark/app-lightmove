package app.lightmove.api.core.platformconfig.service;

import app.lightmove.api.core.config.ExportSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.platformconfig.constant.PlatformSetting;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The one read of an allowlisted value: a super admin's override where there is one, else what the deployment bound
 * at boot. Read it where it is used and never keep it in a field, or a change waits for the next deploy.
 */
@Component
@RequiredArgsConstructor
public class PlatformSettings {

    private final PlatformOverrides overrides;
    private final LightMoveProperties properties;

    public int intValue(PlatformSetting setting) {
        return Math.toIntExact(((Number) valueOf(setting)).longValue());
    }

    public long longValue(PlatformSetting setting) {
        return ((Number) valueOf(setting)).longValue();
    }

    public double doubleValue(PlatformSetting setting) {
        return ((Number) valueOf(setting)).doubleValue();
    }

    public Duration duration(PlatformSetting setting) {
        return (Duration) valueOf(setting);
    }

    public String text(PlatformSetting setting) {
        return (String) valueOf(setting);
    }

    /** The three export ceilings in force, in the shape every export already reads them. */
    public ExportSettings exportCaps() {
        return new ExportSettings(intValue(PlatformSetting.EXPORT_MAX_COMPANIES),
                intValue(PlatformSetting.EXPORT_MAX_CANDIDATES), intValue(PlatformSetting.EXPORT_MAX_AUDIT_EVENTS));
    }

    /** The typed value in force, as {@link PlatformSetting#parse} answers one. */
    public Object valueOf(PlatformSetting setting) {
        Object override = overrides.current().settings().get(setting);
        return override != null ? override : setting.defaultIn(properties);
    }
}
