package app.lightmove.api.notetaker.service;

import app.lightmove.api.notetaker.constant.MeetingRecorderKind;
import app.lightmove.api.notetaker.constant.NotetakerEndReason;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.model.NotetakerLaunch;
import app.lightmove.api.notetaker.model.RecallBotRequest;
import app.lightmove.api.outreach.service.ZoomService;
import java.util.Map;

/**
 * Recall.ai's bot as a recorder: the backup behind our own, and the only one a Zoom call gets, let in on the
 * consultant's own Zoom account. Built by the chain rather than registered as a recorder, so it never stands in for
 * our own bot where one recorder is asked for.
 */
public class RecallRecorder implements MeetingRecorder {

    private final RecallBotApi recall;
    private final ZoomService zoom;

    public RecallRecorder(RecallBotApi recall, ZoomService zoom) {
        this.recall = recall;
        this.zoom = zoom;
    }

    /** What every bot of ours says in the call's chat before anything is recorded; the browser bot's own words. */
    static String consentLine(String botName) {
        return botName + " is recording and transcribing this call to take notes for Uncava. "
                + "If you'd rather not be recorded, ask the host to remove it.";
    }

    @Override
    public MeetingRecorderKind kind() {
        return MeetingRecorderKind.RECALL;
    }

    public boolean isOffered() {
        return recall.isOffered();
    }

    @Override
    public String launch(NotetakerLaunch launch) {
        String onBehalf = null;
        if (launch.platform() == NotetakerPlatform.ZOOM) {
            onBehalf = zoom.onBehalfTokenFor(launch.userId(), launch.workspaceId(), launch.joinUrl())
                    .orElseThrow(() -> new BotNotSent(NotetakerEndReason.ZOOM_NOT_CONNECTED, null));
        }
        return recall.send(new RecallBotRequest(launch.joinUrl(), launch.botName(), consentLine(launch.botName()),
                Map.of("workspace_id", launch.workspaceId().toString(),
                        "recording_id", launch.recordingId().toString(),
                        "person_id", launch.personId() == null ? "" : launch.personId().toString()),
                onBehalf));
    }

    @Override
    public void requestLeave(String externalId) {
        recall.leave(externalId);
    }
}
