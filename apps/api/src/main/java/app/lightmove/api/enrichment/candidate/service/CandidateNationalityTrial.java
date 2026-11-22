package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.core.llm.service.PromptTrial;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The nationality reading's Try it: the synthetic evaluation set's location trap, syn-01. Nobody here exists. */
@Component
@RequiredArgsConstructor
class CandidateNationalityTrial implements PromptTrial {

    private static final CandidateDossier EXECUTIVE = new CandidateDossier(
            "Oliver Pembridge", "Group CFO", "Harbourline Holdings", "Dubai", "United Arab Emirates", null,
            "Finance leader in Gulf conglomerates for twelve years.",
            List.of(new CandidateCareerEntry("Harbourline Holdings", "Group CFO", "2013 – Present", "Dubai"),
                    new CandidateCareerEntry("Carrow & Finch", "Audit Senior", "2005 – 2009",
                            "Manchester, United Kingdom")),
            List.of(new CandidateEducationEntry("University of Leeds", "BSc Accounting", "2001 – 2004")),
            List.of(),
            List.of("English"),
            Set.of());

    private final CandidateNationalityClassifier classifier;

    @Override
    public String promptId() {
        return "candidate-nationality";
    }

    @Override
    public String inputName() {
        return "Location trap";
    }

    @Override
    public String inputDescription() {
        return "A fictional finance chief living in Dubai whose schooling and first job were in the United "
                + "Kingdom, with no notes or documents. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return CandidateNationalityClassifier.USER_MESSAGE;
    }

    @Override
    public void run() {
        classifier.classify(EXECUTIVE, MaterialText.NONE);
    }
}
