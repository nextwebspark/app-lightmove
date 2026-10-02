package app.lightmove.api.outreach.dto;

import app.lightmove.api.outreach.model.SequenceStep;

public record SequenceStepResponse(int delayWorkingDays, String subject, String body) {

    public static SequenceStepResponse of(SequenceStep step) {
        return new SequenceStepResponse(step.getDelayWorkingDays(), step.getSubject(), step.getBody());
    }
}
