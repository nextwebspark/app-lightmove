package app.lightmove.api.prompteval.dto;

import app.lightmove.api.prompteval.constant.JudgePick;

/** A pick the judge and the person made differently. {@code judge} is null where it gave none that could be read. */
public record EvalRubricDifference(String caseKey, String criterionKey, String criterion, JudgePick person,
                                   JudgePick judge, String reason) {
}
