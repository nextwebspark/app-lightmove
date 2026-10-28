package app.lightmove.api.notetaker.service;

import app.lightmove.api.TestLlmCallPolicy;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.enrichment.candidate.model.ExecutiveReadingText;
import app.lightmove.api.notetaker.constant.CoverageState;
import app.lightmove.api.notetaker.constant.RecordGap;
import app.lightmove.api.notetaker.model.CompetencyCoverage;
import app.lightmove.api.notetaker.model.GapHeard;
import app.lightmove.api.notetaker.model.InterviewQuestion;
import app.lightmove.api.notetaker.model.LiveCoaching;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.model.BriefCompetencies;
import app.lightmove.api.position.model.BriefCompetency;
import app.lightmove.api.position.model.BriefCriterion;
import com.google.genai.Client;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatModel;
import org.springframework.ai.google.genai.GoogleGenAiChatOptions;

/**
 * The interview copilot's two prompts, through the shipped {@link InterviewPlanner} and {@link InterviewCoach}, against
 * the real model on a synthetic brief, executive and call. Excluded from {@code ./mvnw test}; run it with Application
 * Default Credentials:
 *
 * <pre>
 * LIGHTMOVE_EVAL_RUNS=3 ./mvnw test -Dgroups=eval -DexcludedGroups= -Dtest=InterviewCopilotEval
 * </pre>
 *
 * <p>The plan is scored on whether every question reads as one line; the coach on a call that plants the traps it
 * must not fall into — a teammate praising the executive, a "we" with no part of their own, a hypothetical, a passing
 * mention, a question nobody answered, a fact said late in a long answer. Counts are appended to
 * {@code docs/eval/interview-copilot-eval.md}.
 */
@Tag("eval")
class InterviewCopilotEval {

    private static final Path REPORT = Path.of("..", "..", "docs", "eval", "interview-copilot-eval.md");
    private static final Pattern SECOND_SENTENCE = Pattern.compile("[.?!]\\s+\\S");
    private static final Instant CALL_START = Instant.parse("2026-10-27T10:00:00Z");

    private static final BriefCompetency TREASURY = new BriefCompetency(CompetencyPanel.TECHNICAL,
            "Treasury & capital structure", "Refinancing, banking relationships and the cost of debt", 30);
    private static final BriefCompetency TEAMS = new BriefCompetency(CompetencyPanel.BEHAVIOURAL,
            "Building finance teams", "Hiring and developing a finance leadership bench", 25);
    private static final BriefCompetency DIGITAL = new BriefCompetency(CompetencyPanel.TECHNICAL,
            "Digital finance transformation", "Leading ERP and reporting change programmes", 25);
    private static final BriefCompetency BOARD = new BriefCompetency(CompetencyPanel.BEHAVIOURAL,
            "Board & investor relations", "Presenting to the board and to investors", 20);

    private static final AssessmentBrief BRIEF = new AssessmentBrief("Chief Financial Officer", Seniority.C_SUITE,
            "Finance", "Dubai", "United Arab Emirates",
            "A listed ports group needs a CFO to refinance its debt and modernise finance.",
            List.of("Own the group's capital structure", "Lead the finance function of 120 people"),
            "Debt maturities in 2027", List.of("Refinance before 2027"),
            List.of(new BriefCriterion("Has led a refinancing above USD 500m", CriterionMode.REQUIRED)),
            new BriefCompetencies(List.of(TREASURY, TEAMS, DIGITAL, BOARD), 60));

    private static final ExecutiveReadingText EXECUTIVE = new ExecutiveReadingText("""
            Rania Khoury — Group Finance Director, Gulf Ports Holding (2019–present), Dubai.
            Previously Treasurer, Levant Shipping (2012–2019). ACCA. MBA, INSEAD.
            About: Finance leader across ports and logistics; capital markets, treasury and finance teams.""",
            "- 2026-10-01 Call: wants a listed-company CFO seat; open to Dubai.", "none", 1, 0);

    private static final Set<RecordGap> MISSING = EnumSet.of(RecordGap.NOTICE, RecordGap.PACKAGE, RecordGap.LOCATION);

    @Test
    void evaluate() throws IOException {
        int runs = Integer.parseInt(envOr("LIGHTMOVE_EVAL_RUNS", "3"));
        StructuredPromptFactory prompts = TestLlmCallPolicy.promptsOver(vertexModel());
        InterviewPlanner planner = new InterviewPlanner(prompts);
        InterviewCoach coach = new InterviewCoach(prompts);

        PlanScore plan = new PlanScore();
        Map<String, int[]> coachHits = new LinkedHashMap<>();
        for (int run = 0; run < runs; run++) {
            plan.add(planner.draft(BRIEF, EXECUTIVE, Set.of(), EnumSet.of(RecordGap.NOTICE, RecordGap.PACKAGE,
                    RecordGap.MOTIVATION)));
            LiveCoaching reading = coach.coach(BRIEF, List.of(), MISSING, null, call(), CALL_START);
            System.out.println("coach run " + (run + 1) + ": " + reading);
            CoachExpectations.of(reading).forEach((check, held) ->
                    coachHits.computeIfAbsent(check, key -> new int[2])[held ? 0 : 1]++);
        }

        List<String> report = new ArrayList<>();
        report.add("");
        report.add("## " + LocalDate.now(ZoneOffset.UTC) + " — " + model() + ", " + runs + " runs");
        report.add("");
        report.add(("**Prepare questions** — %d questions: %d at most 90 characters, %d past 90, %d with a second "
                + "sentence, %d cut at the 120 limit, longest %d.")
                .formatted(plan.questions, plan.short_, plan.questions - plan.short_, plan.twoSentences, plan.clipped,
                        plan.longest));
        report.add("");
        report.add("**Live coach** — each check held / missed across runs:");
        report.add("");
        report.add("| Check | Held | Missed |");
        report.add("|---|---|---|");
        coachHits.forEach((check, counts) -> report.add("| %s | %d | %d |".formatted(check, counts[0], counts[1])));
        report.forEach(System.out::println);
        Files.writeString(REPORT, String.join("\n", report) + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE,
                StandardOpenOption.APPEND);
    }

