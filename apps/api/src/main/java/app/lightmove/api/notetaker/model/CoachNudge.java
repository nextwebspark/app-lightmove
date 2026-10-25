package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.NudgeStatus;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/** A question the live copilot suggests asking next, and why: a competency short of an example, or a gap not asked. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record CoachNudge(String id, String text, String reason, NudgeStatus status) {

    public CoachNudge as(NudgeStatus next) {
        return new CoachNudge(id, text, reason, next);
    }
}
