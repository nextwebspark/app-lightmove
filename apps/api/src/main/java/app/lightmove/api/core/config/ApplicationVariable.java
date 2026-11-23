package app.lightmove.api.core.config;

import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.function.ToIntFunction;
import lombok.Getter;

/**
 * The allowlist of {@code lightmove.*} values a platform super admin may override without a release (#1314). This
 * list is the whole guarantee of what can be changed at runtime: a secret, a key, anything under {@code auth} or
 * {@code crypto}, a limit that protects sign-in or a switch that turns a security check off must never be on it.
 *
 * <p>Every entry is a whole number inside its bounds. The bounds hold an override only: the yml value is the
 * deployment's own and serves as it is.
 */
@Getter
public enum ApplicationVariable {

    SOURCING_MAX_COMPANIES_PER_RUN(ApplicationVariableGroup.EXECUTIVE_SOURCING, "max-companies-per-run",
            "Companies per run", "How many companies one Find executives run works through.", 1, 25, null,
            properties -> properties.enrichment().sourcing().maxCompaniesPerRun()),

    SOURCING_INDEX_PEOPLE_PER_COMPANY(ApplicationVariableGroup.EXECUTIVE_SOURCING, "index-people-per-company",
            "People indexed per company", "How many names are listed before any is checked.", 10, 500, null,
            properties -> properties.enrichment().sourcing().indexPeoplePerCompany()),

    SOURCING_TITLE_CHECKS_PER_COMPANY(ApplicationVariableGroup.EXECUTIVE_SOURCING, "title-checks-per-company",
            "Title checks per company", "How many job titles are compared with the brief.", 10, 500, null,
            properties -> properties.enrichment().sourcing().titleChecksPerCompany()),

    SOURCING_PARALLELISM(ApplicationVariableGroup.EXECUTIVE_SOURCING, "parallelism",
            "Companies worked at once", "How many companies a run works on at the same time.", 1, 8, null,
            properties -> properties.enrichment().sourcing().parallelism()),

    SOURCING_RUN_DEADLINE(ApplicationVariableGroup.EXECUTIVE_SOURCING, "run-deadline",
            "Run deadline", "How long a run may take before it stops with what it has.", 30, 600, "seconds",
            properties -> Math.toIntExact(properties.enrichment().sourcing().runDeadline().toSeconds()));

    private final ApplicationVariableGroup group;
    private final String key;
    private final String label;
    private final String description;
    private final int min;
    private final int max;
    /** What the number counts, where it is not a plain count. Null otherwise. */
    private final String unit;
    private final ToIntFunction<LightMoveProperties> configured;

    ApplicationVariable(ApplicationVariableGroup group, String name, String label, String description, int min,
                        int max, String unit, ToIntFunction<LightMoveProperties> configured) {
        this.group = group;
        this.key = group.getKeyPrefix() + "." + name;
        this.label = label;
        this.description = description;
        this.min = min;
        this.max = max;
        this.unit = unit;
        this.configured = configured;
    }

    public static Optional<ApplicationVariable> ofKey(String key) {
        return Arrays.stream(values()).filter(variable -> variable.key.equals(key)).findFirst();
    }

    /** The yml value: what serves while nothing overrides it. */
    public int configuredDefault(LightMoveProperties properties) {
        return configured.applyAsInt(properties);
    }

    /** A typed or stored value as this variable's number, or empty when it is not a whole number in bounds. */
    public OptionalInt parseInRange(String text) {
        if (text == null || !text.strip().matches("\\d{1,9}")) {
            return OptionalInt.empty();
        }
        int number = Integer.parseInt(text.strip());
        return number < min || number > max ? OptionalInt.empty() : OptionalInt.of(number);
    }
}
