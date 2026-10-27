package app.lightmove.api.notetaker.model;

import app.lightmove.api.notetaker.constant.CoachState;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * The live copilot's reading of a call so far (V154 {@code live}): how far each competency is shown, which of the
 * record's gaps the executive spoke to and which prepared questions were asked. A {@code nudges} key left in an older
 * row is ignored.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record LiveCoaching(List<CompetencyCoverage> coverage, List<GapHeard> gaps, List<String> asked,
                           CoachState coach) {

    public static LiveCoaching empty() {
        return new LiveCoaching(List.of(), List.of(), List.of(), CoachState.RUNNING);
    }

    public LiveCoaching withCoach(CoachState next) {
        return new LiveCoaching(coverage, gaps, asked, next);
    }

    public LiveCoaching withAsked(List<String> next) {
        return new LiveCoaching(coverage, gaps, List.copyOf(next), coach);
    }
}
