package app.lightmove.api.prompteval.constant;

/** Which of a pair's two answers is the better on one criterion, as the pair names them. */
public enum JudgePick {
    A,
    B,
    TIE;

    /** The same pick with the pair's two answers swapped. */
    public JudgePick swapped() {
        return this == A ? B : this == B ? A : TIE;
    }
}
