package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.model.CallHeldFields;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.notetaker.constant.CoachState;
import app.lightmove.api.notetaker.constant.CoverageState;
import app.lightmove.api.notetaker.constant.InterviewQuestionSection;
import app.lightmove.api.notetaker.constant.InterviewQuestionSource;
import app.lightmove.api.notetaker.constant.RecordGap;
import app.lightmove.api.notetaker.model.InterviewQuestion;
import app.lightmove.api.notetaker.model.LiveCoaching;
import app.lightmove.api.notetaker.model.RecallLiveUtterance;
import app.lightmove.api.notetaker.service.InterviewCoach.CoachAnswer;
import app.lightmove.api.notetaker.service.InterviewPlanner.PlanAnswer;
import app.lightmove.api.position.model.BriefCompetency;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class InterviewCopilotRulesTest {

    private static final BriefCompetency TREASURY = new BriefCompetency(CompetencyPanel.TECHNICAL,
            "Treasury & capital structure", null, 20);
    private static final BriefCompetency TEAMS = new BriefCompetency(CompetencyPanel.BEHAVIOURAL,
            "Building finance teams", null, 15);

    @Test
    @DisplayName("a plan keeps only questions it can place: a known competency, a listed gap, two clarifiers")
    void planIsHeldToTheBrief() {
        List<InterviewQuestion> kept = InterviewPlanner.questionsOf(new PlanAnswer(null,
                List.of(new PlanAnswer.Fit("C1", "  Tell me   about a raise. ", "A named deal"),
                        new PlanAnswer.Fit("c2", "", null),
                        new PlanAnswer.Fit("c7", "Unknown competency.", null)),
                List.of(new PlanAnswer.Gap("notice", "What notice would you need?"),
                        new PlanAnswer.Gap("PACKAGE", "Not listed as missing.")),
                List.of(new PlanAnswer.Clarify("One?", "why"), new PlanAnswer.Clarify("Two?", null),
                        new PlanAnswer.Clarify("Three?", null))),
                competencies(), EnumSet.of(RecordGap.NOTICE));

        assertThat(kept).extracting(InterviewQuestion::section).containsExactly(InterviewQuestionSection.FIT,
                InterviewQuestionSection.GAP, InterviewQuestionSection.CLARIFY, InterviewQuestionSection.CLARIFY);
        assertThat(kept.getFirst().text()).isEqualTo("Tell me about a raise.");
        assertThat(kept.getFirst().competency()).isEqualTo(TREASURY.name());
        assertThat(kept).allSatisfy(question -> assertThat(question.source()).isEqualTo(InterviewQuestionSource.AI));
    }

    @Test
    @DisplayName("a drafted question is held to one line, cut at a word")
    void draftedQuestionIsOneLine() {
        String longQuestion = "Walk me through the refinancing you led, who you negotiated with and what it changed "
                + "for the group's cost of debt over the following three years.";

        List<InterviewQuestion> kept = InterviewPlanner.questionsOf(new PlanAnswer(null,
                List.of(new PlanAnswer.Fit("c1", longQuestion, "A named deal, their own part, the result, and more")),
                List.of(), List.of()), competencies(), EnumSet.noneOf(RecordGap.class));

        assertThat(kept.getFirst().text()).hasSizeLessThanOrEqualTo(InterviewPlanner.LONGEST_DRAFTED_QUESTION)
                .isNotEqualTo(longQuestion).satisfies(text -> assertThat(longQuestion).startsWith(text));
        assertThat(kept.getFirst().text()).doesNotEndWith(" ").matches(".*\\w$");
    }

    @Test
    @DisplayName("only an empty field is a gap, and expected package goes with an empty motivation")
    void gapsAreOnlyWhatIsEmpty() {
        UUID tag = UUID.randomUUID();
        assertThat(InterviewPlanService.missingOf(new CallHeldFields(null, null, null, null, null, null, null, null,
                null, tag, false))).containsExactly(RecordGap.NOTICE, RecordGap.PACKAGE, RecordGap.LOCATION,
                RecordGap.RELOCATION, RecordGap.MOTIVATION, RecordGap.EXPECTED_PACKAGE);
        assertThat(InterviewPlanService.missingOf(new CallHeldFields("3 months", "AED", 1L, null, null, null, null,
                "United Arab Emirates", "Wants a group remit.", tag, true))).isEmpty();
    }

    @Test
    @DisplayName("the coach moves a competency forward only and keeps what it heard")
    void coachReadingOnlyMovesForward() {
        Map<String, BriefCompetency> ids = competencies();
        LiveCoaching start = InterviewCoach.withEveryCompetency(null, ids);
        assertThat(start.coverage()).extracting(line -> line.state()).containsOnly(CoverageState.NOT_YET);
        InterviewQuestion notice = new InterviewQuestion("plan-q", InterviewQuestionSection.GAP, null, null,
                RecordGap.NOTICE, "Notice?", null, null, true, InterviewQuestionSource.AI);

        LiveCoaching first = InterviewCoach.merged(start, new CoachAnswer(null,
                List.of(new CoachAnswer.Coverage("c1", "evidenced", "We refinanced 1.2 billion.", "05:33"),
                        new CoachAnswer.Coverage("c9", "EVIDENCED", "x", "00:01")),
                List.of(new CoachAnswer.GapHeard("NOTICE", "09:12"), new CoachAnswer.GapHeard("PACKAGE", "10:00")),
                List.of("q1", "q9")),
                ids, InterviewCoach.questionIdsOf(List.of(notice)), Set.of(RecordGap.NOTICE));

        assertThat(first.coverage().getFirst().state()).isEqualTo(CoverageState.EVIDENCED);
        assertThat(first.coverage().getFirst().at()).isEqualTo("05:33");
        assertThat(first.gaps()).extracting(gap -> gap.gap()).containsExactly(RecordGap.NOTICE);
        assertThat(first.asked()).containsExactly("plan-q");
        assertThat(first.coach()).isEqualTo(CoachState.RUNNING);

        LiveCoaching second = InterviewCoach.merged(first, new CoachAnswer(null,
                List.of(new CoachAnswer.Coverage("c1", "TOUCHED", "Later words.", "20:00")), List.of(), List.of()),
                ids, Map.of(), Set.of(RecordGap.NOTICE));

        assertThat(second.coverage().getFirst().state()).isEqualTo(CoverageState.EVIDENCED);
        assertThat(second.coverage().getFirst().quote()).isEqualTo("We refinanced 1.2 billion.");
        assertThat(second.gaps()).extracting(gap -> gap.gap()).containsExactly(RecordGap.NOTICE);
    }

    @Test
    @DisplayName("a competency moves only on the executive's own words, joined across the lines of one answer")
    void coverageIsHeldToWhatTheExecutiveSaid() {
        Instant start = Instant.parse("2026-10-27T10:00:00Z");
        List<InterviewCoach.HeardLine> lines = List.of(
                new InterviewCoach.HeardLine(start.plusSeconds(10), "Yara", "Tell me about the refinancing.", true),
                new InterviewCoach.HeardLine(start.plusSeconds(20), "Priya", "We refinanced 1.2 billion in 2024,", false),
                new InterviewCoach.HeardLine(start.plusSeconds(24), "Priya", "and I led the bank talks myself.", false),
                new InterviewCoach.HeardLine(start.plusSeconds(30), "Yara", "Impressive, you clearly built the team.",
                        true));

        List<CoachAnswer.Coverage> kept = InterviewCoach.verifiedCoverage(List.of(
                new CoachAnswer.Coverage("c1", "EVIDENCED", "in 2024, and I led the bank talks", "00:20"),
                new CoachAnswer.Coverage("c2", "EVIDENCED", "you clearly built the team", "00:30"),
                new CoachAnswer.Coverage("c2", "TOUCHED", "myself. Impressive", "00:24"),
                new CoachAnswer.Coverage("c2", "TOUCHED", "Never said this", "00:40"),
                new CoachAnswer.Coverage("c2", "TOUCHED", null, null)), lines);

        assertThat(kept).extracting(CoachAnswer.Coverage::id).containsExactly("c1");
    }

    @Test
    @DisplayName("a gap is heard only at a time inside one of the executive's lines, never without a time")
    void gapsAreHeldToWhenTheExecutiveSpoke() {
        Instant start = Instant.parse("2026-10-27T10:00:00Z");
        List<InterviewCoach.HeardLine> lines = List.of(
                new InterviewCoach.HeardLine(start.plusSeconds(10), "Yara", "What notice do you have?", true),
                new InterviewCoach.HeardLine(start.plusSeconds(20), "Priya",
                        "A long answer about my team, the board, and three months notice at the end.", false),
                new InterviewCoach.HeardLine(start.plusSeconds(80), "Yara", "And the package?", true));

        List<CoachAnswer.GapHeard> kept = InterviewCoach.verifiedGaps(List.of(
                new CoachAnswer.GapHeard("NOTICE", "[01:05]"),
                new CoachAnswer.GapHeard("PACKAGE", "01:30"),
                new CoachAnswer.GapHeard("LOCATION", "00:12"),
                new CoachAnswer.GapHeard("MOTIVATION", null)), lines, start);

        assertThat(kept).extracting(CoachAnswer.GapHeard::gap).containsExactly("NOTICE");
    }

    @Test
    @DisplayName("a voice is the executive's only when its name is theirs, never on one shared word")
    void voiceIsNamedAsTheExecutive() {
        assertThat(TeamVoices.isNamedAs("Hakan Demir", "Hakan Demir", true)).isTrue();
        assertThat(TeamVoices.isNamedAs("Hakan Demir", "  hakan ", true)).isTrue();
        assertThat(TeamVoices.isNamedAs("Hakan Demir", "Dr. Hakan Demir", true)).isTrue();
        assertThat(TeamVoices.isNamedAs("Hakan Demir", "Alexander Petrov", true)).isFalse();
        assertThat(TeamVoices.isNamedAs("Mohammed Ali", "Mohammed Hassan", true)).isFalse();
        assertThat(TeamVoices.isNamedAs("Hakan Demir", "Hakan Demir", false)).isFalse();
        assertThat(TeamVoices.isNamedAs(null, "Hakan Demir", true)).isFalse();
    }

    @Test
    @DisplayName("an outsider on the call is not the executive; where nobody is named as them, every other voice is")
    void executiveVoicesLeaveOutsidersOut() {
        List<TeamVoices.SpeakerVoice> others = List.of(new TeamVoices.SpeakerVoice("Hakan Demir", true),
                new TeamVoices.SpeakerVoice("Alexander Petrov", true));
        assertThat(TeamVoices.executiveVoicesOf("Hakan Demir", others)).containsExactly("Hakan Demir");

        List<TeamVoices.SpeakerVoice> unnamed = List.of(new TeamVoices.SpeakerVoice("Galaxy S24", true),
                new TeamVoices.SpeakerVoice("Speaker 7", false));
        assertThat(TeamVoices.executiveVoicesOf("Hakan Demir", unnamed))
                .containsExactlyInAnyOrder("Galaxy S24", "Speaker 7");
    }

    @Test
    @DisplayName("an outsider's words are never the executive's evidence, and the coach reads them marked")
    void outsiderIsNeverEvidence() {
        Instant start = Instant.parse("2026-10-27T10:00:00Z");
        List<InterviewCoach.HeardLine> lines = List.of(
                new InterviewCoach.HeardLine(start.plusSeconds(10), "Yara", "What notice do you have?", true, false),
                new InterviewCoach.HeardLine(start.plusSeconds(20), "Alexander",
                        "I led the refinancing and my notice is three months.", false, false),
                new InterviewCoach.HeardLine(start.plusSeconds(40), "Hakan", "Happy to be here.", false, true));

        assertThat(InterviewCoach.verifiedCoverage(List.of(
                new CoachAnswer.Coverage("c1", "EVIDENCED", "I led the refinancing", "00:20")), lines)).isEmpty();
        assertThat(InterviewCoach.verifiedGaps(List.of(new CoachAnswer.GapHeard("NOTICE", "00:22")), lines, start))
                .isEmpty();
        assertThat(InterviewCoach.transcriptText(lines, start))
                .contains("Yara (your team): What notice")
                .contains("Alexander (not the executive): I led")
                .contains("Hakan: Happy");
    }

    @Test
    @DisplayName("a question ticked during a run stays ticked")
    void consultantWinsOverARun() {
        LiveCoaching reading = new LiveCoaching(List.of(), List.of(), List.of("q-a"), CoachState.RUNNING);
        LiveCoaching meanwhile = new LiveCoaching(List.of(), List.of(), List.of("q-b"), CoachState.RUNNING);

        LiveCoaching kept = InterviewCoachWorker.reconciled(reading, meanwhile);

        assertThat(kept.asked()).containsExactlyInAnyOrder("q-a", "q-b");
    }

    @Test
    @DisplayName("Recall's real-time words read as one utterance, keyed so a retried delivery is the same line")
    void liveDeliveriesAreRead() {
        String body = """
                {"event":"transcript.data","data":{"bot":{"id":"b1"},"data":{
                  "words":[{"text":"Three","start_timestamp":{"relative":12.5},"end_timestamp":{"relative":12.9}},
                           {"text":"months.","start_timestamp":{"relative":13.0},"end_timestamp":{"relative":14.5}}],
                  "participant":{"id":7,"name":"Priya Raman"}}}}""";
        List<RecallLiveUtterance> read = RecallBotClient.liveUtterancesIn(body.getBytes(StandardCharsets.UTF_8));

        assertThat(read).hasSize(1);
        RecallLiveUtterance utterance = read.getFirst();
        assertThat(utterance.text()).isEqualTo("Three months.");
        assertThat(utterance.partial()).isFalse();
        assertThat(NotetakerLiveFeed.utteranceKeyOf(utterance)).isEqualTo("7:12.500");
        Instant received = Instant.parse("2026-10-23T10:00:00Z");
        assertThat(NotetakerLiveFeed.saidAtOf(utterance, received)).isEqualTo(received.minusSeconds(2));
        assertThat(RecallBotClient.liveUtterancesIn("{\"event\":\"bot.done\"}".getBytes(StandardCharsets.UTF_8)))
                .isEmpty();
        assertThat(RecallBotClient.liveUtterancesIn("nope".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    @DisplayName("only a live bot streams, in Recall's low-latency mode, to our endpoint")
    void liveBotConfig() {
        assertThat(RecallBotClient.recordingConfigOf(null).toString()).contains("prioritize_accuracy")
                .doesNotContain("realtime_endpoints");
        assertThat(RecallBotClient.recordingConfigOf(URI.create("https://app.example/api/v1/notetaker/webhooks/"
                + "recall/live")).toString()).contains("prioritize_low_latency", "realtime_endpoints",
                "transcript.data", "transcript.partial_data", "https://app.example/api/v1/notetaker/webhooks/recall/live");
    }

    private static Map<String, BriefCompetency> competencies() {
        Map<String, BriefCompetency> byId = new LinkedHashMap<>();
        byId.put("c1", TREASURY);
        byId.put("c2", TEAMS);
        return byId;
    }
}
