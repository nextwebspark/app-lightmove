package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.CoachState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The live copilot's reading of a call so far (V153 {@code live}): how far each competency is shown, which of the
 * record's gaps the executive spoke to, which prepared questions were asked, and every nudge it made — dismissed ones
 * included, so none is suggested twice.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LiveCoaching(List<CompetencyCoverage> coverage, List<GapHeard> gaps, List<String> asked,
                           List<CoachNudge> nudges, CoachState coach) {

    public static LiveCoaching empty() {
        return new LiveCoaching(List.of(), List.of(), List.of(), List.of(), CoachState.RUNNING);
    }

    public LiveCoaching withCoach(CoachState next) {
        return new LiveCoaching(coverage, gaps, asked, nudges, next);
    }

    public LiveCoaching withAsked(List<String> next) {
        return new LiveCoaching(coverage, gaps, List.copyOf(next), nudges, coach);
    }

    public LiveCoaching withNudges(List<CoachNudge> next) {
        return new LiveCoaching(coverage, gaps, asked, List.copyOf(next), coach);
    }
}
