package app.lightmove.api.prompteval.model;

import app.lightmove.api.prompteval.constant.JudgePick;

/** The judge's pick on one criterion, in the A and B it was shown. {@code pick} null: none that could be read. */
public record JudgedPick(JudgePick pick, String reason) {

    public static final JudgedPick UNREADABLE = new JudgedPick(null, null);
}
