package app.lightmove.api.notetaker.service;

import app.lightmove.api.common.transcript.CallTranscriptText;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.ContactRedaction;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.notetaker.constant.CoachState;
import app.lightmove.api.notetaker.constant.CoverageState;
import app.lightmove.api.notetaker.constant.RecordGap;
import app.lightmove.api.notetaker.model.CompetencyCoverage;
import app.lightmove.api.notetaker.model.GapHeard;
import app.lightmove.api.notetaker.model.InterviewQuestion;
import app.lightmove.api.notetaker.model.LiveCoaching;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.model.BriefCompetency;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * One model call reading a live call so far, and the code that folds its answer into what the copilot already showed:
 * a competency only ever moves forward, a gap heard stays heard and a prepared question asked stays asked. A reading
 * counts only when the words it quotes are on an executive's line — never the team's. The model sees the brief, the plan and which facts are missing
 * — never a held value or a package figure — and the transcript with contacts taken out and the team's voices marked.
 */
@Component
class InterviewCoach {

    private static final String PROMPT_ID = "interview-coach";
    private static final String BLOCKED = "{\"guard\":\"" + BlockedAnswer.MARKER + "\"}";
    /** The start and the latest of a long call; what fell out of the middle is already in the reading so far. */
    static final int LONGEST_TRANSCRIPT = 40_000;
    private static final int LONGEST_QUOTE = 300;
    private static final long GAP_TIME_SLACK_SECONDS = 5;
    private static final String TEAM_MARK = " (your team)";
    private static final Pattern CALL_TIME = Pattern.compile("\\d{1,2}:\\d{2}(:\\d{2})?");

    private final StructuredPrompt prompt;

