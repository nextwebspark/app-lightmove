package app.lightmove.api.promptregistry.dto;

import java.util.List;

/**
 * What a judge found on the run a put live would rest on. {@code counted}: its criteria were part of the run's
 * result; otherwise they are shown and {@code state} says why they did not count. {@code forCandidate} of the
 * {@code criteria} went to the version beyond the tolerance, and {@code forBaseline} names those that went to the
 * one serving.
 */
public record PromotionGateJudged(boolean counted, String state, int criteria, int forCandidate,
                                  List<String> forBaseline) {
}
