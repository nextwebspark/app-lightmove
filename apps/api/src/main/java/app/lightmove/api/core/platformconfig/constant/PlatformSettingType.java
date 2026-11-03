package app.lightmove.api.core.platformconfig.constant;

/** How an allowlisted value is written, parsed and bounded. */
public enum PlatformSettingType {
    INT,
    LONG,
    DOUBLE,
    /** Bounded in whole days; written as Spring writes one ({@code 14d}). */
    DURATION,
    /** A Gemini model name; the pattern is the bound. */
    MODEL_NAME
}
