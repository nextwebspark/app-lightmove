package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.workspace.model.WorkspaceAiControls;
import java.util.Collections;
import java.util.Set;

/**
 * Which of the two background calls an executive still needs, within what the workspace lets the AI infer. The
 * classifier is asked once: a reading it already gave — a suggestion or Unknown — is not bought again.
 */
record BackgroundNeeds(Set<BackgroundField> inferring, boolean classifying) {

    static BackgroundNeeds of(CandidateDossier dossier, boolean nationalityRead, WorkspaceAiControls controls) {
        Set<BackgroundField> missing = dossier.missingBackground();
        Set<BackgroundField> inferring = CandidateAiEnricher.askedOf(missing);
        if (!controls.inferGender()) {
            inferring.remove(BackgroundField.GENDER);
        }
        return new BackgroundNeeds(Collections.unmodifiableSet(inferring),
                controls.inferNationality() && missing.contains(BackgroundField.NATIONALITY) && !nationalityRead);
    }

    boolean any() {
        return !inferring.isEmpty() || classifying;
    }
}
