package app.lightmove.api.core.platformconfig.constant;

import static app.lightmove.api.core.platformconfig.constant.PlatformSettingGroup.ASSISTANT;
import static app.lightmove.api.core.platformconfig.constant.PlatformSettingGroup.EXPORT;
import static app.lightmove.api.core.platformconfig.constant.PlatformSettingGroup.FAIR_USE;
import static app.lightmove.api.core.platformconfig.constant.PlatformSettingGroup.MCP;
import static app.lightmove.api.core.platformconfig.constant.PlatformSettingGroup.SOURCING;
import static app.lightmove.api.core.platformconfig.constant.PlatformSettingGroup.TRIAL;
import static app.lightmove.api.core.platformconfig.constant.PlatformSettingType.DOUBLE;
import static app.lightmove.api.core.platformconfig.constant.PlatformSettingType.DURATION;
import static app.lightmove.api.core.platformconfig.constant.PlatformSettingType.INT;
import static app.lightmove.api.core.platformconfig.constant.PlatformSettingType.LONG;
import static app.lightmove.api.core.platformconfig.constant.PlatformSettingType.MODEL_NAME;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.regex.Pattern;
import org.springframework.boot.convert.DurationStyle;

/**
 * The allowlist of {@code lightmove.*} values a super admin may change from Settings → Platform → Variables, and the
 * whole guarantee that nothing else can be: a key that is not a constant here is never stored, read or applied.
 * Never add a secret, a key, an {@code auth.*} or {@code crypto.*} value, a sign-in rate limit, or anything that
 * switches a check off. The default is what the deployment bound at boot, so Reset returns to its yml and env.
 */
public enum PlatformSetting {

    SOURCING_MAX_COMPANIES_PER_RUN("enrichment.sourcing.max-companies-per-run", SOURCING, INT, 1, 25,
            "Companies per run",
            "How many companies one Find executives press searches when none are ticked.",
            properties -> properties.enrichment().sourcing().maxCompaniesPerRun()),
    SOURCING_HITS_PER_COMPANY("enrichment.sourcing.hits-per-company", SOURCING, INT, 1, 25,
            "People asked per company",
            "Profiles one search asks the index for at each company. Every returned profile is billed.",
            properties -> properties.enrichment().sourcing().hitsPerCompany()),
    SOURCING_PICKS_PER_COMPANY("enrichment.sourcing.picks-per-company", SOURCING, INT, 1, 10,
            "People filed per company",
            "The best-ranked hits filed at each company. Never more than the people asked per company.",
            properties -> properties.enrichment().sourcing().picksPerCompany()),
    SOURCING_MAX_SEARCH_ROUNDS("enrichment.sourcing.max-search-rounds", SOURCING, INT, 1, 5,
            "Search rounds",
            "How many times a company that found nobody is searched again with other title words.",
            properties -> properties.enrichment().sourcing().maxSearchRounds()),

    FAIR_USE_PEOPLE_SEARCH_PAGE_PER_SEAT("billing.fair-use.people-search-page-per-seat", FAIR_USE, LONG, 0, 1_000_000,
            "People search pages",
            "Pages of Strategy → People a workspace may read each month per staff seat. 0 is no ceiling.",
            properties -> properties.billing().fairUse().peopleSearchPagePerSeat()),
    FAIR_USE_SOURCING_RUN_PER_SEAT("billing.fair-use.sourcing-run-per-seat", FAIR_USE, LONG, 0, 1_000_000,
            "Executives found",
            "Executives Find executives may file each month per staff seat. 0 is no ceiling.",
            properties -> properties.billing().fairUse().sourcingRunPerSeat()),
    FAIR_USE_AI_ENRICH_PER_SEAT("billing.fair-use.ai-enrich-per-seat", FAIR_USE, LONG, 0, 1_000_000,
            "AI suggestions",
            "Suggest with AI and Assess with AI presses each month per staff seat. 0 is no ceiling.",
            properties -> properties.billing().fairUse().aiEnrichPerSeat()),
    FAIR_USE_OUTREACH_OPENER_PER_SEAT("billing.fair-use.outreach-opener-per-seat", FAIR_USE, LONG, 0, 1_000_000,
            "Outreach openers",
            "People an opener may be written for each month per staff seat. 0 is no ceiling.",
            properties -> properties.billing().fairUse().outreachOpenerPerSeat()),
    FAIR_USE_ASSISTANT_ASK_PER_SEAT("billing.fair-use.assistant-ask-per-seat", FAIR_USE, LONG, 0, 1_000_000,
            "Assistant questions",
            "Questions the assistant may answer each month per staff seat. 0 is no ceiling.",
            properties -> properties.billing().fairUse().assistantAskPerSeat()),

    TRIAL_CREDITS("billing.trial.credits", TRIAL, LONG, 0, 1_000,
            "Trial credits",
            "Contact credits a newly founded workspace starts its trial with.",
            properties -> properties.billing().trial().credits()),
    TRIAL_LENGTH("billing.trial.length", TRIAL, DURATION, 1, 90,
            "Trial length",
            "How long a newly founded workspace's trial lasts, in days, written like 14d.",
            properties -> properties.billing().trial().length()),

