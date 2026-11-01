package app.lightmove.api.core.config;

import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Row-level security — {@code lightmove.row-level-security.*}.
 *
 * @param requireEnforced fail the boot when the database would not enforce it, rather than warn
 */
public record RowLevelSecuritySettings(@DefaultValue("true") boolean requireEnforced) {}
