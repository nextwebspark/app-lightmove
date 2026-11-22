package app.lightmove.api.outreach.service;

import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.CandidateEducationEntry;
import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.outreach.model.OpenerBrief;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The opener's Try it: an invented supply chain executive and an invented role. Nobody here exists. */
@Component
@RequiredArgsConstructor
class OutreachOpenerTrial implements PromptTrial {

    private static final CandidateDossier EXECUTIVE = new CandidateDossier(
            "Farid Okonkwo-Lindqvist", "Vice President, Supply Chain", "Quillmere Retail", "Jeddah", "Saudi Arabia",
            null,
            "Supply chain leader for a regional grocery retailer. Opened its automated distribution centre in 2024 "
                    + "and cut store stock-outs by a third.",
            List.of(new CandidateCareerEntry("Quillmere Retail", "Vice President, Supply Chain", "2020 - present",
                            "Jeddah"),
                    new CandidateCareerEntry("Quillmere Retail", "Director of Distribution", "2015 - 2020",
                            "Jeddah"),
                    new CandidateCareerEntry("Ostrava Freight Lines", "Network Planning Manager", "2008 - 2015",
                            "Gothenburg")),
            List.of(new CandidateEducationEntry("Chalmers University of Technology", "MSc Supply Chain Management",
                    "2006 - 2008")),
            List.of("Distribution networks", "Warehouse automation", "Demand planning"),
            List.of("English", "Swedish", "Arabic"),
            Set.of());

    private static final OpenerBrief BRIEF =
            new OpenerBrief("Chief Operating Officer", "C-Suite", "Logistics", "Riyadh", "Saudi Arabia");

    private final OutreachOpenerDrafter drafter;

    @Override
    public String promptId() {
        return "outreach-opener";
    }

    @Override
    public String inputName() {
        return "Supply chain executive";
    }

    @Override
    public String inputDescription() {
        return "A fictional supply chain executive's profile and a fictional operations chief role in logistics. "
                + "No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return OutreachOpenerDrafter.USER_MESSAGE;
    }

    @Override
    public void run() {
        drafter.draft(EXECUTIVE, BRIEF);
    }
}
