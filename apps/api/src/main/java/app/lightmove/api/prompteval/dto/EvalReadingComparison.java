package app.lightmove.api.prompteval.dto;

/** One scored thing of a case, both versions side by side. {@code expected} is the case's label in words. */
public record EvalReadingComparison(String key, String label, String expected, EvalReadingSide candidate,
                                    EvalReadingSide baseline) {
}
