package app.uncava.api.enrichment.candidate.service;

import app.uncava.api.candidate.constant.BackgroundField;
import app.uncava.api.candidate.model.CandidateDossier;

/**
 * Which of the two background calls an executive still needs. The classifier is asked once: a reading it already
 * gave — a suggestion or Unknown — is not bought again.
 */
record BackgroundNeeds(boolean inferring, boolean classifying) {

    static BackgroundNeeds of(CandidateDossier dossier, boolean nationalityRead) {
        return new BackgroundNeeds(
                dossier.missingBackground().stream().anyMatch(CandidateAiEnricher.INFERRED::contains),
                dossier.missingBackground().contains(BackgroundField.NATIONALITY) && !nationalityRead);
    }

    boolean any() {
        return inferring || classifying;
    }
}
