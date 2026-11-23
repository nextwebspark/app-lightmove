package app.lightmove.api.prompteval.dto;

/** One scored thing of an answer, as a person labelling reads it. {@code score} null: the answer gave none. */
public record EvalCalibrationReading(String label, Double score, String text) {
}
