package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.NotetakerEndReason;

/** No recorder could send a bot: the run ends with this reason. */
public class BotNotSent extends RuntimeException {

    private final NotetakerEndReason reason;

    public BotNotSent(NotetakerEndReason reason, Throwable cause) {
        super("No bot was sent: " + reason, cause);
        this.reason = reason;
    }

    public NotetakerEndReason reason() {
        return reason;
    }
}
