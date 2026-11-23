package app.lightmove.api.promptregistry.model;

/** A check the version did worse on than the one serving, beyond the prompt's tolerance: both pass rates in percent. */
public record PromotionGateFailedCheck(String label, double candidate, double baseline) {}
