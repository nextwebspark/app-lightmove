package app.lightmove.api.notetaker.service;

import app.lightmove.api.common.transcript.CallTranscriptText;
import app.lightmove.api.core.llm.model.BlockedAnswer;
import app.lightmove.api.core.llm.service.ContactRedaction;
import app.lightmove.api.core.llm.service.StructuredPrompt;
import app.lightmove.api.core.llm.service.StructuredPromptFactory;
import app.lightmove.api.notetaker.model.CompetencyEvidence;
import app.lightmove.api.notetaker.model.MeetingSummary;
import app.lightmove.api.notetaker.model.TranscriptLine;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.model.BriefCompetency;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * One model call turning a call's transcript into its note, against the position's brief when the call was about
 * one. The brief goes in through {@link AssessmentBrief} alone — role, criteria and competencies, never what the
 * position pays or why it is open — and every address and number is taken out of the transcript first.
 */
@Component
class MeetingSummariser {

    private static final String PROMPT_ID = "meeting-summary";
    private static final String BLOCKED = "{\"overview\":\"" + BlockedAnswer.MARKER + "\"}";
    private static final String NOT_STATED = "(not stated)";
    /** About three hours of talk; a longer transcript keeps its start and its end. */
    private static final int LONGEST_TRANSCRIPT = 400_000;
    private static final int LONGEST_ITEM = 600;

    private final StructuredPrompt prompt;

    MeetingSummariser(StructuredPromptFactory prompts) {
        this.prompt = prompts.create(PROMPT_ID, BLOCKED);
    }

    /** {@code brief} is null for a call about no position. Throws when nothing usable comes back. */
    MeetingSummary summarise(List<TranscriptLine> transcript, Instant callStart, AssessmentBrief brief) {
        Map<String, BriefCompetency> competencyById = brief == null ? Map.of() : idsOf(brief);
        ModelAnswer answer = prompt.ask(ModelAnswer.class, user -> user.text("""
                THE POSITION
                {position}

                THE TRANSCRIPT (time into the call, speaker, words)
                {transcript}
                """)
                .param("position", brief == null ? "None: the call was about no position. Leave the brief fields empty."
                        : positionOf(brief, competencyById))
                .param("transcript", transcriptText(transcript, callStart)));
        if (answer == null || answer.overview() == null || answer.overview().isBlank()) {
            throw new IllegalStateException("The summariser answered no overview");
        }
        if (BlockedAnswer.matches(answer.overview())) {
            throw new IllegalStateException("The summariser's guard stopped the transcript");
        }
        return new MeetingSummary(clip(answer.overview()), items(answer.keyPoints()), clipOrNull(answer.motivation()),
                clipOrNull(answer.packageSaid()), clipOrNull(answer.notice()), clipOrNull(answer.mobility()),
                items(answer.concerns()), items(answer.nextSteps()),
                evidenceOf(answer.competencyEvidence(), competencyById),
                namesOf(answer.competenciesNotCovered(), competencyById), items(answer.briefConcerns()));
    }

    /** The transcript as every Notetaker prompt reads it: timed, attributed, contacts taken out, start and end kept. */
    static String transcriptText(List<TranscriptLine> transcript, Instant callStart) {
        String text = transcript.stream()
                .map(line -> "[%s] %s: %s".formatted(CallTranscriptText.elapsed(callStart, line.at()), line.speaker(),
                        line.text()))
                .collect(Collectors.joining("\n"));
        return CallTranscriptText.keepStartAndEnd(ContactRedaction.redact(text), LONGEST_TRANSCRIPT);
    }

    static String positionOf(AssessmentBrief brief, Map<String, BriefCompetency> competencyById) {
        String place = List.of(Objects.toString(brief.locationCity(), ""),
                        Objects.toString(brief.locationCountry(), ""))
                .stream().filter(part -> !part.isBlank()).collect(Collectors.joining(", "));
        return """
                Title: %s
                Level: %s
                Department: %s
                Location: %s
                About the role: %s
                Responsibilities:
                %s
                Selection criteria:
                %s
                Competencies (id, panel, name, what it means here):
                %s""".formatted(orNotStated(brief.roleTitle()),
                brief.seniority() == null ? NOT_STATED : brief.seniority().value(), orNotStated(brief.department()),
                orNotStated(place), orNotStated(brief.narrative()), bullets(brief.responsibilities()),
                brief.criteria().isEmpty() ? NOT_STATED : brief.criteria().stream()
                        .map(criterion -> "- (%s) %s".formatted(criterion.mode().name().toLowerCase(Locale.ROOT),
                                criterion.text()))
                        .collect(Collectors.joining("\n")),
                competencyById.isEmpty() ? NOT_STATED : competencyById.entrySet().stream()
                        .map(entry -> "- %s [%s] %s%s".formatted(entry.getKey(), entry.getValue().panel().value(),
                                entry.getValue().name(), entry.getValue().description() == null
                                        || entry.getValue().description().isBlank() ? ""
                                        : ": " + entry.getValue().description()))
                        .collect(Collectors.joining("\n")));
    }

    static Map<String, BriefCompetency> idsOf(AssessmentBrief brief) {
        Map<String, BriefCompetency> byId = new LinkedHashMap<>();
        List<BriefCompetency> competencies = brief.competencies() == null ? List.of()
                : brief.competencies().competencies();
        for (int index = 0; index < competencies.size(); index++) {
            byId.put("c" + (index + 1), competencies.get(index));
        }
        return byId;
    }

    private static List<CompetencyEvidence> evidenceOf(List<ModelEvidence> answered,
                                                       Map<String, BriefCompetency> competencyById) {
        if (answered == null) {
            return List.of();
        }
        return answered.stream()
                .filter(Objects::nonNull)
                .filter(entry -> entry.id() != null && competencyById.containsKey(entry.id().strip())
                        && entry.evidence() != null && !entry.evidence().isBlank())
                .map(entry -> new CompetencyEvidence(competencyById.get(entry.id().strip()).name(),
                        clip(entry.evidence()), clipOrNull(entry.at())))
                .toList();
    }

    private static List<String> namesOf(List<String> ids, Map<String, BriefCompetency> competencyById) {
        if (ids == null) {
            return List.of();
        }
        return ids.stream()
                .filter(Objects::nonNull)
                .map(String::strip)
                .filter(competencyById::containsKey)
                .distinct()
                .map(id -> competencyById.get(id).name())
                .toList();
    }

    private static String bullets(List<String> lines) {
        return lines == null || lines.isEmpty() ? NOT_STATED
                : lines.stream().map(line -> "- " + line).collect(Collectors.joining("\n"));
    }

    private static String orNotStated(String value) {
        return value == null || value.isBlank() ? NOT_STATED : value;
    }

    private static List<String> items(List<String> answered) {
        return answered == null ? List.of() : answered.stream()
                .filter(item -> item != null && !item.isBlank())
                .map(MeetingSummariser::clip)
                .toList();
    }

    private static String clipOrNull(String value) {
        return value == null || value.isBlank() ? null : clip(value);
    }

    private static String clip(String value) {
        String stripped = value.strip();
        return stripped.length() <= LONGEST_ITEM ? stripped : stripped.substring(0, LONGEST_ITEM) + "…";
    }

    private record ModelAnswer(String overview, List<String> keyPoints, String motivation,
                               @JsonProperty("package") String packageSaid,
                               String notice, String mobility, List<String> concerns, List<String> nextSteps,
                               List<ModelEvidence> competencyEvidence, List<String> competenciesNotCovered,
                               List<String> briefConcerns) {}

    private record ModelEvidence(String id, String evidence, String at) {}
}
