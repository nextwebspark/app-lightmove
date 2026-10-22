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
    /** The bot could not post its recording notice in the call's chat, so it left rather than record unannounced. */
    CONSENT_NOT_SHOWN,
    /** The bot could not be sure its microphone and camera were off, so it never asked to join. */
    DEVICES_NOT_OFF,
    /** The bot stopped working mid-run. */
    CRASHED,
    /** The bot fell silent. */
    HEARTBEAT_LOST,
    /** The call was recorded, but its transcript or note could not be made; a consultant may try again. */
    PROCESSING_FAILED,
    /** The backup recorder standing behind ours failed too, or was never heard from again. */
    BACKUP_FAILED,
    /** A Zoom call, joined only by the backup recorder on the consultant's own Zoom account, which is not connected. */
    ZOOM_NOT_CONNECTED;

    /** What a bot may report about itself; the rest are the server's own findings. */
    public static final Set<NotetakerEndReason> REPORTED_BY_BOT = EnumSet.of(LAUNCH_FAILED, JOIN_BLOCKED, NOT_ADMITTED,
            CONSENT_NOT_SHOWN, DEVICES_NOT_OFF, CRASHED);
}
