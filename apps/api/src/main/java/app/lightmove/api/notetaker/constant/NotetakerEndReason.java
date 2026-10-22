package app.lightmove.api.notetaker.constant;

import java.util.EnumSet;
import java.util.Set;

/** Why a run ended without notes: a skipped call's reason, or a failed bot's. */
public enum NotetakerEndReason {

    /** Someone on the call is marked do not contact. */
    DO_NOT_CONTACT,
    /** The bot could not be started. */
    LAUNCH_FAILED,
    /** Started, but never reported back. */
    LAUNCH_TIMEOUT,
    /** The call's host does not let a guest in. */
    JOIN_BLOCKED,
    /** Nobody admitted the bot from the lobby in time. */
    NOT_ADMITTED,
    /** The bot stopped working mid-run. */
    CRASHED,
    /** The bot fell silent. */
    HEARTBEAT_LOST;

    /** What a bot may report about itself; the rest are the server's own findings. */
    public static final Set<NotetakerEndReason> REPORTED_BY_BOT = EnumSet.of(LAUNCH_FAILED, JOIN_BLOCKED, NOT_ADMITTED,
            CRASHED);
}
