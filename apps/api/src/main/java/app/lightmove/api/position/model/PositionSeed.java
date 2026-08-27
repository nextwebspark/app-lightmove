package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.PositionSeniority;
import java.util.List;

/**
 * Everything a role template decides for a fresh brief. Scalars a template cannot know — the client's
 * location, the package, the department — are absent rather than guessed.
 *
 * <p>Top-level rather than nested inside the template library: a type whose name only means something
 * through its enclosing path is the thing this codebase renamed {@code Auth.Jwt} to avoid.
 */
public record PositionSeed(
        PositionSeniority seniority,
        String reportsTo,
        String narrative,
        List<String> responsibilities,
        List<PositionCriterion> criteria,
        List<PositionCompetency> competencies
) {
}
