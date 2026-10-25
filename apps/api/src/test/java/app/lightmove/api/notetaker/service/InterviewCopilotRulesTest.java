package app.lightmove.api.notetaker.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.model.CallHeldFields;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.notetaker.constant.CoachState;
import app.lightmove.api.notetaker.constant.CoverageState;
import app.lightmove.api.notetaker.constant.InterviewQuestionSection;
import app.lightmove.api.notetaker.constant.InterviewQuestionSource;
import app.lightmove.api.notetaker.constant.NudgeStatus;
import app.lightmove.api.notetaker.constant.RecordGap;
import app.lightmove.api.notetaker.model.CoachNudge;
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
    @DisplayName("the coach moves a competency forward only, and never brings back a dismissed nudge")
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
                List.of("q1", "q9"),
                List.of(new CoachAnswer.Nudge("Ask for an example.", "No example"),
                        new CoachAnswer.Nudge("Two", null), new CoachAnswer.Nudge("Three", null),
                        new CoachAnswer.Nudge("Four", null))),
                ids, InterviewCoach.questionIdsOf(List.of(notice)), Set.of(RecordGap.NOTICE));

        assertThat(first.coverage().getFirst().state()).isEqualTo(CoverageState.EVIDENCED);
        assertThat(first.coverage().getFirst().at()).isEqualTo("05:33");
        assertThat(first.gaps()).extracting(gap -> gap.gap()).containsExactly(RecordGap.NOTICE);
        assertThat(first.asked()).containsExactly("plan-q");
        assertThat(first.nudges()).hasSize(InterviewCoach.MOST_NUDGES);
        assertThat(first.coach()).isEqualTo(CoachState.RUNNING);

        CoachNudge dismissed = first.nudges().getFirst().as(NudgeStatus.DISMISSED);
        LiveCoaching steered = first.withNudges(List.of(dismissed));
        LiveCoaching second = InterviewCoach.merged(steered, new CoachAnswer(null,
                List.of(new CoachAnswer.Coverage("c1", "TOUCHED", "Later words.", "20:00")), List.of(), List.of(),
                List.of(new CoachAnswer.Nudge("ask for an EXAMPLE", null), new CoachAnswer.Nudge("New one.", null))),
                ids, Map.of(), Set.of(RecordGap.NOTICE));

        assertThat(second.coverage().getFirst().state()).isEqualTo(CoverageState.EVIDENCED);
        assertThat(second.coverage().getFirst().quote()).isEqualTo("We refinanced 1.2 billion.");
        assertThat(second.nudges()).extracting(CoachNudge::text, CoachNudge::status).containsExactly(
                org.assertj.core.groups.Tuple.tuple("Ask for an example.", NudgeStatus.DISMISSED),
                org.assertj.core.groups.Tuple.tuple("New one.", NudgeStatus.SHOWN));
    }

    @Test
    @DisplayName("what a consultant did during a run outlives the run's reading")
    void consultantWinsOverARun() {
        CoachNudge shown = new CoachNudge("n1", "Ask about the team.", null, NudgeStatus.SHOWN);
        LiveCoaching reading = new LiveCoaching(List.of(), List.of(), List.of("q-a"), List.of(shown),
                CoachState.RUNNING);
        LiveCoaching meanwhile = new LiveCoaching(List.of(), List.of(), List.of("q-b"),
                List.of(shown.as(NudgeStatus.USED)), CoachState.RUNNING);

        LiveCoaching kept = InterviewCoachWorker.reconciled(reading, meanwhile);

        assertThat(kept.nudges()).extracting(CoachNudge::status).containsExactly(NudgeStatus.USED);
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
