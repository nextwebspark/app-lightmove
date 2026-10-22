package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.outreach.model.KeptMeeting;
import java.util.List;
import java.util.Optional;

/**
 * One call and every calendar copy of it, with what decides whether the bot joins it. {@code sentFrom} is the
 * copy of the first consultant (by id) whose own switch is on; empty when every owner turned it off.
 */
public record NotetakerCall(String callKey, List<KeptMeeting> copies, NotetakerPlatform platform, boolean joinable,
                            boolean workspaceAllows, boolean meetingAllows, Optional<KeptMeeting> sentFrom,
                            boolean doNotContact) {

    public KeptMeeting first() {
        return copies.getFirst();
    }

    /** Every switch on and a platform ours, before do not contact is asked. */
    public boolean isSwitchedOn() {
        return joinable && workspaceAllows && meetingAllows && sentFrom.isPresent();
    }
}
