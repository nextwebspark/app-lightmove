package app.lightmove.api.core.llm.model;

/** The values one generation setting may take on one model, both ends included. */
public record SettingRange(double min, double max) {

    public SettingRange {
        if (min > max) {
            throw new IllegalArgumentException("A setting range must run upwards, but was " + min + " to " + max);
        }
    }

    public boolean holds(double value) {
        return value >= min && value <= max;
    }
}
