package app.lightmove.api.notetaker.constant;

import java.util.EnumSet;
import java.util.Set;

/** Where one bot on one call has got to. */
public enum NotetakerStatus {

    LAUNCHING,
    WAITING_ROOM,
    RECORDING,
    REMOVE_REQUESTED,
    PROCESSING,
    DONE,
    FAILED,
    REMOVED,
    SKIPPED;

    /** A bot may still be in the call, or about to be. */
    public static final Set<NotetakerStatus> IN_CALL = EnumSet.of(LAUNCHING, WAITING_ROOM, RECORDING, REMOVE_REQUESTED);

    public boolean isInCall() {
        return IN_CALL.contains(this);
    }
}
