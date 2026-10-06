package app.lightmove.api.outreach.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.LocalTime;
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

    /** A follow-up's time of day (V113); null keeps the time of day the step before it went. */
    @Column(name = "send_time")
    private LocalTime sendTime;

    public static SequenceStep of(int delayWorkingDays, String subject, String body, LocalTime sendTime) {
        SequenceStep step = new SequenceStep();
        step.delayWorkingDays = delayWorkingDays;
        step.subject = subject;
        step.body = body;
        step.sendTime = sendTime;
        return step;
    }
}
