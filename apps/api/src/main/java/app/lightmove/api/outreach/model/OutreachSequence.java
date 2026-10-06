package app.lightmove.api.outreach.model;

import app.lightmove.api.core.persistence.model.BaseEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * A position's outreach sequence (V100): a name, up to three emails and the days and hours they may go
 * (V113), saved whole by the editor.
 */
@Entity
@Table(name = "app_lm_outreach_sequence")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutreachSequence extends BaseEntity {

    public static final int MAX_STEPS = 3;

    @Column(name = "workspace_id", nullable = false, updatable = false)
    private UUID workspaceId;

    @Column(name = "project_id", nullable = false, updatable = false)
    private UUID projectId;

    @Column(name = "name", nullable = false, length = 120)
    private String name;

    @Column(name = "created_by", updatable = false)
    private UUID createdBy;

    /** ISO weekday bitmask: Monday is bit 0, Sunday bit 6. */
    @Column(name = "send_days", nullable = false)
    private int sendDays;

    @Column(name = "window_start", nullable = false)
    private LocalTime windowStart;

    @Column(name = "window_end", nullable = false)
    private LocalTime windowEnd;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "app_lm_outreach_sequence_step", joinColumns = @JoinColumn(name = "sequence_id"))
    @OrderColumn(name = "sort_order")
    private List<SequenceStep> steps = new ArrayList<>();

    public static OutreachSequence written(UUID workspaceId, UUID projectId, UUID createdBy, String name,
                                           List<SequenceStep> steps, SendingWindow schedule) {
        OutreachSequence sequence = new OutreachSequence();
        sequence.workspaceId = workspaceId;
        sequence.projectId = projectId;
        sequence.createdBy = createdBy;
        sequence.rewrite(name, steps, schedule);
        return sequence;
    }

    public void rewrite(String newName, List<SequenceStep> newSteps, SendingWindow schedule) {
        this.name = newName;
        this.steps.clear();
        this.steps.addAll(newSteps);
        this.sendDays = maskOf(schedule.workingDays());
        this.windowStart = schedule.start();
        this.windowEnd = schedule.end();
    }

    public SendingWindow sendingWindow() {
        Set<DayOfWeek> days = EnumSet.noneOf(DayOfWeek.class);
        for (DayOfWeek day : DayOfWeek.values()) {
            if ((sendDays & bitOf(day)) != 0) {
                days.add(day);
            }
        }
        return new SendingWindow(windowStart, windowEnd, days);
    }

    public SequenceStep firstStep() {
        return steps.getFirst();
    }

    /** Whether any step's subject or body asks for {@code token}. */
    public boolean uses(String token) {
        return anyStepUses(steps, token);
    }

    public static boolean anyStepUses(List<SequenceStep> steps, String token) {
        return steps.stream().anyMatch(step -> SequenceTokens.uses(step.getSubject(), token)
                || SequenceTokens.uses(step.getBody(), token));
    }

    private static int maskOf(Set<DayOfWeek> days) {
        return days.stream().mapToInt(OutreachSequence::bitOf).reduce(0, (mask, bit) -> mask | bit);
    }

    private static int bitOf(DayOfWeek day) {
        return 1 << (day.getValue() - 1);
    }
}
