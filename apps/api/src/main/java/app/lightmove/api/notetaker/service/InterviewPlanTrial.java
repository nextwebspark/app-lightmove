package app.lightmove.api.notetaker.service;

import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.llm.service.PromptTrial;
import app.lightmove.api.enrichment.candidate.model.ExecutiveReadingText;
import app.lightmove.api.notetaker.constant.RecordGap;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.model.BriefCompetencies;
import app.lightmove.api.position.model.BriefCompetency;
import app.lightmove.api.position.model.BriefCriterion;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The interview plan's Try it: an invented operations chief and an invented brief. Nobody here exists. */
@Component
@RequiredArgsConstructor
class InterviewPlanTrial implements PromptTrial {

    static final String NETWORK_OPERATIONS = "Network operations";

    /** The invented brief every Notetaker trial reads. */
    static final AssessmentBrief BRIEF = new AssessmentBrief(
            "Chief Operating Officer", Seniority.C_SUITE, "Operations", "Jeddah", "Saudi Arabia",
            "Runs operations for a family-owned cold-chain distributor opening its third regional hub.",
            List.of("Run the delivery network and the three hubs as one operation",
                    "Bring warehousing and transport under a single plan",
                    "Build the operations team the third hub needs"),
            "The group is opening a third hub and its transport and warehousing have never reported to one person.",
            List.of("Regional expansion", "Service reliability"),
            List.of(new BriefCriterion("Has run a multi-site logistics network", CriterionMode.REQUIRED),
                    new BriefCriterion("Has worked in the Gulf", CriterionMode.PREFERRED)),
            new BriefCompetencies(List.of(
                    new BriefCompetency(CompetencyPanel.TECHNICAL, NETWORK_OPERATIONS,
                            "Runs transport and route planning across several sites", 60),
                    new BriefCompetency(CompetencyPanel.TECHNICAL, "Warehousing",
                            "Has owned temperature-controlled storage and its losses", 40),
                    new BriefCompetency(CompetencyPanel.BEHAVIOURAL, "Leading change",
                            "Takes a team through a change it did not ask for", 50),
                    new BriefCompetency(CompetencyPanel.BEHAVIOURAL, "Owner relationships",
                            "Holds the confidence of a founding family", 50)), 60));

    private static final ExecutiveReadingText EXECUTIVE = new ExecutiveReadingText("""
            Name: Tomas Ellery
            Current title: Operations Director
            Current employer: Brindlemoor Freight
            Location: Dubai, United Arab Emirates
            LinkedIn profile: not stated
            About: Runs the delivery network of a regional freight carrier. Rebuilt its route planning in 2024.

            Career history, most recent first:
            - Operations Director at Brindlemoor Freight (2022 - present)
            - Head of Transport at Brindlemoor Freight (2018 - 2022)
            - Depot Manager at Quillhaven Haulage (2012 - 2018)

            Education:
            - University of Leeds, BEng Mechanical Engineering (2007 - 2010)

            Skills: Route planning, Fleet management, Depot operations
            Languages: English""", """
            - 2026-10-12, Researcher: A former colleague says he took on-time delivery from 81 to 94 per cent in \
            two years, and that warehousing never reported to him.
            - 2026-10-05, Researcher: Left Quillhaven Haulage after six years; the reason is not on file.""", """
            Conference talk, May 2025 (given by the executive): Describes moving four depots onto one planning \
            system and the drivers' resistance to it.""", 2, 1);

    private static final Set<String> EVIDENCED = Set.of(InterviewPlanner.keyOf(NETWORK_OPERATIONS));

    private static final Set<RecordGap> GAPS = EnumSet.of(RecordGap.NOTICE, RecordGap.PACKAGE, RecordGap.RELOCATION);

    private final InterviewPlanner planner;

    @Override
    public String promptId() {
        return "interview-plan";
    }

    @Override
    public String inputName() {
        return "One competency evidenced";
    }

    @Override
    public String inputDescription() {
        return "A fictional operations chief with a profile, two notes and a conference talk, read against a brief "
                + "with one competency already evidenced and three facts missing. No workspace's data is used.";
    }

    @Override
    public String userMessageTemplate() {
        return InterviewPlanner.USER_MESSAGE;
    }

    @Override
    public void run() {
        planner.draft(BRIEF, EXECUTIVE, EVIDENCED, GAPS);
    }
}
