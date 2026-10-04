package app.lightmove.api.outreach.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One email of a sequence. Only the first carries a subject; the others reply in its thread. */
@Embeddable
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SequenceStep {

    @Column(name = "delay_working_days", nullable = false)
    private int delayWorkingDays;

    @Column(name = "subject", length = 200)
    private String subject;

    @Column(name = "body", nullable = false)
    private String body;

    public static SequenceStep of(int delayWorkingDays, String subject, String body) {
        SequenceStep step = new SequenceStep();
        step.delayWorkingDays = delayWorkingDays;
        step.subject = subject;
        step.body = body;
        return step;
    }
}
