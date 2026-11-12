package app.lightmove.api.enrichment.candidate.service;

import static app.lightmove.api.enrichment.candidate.service.CandidateAiEnricherTest.dossier;
import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.BackgroundField;
import app.lightmove.api.workspace.model.WorkspaceAiControls;
import java.util.EnumSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class BackgroundNeedsTest {

    private static final EnumSet<BackgroundField> ALL_MISSING = EnumSet.allOf(BackgroundField.class);

    @Test
    @DisplayName("with every switch on, both calls are needed for a person missing everything")
    void everythingOnAsksBoth() {
        BackgroundNeeds needs = BackgroundNeeds.of(dossier(ALL_MISSING), false, WorkspaceAiControls.allOn());

        assertThat(needs.inferring()).containsExactly(
                BackgroundField.GENDER, BackgroundField.YEARS_EXPERIENCE, BackgroundField.SENIORITY);
        assertThat(needs.classifying()).isTrue();
    }

    @Test
    @DisplayName("gender off leaves years and seniority to infer, and nationality off asks no classifier (#922)")
    void switchedOffInferencesAreNotAsked() {
        BackgroundNeeds needs = BackgroundNeeds.of(dossier(ALL_MISSING), false,
                new WorkspaceAiControls(false, false, true, true));

        assertThat(needs.inferring()).containsExactly(BackgroundField.YEARS_EXPERIENCE, BackgroundField.SENIORITY);
        assertThat(needs.classifying()).isFalse();
    }

    @Test
    @DisplayName("a person missing only gender needs nothing while gender is off")
    void onlyGenderMissingAndOffNeedsNothing() {
        BackgroundNeeds needs = BackgroundNeeds.of(dossier(EnumSet.of(BackgroundField.GENDER)), false,
                new WorkspaceAiControls(false, true, true, true));

        assertThat(needs.any()).isFalse();
    }
}
