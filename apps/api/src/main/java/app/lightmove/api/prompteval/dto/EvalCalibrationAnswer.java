package app.lightmove.api.prompteval.dto;

import java.util.List;

/** One of a pair's answers: its words for a prompt answered in words, else what it scored. Never which version. */
public record EvalCalibrationAnswer(String prose, List<EvalCalibrationReading> readings) {
}
