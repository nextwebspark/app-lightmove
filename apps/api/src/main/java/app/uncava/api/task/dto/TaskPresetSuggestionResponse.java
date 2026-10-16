package app.uncava.api.task.dto;

/** A title the team keeps typing rather than picking — Settings offers to make it a preset. */
public record TaskPresetSuggestionResponse(String label, long count) {}
