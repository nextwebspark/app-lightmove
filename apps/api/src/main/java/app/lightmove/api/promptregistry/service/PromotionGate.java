package app.lightmove.api.promptregistry.service;

import app.lightmove.api.promptregistry.model.PromotionGateResult;
import java.util.Map;

/**
 * What the evals found for each version of a prompt measured against the one serving. Declared here and answered
 * by {@code prompteval}, which depends on this package, so the registry never learns that runs exist.
 */
public interface PromotionGate {

    /** By version; a version no run compares with {@code liveVersion} is absent. */
    Map<Integer, PromotionGateResult> against(String promptId, int liveVersion);
}
