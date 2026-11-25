package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.core.llm.service.PromptTrial;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The background proposal's Try it: an invented operations chief missing all three fields. Nobody here exists. */
@Component
@RequiredArgsConstructor
class CandidateAiEnrichTrial implements PromptTrial {

    private static final CandidateDossier EXECUTIVE = new CandidateDossier(
            "Ingrid Solheim", "Chief Operating Officer", "Brackenfield Foods", "Rotterdam", "Netherlands", null,
            "Runs manufacturing and supply chain for a family-owned food group with nine plants. Joined as a "
                    + "graduate engineer and has led operations since 2019.",
            List.of(new CandidateCareerEntry("Brackenfield Foods", "Chief Operating Officer", "2019 - present",
                            "Rotterdam"),
                    new CandidateCareerEntry("Brackenfield Foods", "Plant Director", "2012 - 2019", "Eindhoven"),
                    new CandidateCareerEntry("Tarn Valley Dairies", "Production Engineer", "2004 - 2012",
                            "Bergen")),
            List.of(new CandidateEducationEntry("Norwegian University of Science and Technology",
                    "MSc Industrial Engineering", "1999 - 2004")),
            List.of("Lean manufacturing", "Supply chain", "Plant commissioning"),
            List.of("Norwegian", "Dutch", "English"),
            CandidateAiEnricher.INFERRED);

    private static final String NOTES = """
            - 2026-10-09, Researcher: She reports to the chief executive and sits on the executive committee. \
            She said she has run plants for most of her twenty-two years in the industry.""";

    private static final String DOCUMENTS = """
            Speaker biography, 2025: Ingrid joined Brackenfield in 2012 and was appointed to its executive \
            committee in 2019. She leads 2,400 people across nine sites.""";

    private final CandidateAiEnricher enricher;

    @Override
    public String promptId() {
        return "candidate-ai-enrich";
    }

    @Override
    public String inputName() {
        return "Three fields missing";
    }

    @Override
    public String inputDescription() {
        return "A fictional operations chief with a profile, one note and a speaker biography, missing gender, "
                + "years of experience and seniority. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return CandidateAiEnricher.USER_MESSAGE;
    }

    @Override
    public void run() {
        enricher.infer(EXECUTIVE, new MaterialText(NOTES, DOCUMENTS, 1, 1));
    }
}
