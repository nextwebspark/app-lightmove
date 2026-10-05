package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.model.SequenceStep;
import java.time.LocalTime;

public record SequenceStepResponse(int delayWorkingDays, String subject, String body, LocalTime sendTime) {

    public static SequenceStepResponse of(SequenceStep step) {
        return new SequenceStepResponse(step.getDelayWorkingDays(), step.getSubject(), step.getBody(),
                step.getSendTime());
    }
}
