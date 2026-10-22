package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.model.NotetakerLaunch;

/**
 * Sends a bot into a call. Our own browser bot answers first; Recall stands behind it when ours fails (#955).
 * A launch is never retried by the caller: a second bot in an executive's call is worse than none.
 */
public interface MeetingRecorder {

    MeetingRecorderKind kind();

    /** Sends the bot; answers the recorder's own id for it. Throws when the bot could not be sent. */
    String launch(NotetakerLaunch launch);

    /**
     * Asks a bot already sent to leave. Our own bot hears it on its next heartbeat, so ours does nothing here;
     * Recall's is told directly.
     */
    void requestLeave(String externalId);
}
