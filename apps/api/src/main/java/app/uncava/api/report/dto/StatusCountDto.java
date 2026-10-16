package app.uncava.api.report.dto;

/**
 * How many executives stand at one stage of the workspace's pipeline — a mix, not a funnel. {@code status}
 * holds the stage key; {@code label} and {@code colour} are the stage's.
 */
public record StatusCountDto(String status, String label, String colour, int count) {}
