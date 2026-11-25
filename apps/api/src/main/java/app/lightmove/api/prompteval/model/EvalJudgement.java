package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.JudgePick;
import app.lightmove.api.prompteval.constant.JudgedWinner;
import java.util.UUID;

/**
 * The judge's pick on one criterion of one pair of a run's answers. {@code candidateShownAs} is the order it saw
 * them in; {@code winner} is null when it gave no pick that could be read.
 */
public record EvalJudgement(UUID caseId, int repeat, String criterionKey, JudgePick candidateShownAs,
                            JudgedWinner winner, String reason) {}
