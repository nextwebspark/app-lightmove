package app.lightmove.api.prompteval.dto;

/** A check two versions did differently on one case: how many of each one's {@code repeats} answers passed it. */
public record EvalCaseCheckChange(String id, String label, int candidatePassed, int baselinePassed, int repeats) {
}