    /** A CFO interview of about two minutes, each line timed from the call's start. */
    private static List<InterviewCoach.HeardLine> call() {
        return List.of(
                team(5, "Thanks for joining, Rania. I hear you are excellent at treasury and built a great team."),
                executive(20, "Thanks. At Gulf Ports in 2023 our bonds were due and rates had doubled."),
                executive(28, "I ran the refinancing myself, negotiated with four banks, and we closed USD 800 "
                        + "million that cut our interest cost by 1.2 points."),
                team(45, "How do you build a finance team?"),
                executive(50, "We always hire well, and the team is strong."),
                team(70, "What would you do with an ERP rollout that was failing?"),
                executive(75, "I would probably bring in a new partner and reset the plan."),
                team(90, "What notice period are you on, and what package would make a move worthwhile?"),
                executive(100, "It is a long story. My board asked me to stay through the audit, which wraps up in "
                        + "March, and after that I am on a three month notice period, which is standard here."),
                team(125, "Understood, thank you."));
    }

    private static InterviewCoach.HeardLine team(int second, String text) {
        return new InterviewCoach.HeardLine(CALL_START.plusSeconds(second), "Yara Haddad", text, true);
    }

    private static InterviewCoach.HeardLine executive(int second, String text) {
        return new InterviewCoach.HeardLine(CALL_START.plusSeconds(second), "Rania Khoury", text, false);
    }

    /** What a careful reader of {@link #call()} finds, check by check. */
    private static final class CoachExpectations {

        static Map<String, Boolean> of(LiveCoaching reading) {
            Map<String, Boolean> checks = new LinkedHashMap<>();
            checks.put("Treasury evidenced (a situation, their action, a result, across two lines)",
                    stateOf(reading, TREASURY) == CoverageState.EVIDENCED);
            checks.put("Teams not evidenced (\"we always hire well\", plus a teammate's praise)",
                    stateOf(reading, TEAMS) != CoverageState.EVIDENCED);
            checks.put("Digital not evidenced (a hypothetical \"I would\")",
                    stateOf(reading, DIGITAL) != CoverageState.EVIDENCED);
            checks.put("Board not reached (a passing mention)", stateOf(reading, BOARD) == CoverageState.NOT_YET);
            checks.put("Every quote is the executive's own words", reading.coverage().stream()
                    .filter(line -> line.quote() != null)
                    .noneMatch(line -> line.quote().contains("excellent") || line.quote().contains("great team")));
            checks.put("Every reached competency carries its line's time", reading.coverage().stream()
                    .filter(line -> line.state() != CoverageState.NOT_YET)
                    .allMatch(line -> line.at() != null));
            checks.put("Notice heard (said late in a long answer)", heard(reading, RecordGap.NOTICE));
            checks.put("Package not heard (asked, never answered)", !heard(reading, RecordGap.PACKAGE));
            checks.put("Location not heard (never raised)", !heard(reading, RecordGap.LOCATION));
            return checks;
        }

        private static CoverageState stateOf(LiveCoaching reading, BriefCompetency competency) {
            return reading.coverage().stream()
                    .filter(line -> line.competency().equals(competency.name()))
                    .map(CompetencyCoverage::state)
                    .findFirst()
                    .orElse(CoverageState.NOT_YET);
        }

        private static boolean heard(LiveCoaching reading, RecordGap gap) {
            return reading.gaps().stream().map(GapHeard::gap).anyMatch(gap::equals);
        }
    }

    private static final class PlanScore {
        int questions;
        int short_;
        int twoSentences;
        int clipped;
        int longest;

        void add(List<InterviewQuestion> plan) {
            for (InterviewQuestion question : plan) {
                String text = question.text();
                System.out.println("  [" + question.section() + "] (" + text.length() + ") " + text);
                questions++;
                longest = Math.max(longest, text.length());
                if (text.length() <= 90) {
                    short_++;
                }
                if (SECOND_SENTENCE.matcher(text).find()) {
                    twoSentences++;
                }
                if (!text.endsWith("?") && !text.endsWith(".")) {
                    clipped++;
                }
            }
        }
    }

    private static ChatModel vertexModel() {
        Client client = Client.builder()
                .vertexAI(true)
                .project(envOr("GOOGLE_CLOUD_PROJECT", "hak-talent-mapping"))
                .location(envOr("GOOGLE_CLOUD_LOCATION", "global"))
                .build();
        return GoogleGenAiChatModel.builder()
                .genAiClient(client)
                .options(GoogleGenAiChatOptions.builder().model(model()).maxOutputTokens(8192).build())
                .build();
    }

    private static String model() {
        return envOr("LIGHTMOVE_EVAL_MODEL", "gemini-3.5-flash");
    }

    private static String envOr(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
