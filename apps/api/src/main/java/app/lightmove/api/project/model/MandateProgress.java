package app.lightmove.api.project.model;

import app.lightmove.api.project.constant.MandatePhase;
import app.lightmove.api.project.constant.ProjectType;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * How far a mandate has got, and how much of its window it has spent getting there. Every figure the
 * projects list and its drawer show is answered from here, and so is the health the two of them
 * colour — which is why this takes {@code today} as a parameter rather than reading the clock.
 */
public record MandateProgress(MandateTimeline timeline, ProjectProgressCounts counts, LocalDate today) {

    private static final double DAYS_PER_WEEK = 7.0;

    /**
     * A universe nobody has built is not a mapped one. Without the guard an empty new mandate reads as
     * fully covered and jumps straight to its shortlist clock on the day it is created.
     */
    public boolean mappingComplete() {
        return counts.universeTotal() > 0 && counts.researched() >= counts.universeTotal();
    }

    public MandatePhase activePhase() {
        return timeline.type() == ProjectType.EXECUTIVE_SEARCH && mappingComplete()
                ? MandatePhase.ENGAGE
                : MandatePhase.MAP;
    }

    public LocalDate governingMilestone() {
        return timeline.governingMilestone(mappingComplete());
    }

    public int mapPercent() {
        return percent(counts.researched(), counts.universeTotal());
    }

    public int engagePercent() {
        return percent(counts.pastIdentified(), counts.candidatesTotal());
    }

    /** How much of the phase the mandate is in has been done, as the health arithmetic reads it. */
    public double completion() {
        return activePhase() == MandatePhase.MAP
                ? fraction(counts.researched(), counts.universeTotal())
                : fraction(counts.pastIdentified(), counts.candidatesTotal());
    }

    /** Days since the mandate began — the span velocity is a rate over, not the current phase's clock. */
    public int daysElapsed() {
        return (int) Math.max(0, ChronoUnit.DAYS.between(timeline.startDate(), today));
    }

    /**
     * Where the clock for the phase in hand starts: the mandate's own start while it is mapping, and
     * the mapping target it just met once a search moves on to its shortlist.
     *
     * <p>That second case is the point. Measuring the engage half from day one would count the whole
     * mapping window as time already spent on work that could not begin until the map was done, so a
     * mandate that finished its map exactly on target would flip to off track on the day it hit it.
     */
    private LocalDate phaseStart() {
        return activePhase() == MandatePhase.MAP || timeline.mappingTarget() == null
                ? timeline.startDate()
                : timeline.mappingTarget();
    }

    /**
     * How much of the window to the governing milestone has been spent. A window of no length — a
     * milestone on the day the phase opens — is spent the moment that day arrives rather than
     * dividing by zero.
     */
    public double elapsedFraction() {
        LocalDate milestone = governingMilestone();
        if (milestone == null) {
            return 0;
        }
        LocalDate from = phaseStart();
        long window = ChronoUnit.DAYS.between(from, milestone);
        if (window <= 0) {
            return today.isBefore(milestone) ? 0 : 1;
        }
        return Math.clamp(ChronoUnit.DAYS.between(from, today) / (double) window, 0, 1);
    }

    public boolean milestonePassed() {
        LocalDate milestone = governingMilestone();
        return milestone != null && today.isAfter(milestone);
    }

    /** Days left to the governing milestone, negative once it has gone by. Null when none is stated. */
    public Integer daysRemaining() {
        LocalDate milestone = governingMilestone();
        return milestone == null ? null : (int) ChronoUnit.DAYS.between(today, milestone);
    }

    /**
     * Executives mapped per week so far. The first week counts as a whole one, so a mandate three days
     * old reports what it has done rather than three times it.
     */
    public double mappingVelocityPerWeek() {
        return counts.candidatesTotal() / Math.max(1, daysElapsed() / DAYS_PER_WEEK);
    }

    private static int percent(long part, long whole) {
        return whole <= 0 ? 0 : (int) Math.round(100.0 * part / whole);
    }

    private static double fraction(long part, long whole) {
        return whole <= 0 ? 0 : Math.min(1, part / (double) whole);
    }
}
