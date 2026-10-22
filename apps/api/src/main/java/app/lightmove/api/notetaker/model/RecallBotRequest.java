package app.lightmove.api.notetaker.model;

import java.util.Map;

/**
 * A backup bot to send: where, under what name, saying what in the call's chat as it joins, tagged with whose run it
 * is. A Zoom call also needs the consultant's on-behalf-of token, which never reaches a log.
 */
public record RecallBotRequest(String meetingUrl, String botName, String consentLine, Map<String, String> metadata,
                               String zoomOnBehalfToken) {

    @Override
    public String toString() {
        return "RecallBotRequest[botName=" + botName + ", metadata=" + metadata + ", zoomOnBehalfToken="
                + (zoomOnBehalfToken == null ? "none" : "<redacted>") + "]";
    }
}
