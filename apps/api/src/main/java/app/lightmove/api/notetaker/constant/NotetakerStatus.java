package app.lightmove.api.notetaker.constant;

import java.util.Set;

/** Where a Notetaker bot has got to, from the press that sent it to the note it filed. */
public enum NotetakerStatus {

    /** Recall holds the bot and will send it at the call's start. */
    SCHEDULED,
    /** On its way in, or waiting in the lobby. */
    JOINING,
    RECORDING,
    /** The call is over; the transcript and the note are being made. */
    PROCESSING,
    DONE,
    FAILED,
    CANCELLED;

    /** Recording counted against the one-bot-per-call claim (V151's partial unique index). */
    public static final Set<NotetakerStatus> LIVE = Set.of(SCHEDULED, JOINING, RECORDING, PROCESSING);

    public boolean isLive() {
        return LIVE.contains(this);
    }
}