    InterviewCoach(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    /** A turn of the live call as the coach reads it: whether the voice is the team's is decided before it is sent. */
    record HeardLine(Instant at, String speaker, String text, boolean team) {}

    /** The guard stopped the call's words: the coach skips this stretch and goes on after it. */
    static final class CoachGuarded extends RuntimeException {
        CoachGuarded() {
            super("The prompt guard stopped the live call's transcript");
        }
    }

    /** Throws {@link CoachGuarded} when the guard stopped the call; a failed model call propagates as it came. */
    LiveCoaching coach(AssessmentBrief brief, List<InterviewQuestion> plan, Set<RecordGap> missing,
                       LiveCoaching before, List<HeardLine> lines, Instant callStart) {
        Map<String, BriefCompetency> competencyById = MeetingSummariser.idsOf(brief);
        Map<String, InterviewQuestion> questionById = questionIdsOf(plan);
        LiveCoaching start = withEveryCompetency(before, competencyById);
        CoachAnswer answer = prompt.ask(CoachAnswer.class, user -> user.text("""
                THE POSITION
                {position}

                PREPARED QUESTIONS (id: question)
                {plan}

                FACTS THE RECORD IS MISSING (gap name: what it is)
                {gaps}

                FOUND EARLIER IN THE CALL
                {found}

                THE TRANSCRIPT SO FAR (time into the call, speaker, words)
                {transcript}
                """)
                .param("position", MeetingSummariser.positionOf(brief, competencyById))
                .param("plan", questionById.isEmpty() ? "none" : questionById.entrySet().stream()
                        .map(entry -> "- %s: %s".formatted(entry.getKey(), entry.getValue().text()))
                        .collect(Collectors.joining("\n")))
                .param("gaps", missing.isEmpty() ? "none" : missing.stream()
                        .map(gap -> "- %s: %s".formatted(gap.name(), InterviewPlanner.GAP_WORDS.get(gap)))
                        .collect(Collectors.joining("\n")))
                .param("found", foundText(start, competencyById, questionById))
                .param("transcript", transcriptText(lines, callStart)));
        if (answer == null) {
            throw new IllegalStateException("The coach answered nothing");
        }
        if (BlockedAnswer.matches(answer.guard())) {
            throw new CoachGuarded();
        }
        return merged(start, verified(answer, lines, callStart), competencyById, questionById, missing);
    }

    static String transcriptText(List<HeardLine> lines, Instant callStart) {
        String text = lines.stream()
                .map(line -> "[%s] %s%s: %s".formatted(CallTranscriptText.elapsed(callStart, line.at()),
                        line.speaker(), line.team() ? TEAM_MARK : "", line.text()))
                .collect(Collectors.joining("\n"));
        return CallTranscriptText.keepStartAndEnd(ContactRedaction.redact(text), LONGEST_TRANSCRIPT);
    }

    /** q1, q2, … for the questions the consultant kept ticked, in the plan's order. */
    static Map<String, InterviewQuestion> questionIdsOf(List<InterviewQuestion> plan) {
        Map<String, InterviewQuestion> byId = new LinkedHashMap<>();
        for (InterviewQuestion question : plan) {
            if (question.included()) {
                byId.put("q" + (byId.size() + 1), question);
            }
        }
        return byId;
    }

    /** Every competency of the brief has a line, not yet reached until the call says otherwise. */
    static LiveCoaching withEveryCompetency(LiveCoaching before, Map<String, BriefCompetency> competencyById) {
        LiveCoaching held = before == null ? LiveCoaching.empty() : before;
        Map<String, CompetencyCoverage> byKey = new LinkedHashMap<>();
        for (BriefCompetency competency : competencyById.values()) {
            CompetencyCoverage previous = held.coverage().stream()
                    .filter(line -> keyOf(line.panel() == null ? null : line.panel().name(), line.competency())
                            .equals(keyOf(competency.panel().name(), competency.name())))
                    .findFirst()
                    .orElse(null);
            byKey.put(keyOf(competency.panel().name(), competency.name()), previous != null ? previous
                    : new CompetencyCoverage(competency.panel(), competency.name(), CoverageState.NOT_YET, null,
                    null));
        }
        return new LiveCoaching(List.copyOf(byKey.values()), held.gaps(), held.asked(), held.coach());
    }

    static LiveCoaching merged(LiveCoaching before, CoachAnswer answer, Map<String, BriefCompetency> competencyById,
                               Map<String, InterviewQuestion> questionById, Set<RecordGap> missing) {
        Map<String, CompetencyCoverage> coverage = new LinkedHashMap<>();
        for (CompetencyCoverage line : before.coverage()) {
            coverage.put(keyOf(line.panel() == null ? null : line.panel().name(), line.competency()), line);
        }
        for (CoachAnswer.Coverage moved : answer.coverage() == null ? List.<CoachAnswer.Coverage>of()
                : answer.coverage()) {
            BriefCompetency competency = moved.id() == null ? null
                    : competencyById.get(moved.id().strip().toLowerCase(Locale.ROOT));
            CoverageState state = stateOf(moved.state());
            if (competency == null || state == null) {
                continue;
            }
            String key = keyOf(competency.panel().name(), competency.name());
            CompetencyCoverage held = coverage.get(key);
            CoverageState next = held.state().atLeast(state);
            boolean rose = next != held.state();
            String quote = InterviewPlanner.clip(moved.quote(), LONGEST_QUOTE);
            // The words that moved it furthest stay on the chip; a repeat at the same state keeps the first.
            boolean takesQuote = quote != null && (rose || held.quote() == null);
            coverage.put(key, new CompetencyCoverage(held.panel(), held.competency(), next,
                    takesQuote ? quote : held.quote(), takesQuote ? timeOf(moved.at()) : held.at()));
        }
        Map<RecordGap, GapHeard> gaps = new LinkedHashMap<>();
        before.gaps().forEach(gap -> gaps.put(gap.gap(), gap));
        for (CoachAnswer.GapHeard heard : answer.gapsHeard() == null ? List.<CoachAnswer.GapHeard>of()
                : answer.gapsHeard()) {
            RecordGap gap = InterviewPlanner.gapOf(heard.gap());
            if (gap != null && missing.contains(gap)) {
                gaps.putIfAbsent(gap, new GapHeard(gap, timeOf(heard.at())));
            }
        }
        Set<String> asked = new LinkedHashSet<>(before.asked());
        for (String shortId : answer.asked() == null ? List.<String>of() : answer.asked()) {
            InterviewQuestion question = shortId == null ? null
                    : questionById.get(shortId.strip().toLowerCase(Locale.ROOT));
            if (question != null) {
                asked.add(question.id());
            }
        }
        return new LiveCoaching(List.copyOf(coverage.values()), List.copyOf(gaps.values()), List.copyOf(asked),
                CoachState.RUNNING);
    }

    private static String foundText(LiveCoaching start, Map<String, BriefCompetency> competencyById,
                                    Map<String, InterviewQuestion> questionById) {
        List<String> lines = new ArrayList<>();
        competencyById.forEach((id, competency) -> start.coverage().stream()
                .filter(line -> line.competency().equals(competency.name()) && line.state() != CoverageState.NOT_YET)
                .findFirst()
                .ifPresent(line -> lines.add("- competency %s: %s".formatted(id, line.state().name()))));
        start.gaps().forEach(gap -> lines.add("- gap %s: heard".formatted(gap.gap().name())));
        questionById.forEach((id, question) -> {
            if (start.asked().contains(question.id())) {
                lines.add("- question %s: asked".formatted(id));
            }
        });
        return lines.isEmpty() ? "nothing yet" : String.join("\n", lines);
    }

    private static CoverageState stateOf(String state) {
        if (state == null) {
            return null;
        }
        return switch (state.strip().toUpperCase(Locale.ROOT)) {
            case "TOUCHED" -> CoverageState.TOUCHED;
            case "EVIDENCED" -> CoverageState.EVIDENCED;
            default -> null;
        };
    }

    private static String keyOf(String panel, String competency) {
        return Objects.toString(panel, "") + "|" + InterviewPlanner.keyOf(competency);
    }

    /**
     * Keeps a reading only where the call backs it: a competency moves only on words quoted from an executive's line,
     * and a gap is heard only where an executive was speaking. The team's voice raises nothing, whatever the model says.
     */
    static CoachAnswer verified(CoachAnswer answer, List<HeardLine> lines, Instant callStart) {
        List<HeardLine> executive = lines.stream().filter(line -> !line.team()).toList();
        List<String> spoken = executive.stream()
                .map(line -> textKeyOf(ContactRedaction.redact(line.text())))
                .toList();
        List<Long> spokenAt = executive.stream()
                .map(line -> secondsOf(CallTranscriptText.elapsed(callStart, line.at())))
                .toList();
        List<CoachAnswer.Coverage> coverage = answer.coverage() == null ? List.of() : answer.coverage().stream()
                .filter(moved -> {
                    String quote = textKeyOf(moved.quote());
                    return !quote.isEmpty() && spoken.stream().anyMatch(line -> line.contains(quote));
                })
                .toList();
        List<CoachAnswer.GapHeard> gaps = answer.gapsHeard() == null ? List.of() : answer.gapsHeard().stream()
                .filter(heard -> !spokenAt.isEmpty() && (timeOf(heard.at()) == null
                        || spokenAt.stream().anyMatch(second -> Math.abs(second - secondsOf(timeOf(heard.at())))
                        <= GAP_TIME_SLACK_SECONDS)))
                .toList();
        return new CoachAnswer(answer.guard(), coverage, gaps, answer.asked());
    }

    private static long secondsOf(String callTime) {
        long seconds = 0;
        for (String part : callTime.split(":")) {
            seconds = seconds * 60 + Long.parseLong(part);
        }
        return seconds;
    }

    static String textKeyOf(String text) {
        return text == null ? "" : text.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", " ").strip();
    }

    private static String timeOf(String at) {
        return at != null && CALL_TIME.matcher(at.strip()).matches() ? at.strip() : null;
    }

    record CoachAnswer(String guard, List<Coverage> coverage, List<GapHeard> gapsHeard, List<String> asked) {

        record Coverage(String id, String state, String quote, String at) {}

        record GapHeard(String gap, String at) {}
    }
}
