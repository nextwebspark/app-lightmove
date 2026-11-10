package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/** Whether the people controls of epic #917 are offered; off, their controllers are never made. */
public record DataProtectionSettings(@DefaultValue("false") boolean enabled) {

    public static final String SWITCH = "lightmove.data-protection.enabled";
}
