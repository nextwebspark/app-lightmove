package app.lightmove.api.strategy.dto;

/** One accepted value of a closed vocabulary: the token sent to the vendor, and what the sidebar shows. */
public record VocabularyOption(String value, String label) {}
