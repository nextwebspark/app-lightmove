package app.lightmove.api.prompteval.constant;

/** Which of a run's two versions the judge found better on one criterion of one pair of answers. */
public enum JudgedWinner {
    CANDIDATE,
    BASELINE,
    TIE
}