    EXPORT_MAX_COMPANIES("export.max-companies", EXPORT, INT, 1, 100_000,
            "Companies per export",
            "An export with more companies than this is refused rather than cut short.",
            properties -> properties.export().maxCompanies()),
    EXPORT_MAX_CANDIDATES("export.max-candidates", EXPORT, INT, 1, 100_000,
            "Candidates per export",
            "An export with more candidates than this is refused rather than cut short.",
            properties -> properties.export().maxCandidates()),
    EXPORT_MAX_AUDIT_EVENTS("export.max-audit-events", EXPORT, INT, 1, 100_000,
            "Audit log rows per export",
            "An audit log export with more rows than this is refused.",
            properties -> properties.export().maxAuditEvents()),

    MCP_MAX_RESULT_CHARS("mcp.max-result-chars", MCP, INT, 10_000, 400_000,
            "Characters per answer",
            "A connected AI app's page is cut at this many characters and reads on from the next row.",
            properties -> properties.mcp().maxResultChars()),

    ASSISTANT_MODEL("assistant.model", ASSISTANT, MODEL_NAME, 0, 0,
            "Model",
            "The Gemini model the assistant answers with.",
            properties -> properties.assistant().model()),
    ASSISTANT_TEMPERATURE("assistant.temperature", ASSISTANT, DOUBLE, 0, 1,
            "Temperature",
            "How varied the assistant's answers are, from 0.0 to 1.0.",
            properties -> properties.assistant().temperature()),
    ASSISTANT_THINKING_BUDGET("assistant.thinking-budget", ASSISTANT, INT, 0, 8_192,
            "Thinking budget",
            "Tokens the model may spend reasoning before the assistant answers.",
            properties -> properties.assistant().thinkingBudget());

    private static final Pattern MODEL_NAME_PATTERN = Pattern.compile("^gemini-[a-z0-9.-]{1,48}$");

    private final String key;
    private final PlatformSettingGroup group;
    private final PlatformSettingType type;
    private final long min;
    private final long max;
    private final String label;
    private final String description;
    private final Function<LightMoveProperties, Object> bootValue;

    PlatformSetting(String key, PlatformSettingGroup group, PlatformSettingType type, long min, long max,
                    String label, String description, Function<LightMoveProperties, Object> bootValue) {
        this.key = key;
        this.group = group;
        this.type = type;
        this.min = min;
        this.max = max;
        this.label = label;
        this.description = description;
        this.bootValue = bootValue;
    }

    public static Optional<PlatformSetting> byKey(String key) {
        return Arrays.stream(values()).filter(setting -> setting.key.equals(key)).findFirst();
    }

    public String key() {
        return key;
    }

    public PlatformSettingGroup group() {
        return group;
    }

    public PlatformSettingType type() {
        return type;
    }

    /** The least an override may be: days for a duration, nothing for a model name. */
    public long min() {
        return min;
    }

    public long max() {
        return max;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    /** The value this deployment bound at boot, typed as {@link #parse} answers. */
    public Object defaultIn(LightMoveProperties properties) {
        Object bound = bootValue.apply(properties);
        return type == INT || type == LONG ? ((Number) bound).longValue() : bound;
    }

    /**
     * The typed value of {@code raw}, or a {@code VALIDATION_FAILED} naming this key. Every message is a fixed
     * sentence built from the bounds alone, never the input. Whole numbers are answered as a {@code Long}.
     */
    public Object parse(String raw) {
        String value = raw == null ? "" : raw.strip();
        return switch (type) {
            case INT, LONG -> wholeNumber(value);
            case DOUBLE -> decimal(value);
            case DURATION -> days(value);
            case MODEL_NAME -> {
                if (!MODEL_NAME_PATTERN.matcher(value).matches()) {
                    throw refused("Must be a Gemini model name, such as gemini-3.5-flash");
                }
                yield value;
            }
        };
    }

    /** How a typed value is stored and shown: a duration as Spring writes it, a number without grouping. */
    public String format(Object value) {
        if (value instanceof Duration duration) {
            return duration.toSeconds() % 86_400 == 0 ? duration.toDays() + "d" : duration.toString();
        }
        return String.valueOf(value);
    }

    private long wholeNumber(String value) {
        long parsed;
        try {
            parsed = Long.parseLong(value);
        } catch (NumberFormatException notWhole) {
            throw refused("Must be a whole number");
        }
        if (parsed < min || parsed > max) {
            throw refused("Must be between " + min + " and " + max);
        }
        return parsed;
    }

    private double decimal(String value) {
        double parsed;
        try {
            parsed = Double.parseDouble(value);
        } catch (NumberFormatException notNumber) {
            throw refused("Must be a number");
        }
        if (!Double.isFinite(parsed) || parsed < min || parsed > max) {
            throw refused("Must be between " + min + " and " + max);
        }
        return parsed;
    }

    private Duration days(String value) {
        Duration parsed;
        try {
            parsed = DurationStyle.detectAndParse(value);
        } catch (IllegalArgumentException notDuration) {
            throw refused("Must be a number of days, such as 14d");
        }
        if (parsed.compareTo(Duration.ofDays(min)) < 0 || parsed.compareTo(Duration.ofDays(max)) > 0) {
            throw refused("Must be between " + min + "d and " + max + "d");
        }
        return parsed;
    }

    private ApiException refused(String message) {
        return ApiException.withField(ErrorCode.VALIDATION_FAILED, key, message);
    }
}
