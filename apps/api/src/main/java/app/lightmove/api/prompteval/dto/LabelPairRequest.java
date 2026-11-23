package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.JudgePick;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;

/** A person's picks on one pair: one for every criterion of its rubric, by criterion key. */
public record LabelPairRequest(@NotEmpty Map<String, JudgePick> picks) {
}
