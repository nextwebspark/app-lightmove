package app.lightmove.api.enrichment.candidate.service;

import app.lightmove.api.candidate.constant.NoteOutcome;
import app.lightmove.api.candidate.constant.PersonDocumentCategory;
import app.lightmove.api.candidate.constant.PersonNoteKind;
import app.lightmove.api.candidate.model.CandidateDossier;
import app.lightmove.api.candidate.model.NoteOnFile;
import app.lightmove.api.candidate.model.SpokenLine;
import app.lightmove.api.candidate.model.TranscriptOnFile;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.model.AssessmentBrief;
import app.lightmove.api.position.model.BriefCompetencies;
import app.lightmove.api.position.model.BriefCompetency;
import app.lightmove.api.position.model.BriefCriterion;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * An assessment draft's eval case as it is stored: a fictional executive's profile, the team's dated notes, call
 * transcripts and documents on them, and the brief they are assessed against. It becomes the same three things the
 * drafter is handed for a real person, the material written out by {@link CandidateMaterialReader}'s own code.
 */
final class AssessmentCase {

    static final String NOTES = "notes";
    static final String TRANSCRIPTS = "transcripts";
    static final String DOCUMENTS = "documents";
    static final String BRIEF = "brief";

    private static final Set<String> INPUT_FIELDS =
            Set.of(GoldenProfileCase.PROFILE, NOTES, TRANSCRIPTS, DOCUMENTS, BRIEF);
    private static final Set<String> NOTE_FIELDS = Set.of("date", "kind", "aboutPosition", "outcome", "body");
    private static final Set<String> TRANSCRIPT_FIELDS = Set.of("date", "title", "lines");
    private static final Set<String> LINE_FIELDS = Set.of("at", "speaker", "team", "text");
    private static final Set<String> DOCUMENT_FIELDS = Set.of("title", "category", "text");
    private static final Set<String> BRIEF_FIELDS = Set.of("roleTitle", "seniority", "department", "locationCity",
            "locationCountry", "narrative", "responsibilities", "businessDriver", "strategicPriorities", "criteria",
            "competencies", "technicalShare");

    /** Time into a call: {@code MM:SS}, or {@code H:MM:SS} past the hour, as a transcript is written out. */
    private static final Pattern ELAPSED = Pattern.compile("(?:(\\d+):)?(\\d{1,2}):(\\d{2})");

    private static final int NOTE_HOUR = 12;
    private static final int CALL_HOUR = 10;
    private static final int MAX_WEIGHT = 100;

    private AssessmentCase() {
    }

    static List<String> problemsOf(JsonNode input, ObjectMapper json) {
        List<String> problems = new ArrayList<>();
        refuseOtherFields(input, INPUT_FIELDS, "input", problems);
        problems.addAll(GoldenProfileCase.profileProblemsOf(input.get(GoldenProfileCase.PROFILE), json));
        each(input, NOTES, problems, AssessmentCase::noteProblems);
        each(input, TRANSCRIPTS, problems, AssessmentCase::transcriptProblems);
        each(input, DOCUMENTS, problems, AssessmentCase::documentProblems);
        briefProblems(input.get(BRIEF), problems);
        return problems;
    }

    static CandidateDossier dossierOf(JsonNode input, ObjectMapper json) {
        return GoldenProfileCase.profileOf(input, json).dossierMissing(Set.of());
    }

    static AssessmentBrief briefOf(JsonNode input) {
        JsonNode brief = input.get(BRIEF);
        List<BriefCriterion> criteria = new ArrayList<>();
        for (JsonNode criterion : brief.path("criteria")) {
            criteria.add(new BriefCriterion(textOf(criterion, "text"),
                    CriterionMode.valueOf(textOf(criterion, "mode"))));
        }
        List<BriefCompetency> competencies = new ArrayList<>();
        for (JsonNode competency : brief.path("competencies")) {
            competencies.add(new BriefCompetency(CompetencyPanel.valueOf(textOf(competency, "panel")),
                    textOf(competency, "name"), textOf(competency, "description"),
                    competency.get("weight").asInt()));
        }
        return new AssessmentBrief(textOf(brief, "roleTitle"), Seniority.fromValue(textOf(brief, "seniority")),
                textOf(brief, "department"), textOf(brief, "locationCity"), textOf(brief, "locationCountry"),
                textOf(brief, "narrative"), textsOf(brief, "responsibilities"), textOf(brief, "businessDriver"),
                textsOf(brief, "strategicPriorities"), criteria,
                new BriefCompetencies(competencies, brief.get("technicalShare").asInt()));
    }

    /** The competencies by the id the drafter gives each, which is the id an answer and a case's labels speak. */
    static Map<String, BriefCompetency> competenciesOf(JsonNode input) {
        return CandidateScorecardDrafter.idsOf(briefOf(input).competencies().competencies());
    }

    /** Notes and calls newest first, as the material of a real person is read. */
    static MaterialText materialOf(JsonNode input) {
        List<NoteOnFile> notes = new ArrayList<>();
        for (JsonNode note : input.path(NOTES)) {
            String kind = textOf(note, "kind");
            String outcome = textOf(note, "outcome");
            notes.add(new NoteOnFile(kind == null ? PersonNoteKind.GENERAL : PersonNoteKind.fromValue(kind),
                    dayOf(note).atTime(NOTE_HOUR, 0).toInstant(ZoneOffset.UTC), textOf(note, "aboutPosition"),
                    textOf(note, "body"), outcome == null ? null : ApiValueEnum.fromValue(NoteOutcome.class, outcome)));
        }
        notes.sort(Comparator.comparing(NoteOnFile::writtenAt).reversed());

        List<DocumentToRead> documents = new ArrayList<>();
        for (JsonNode document : input.path(DOCUMENTS)) {
            String category = textOf(document, "category");
            String text = textOf(document, "text");
            documents.add(new DocumentToRead(textOf(document, "title"), category == null
                    ? PersonDocumentCategory.OTHER : ApiValueEnum.fromValue(PersonDocumentCategory.class, category),
                    maxCharacters -> text));
        }

        List<TranscriptOnFile> transcripts = new ArrayList<>();
        for (JsonNode transcript : input.path(TRANSCRIPTS)) {
            Instant start = startOf(transcript);
            List<SpokenLine> lines = new ArrayList<>();
            for (JsonNode line : transcript.path("lines")) {
                lines.add(new SpokenLine(start.plusSeconds(secondsOf(textOf(line, "at"))), textOf(line, "speaker"),
                        textOf(line, "text"), line.path("team").asBoolean(false)));
            }
            transcripts.add(new TranscriptOnFile(textOf(transcript, "title"), start, lines));
        }
        transcripts.sort(Comparator.comparing(TranscriptOnFile::startsAt).reversed());

        return CandidateMaterialReader.withCalls(CandidateMaterialReader.written(notes, documents), transcripts);
    }

    static LocalDate dayOf(JsonNode dated) {
        return LocalDate.parse(textOf(dated, "date"));
    }

    static Instant startOf(JsonNode transcript) {
        return dayOf(transcript).atTime(CALL_HOUR, 0).toInstant(ZoneOffset.UTC);
    }

    /** Seconds into a call for a time written {@code MM:SS} or {@code H:MM:SS}; -1 when it is neither. */
    static long secondsOf(String elapsed) {
        Matcher written = elapsed == null ? null : ELAPSED.matcher(elapsed.strip());
        if (written == null || !written.matches()) {
            return -1;
        }
        long hours = written.group(1) == null ? 0 : Long.parseLong(written.group(1));
        return hours * 3600 + Long.parseLong(written.group(2)) * 60 + Long.parseLong(written.group(3));
    }

    static String textOf(JsonNode node, String field) {
        JsonNode value = node == null ? null : node.get(field);
        return value != null && value.isString() && !value.asString().isBlank() ? value.asString().strip() : null;
    }

    static List<String> textsOf(JsonNode node, String field) {
        List<String> texts = new ArrayList<>();
        if (node == null) {
            return texts;
        }
        for (JsonNode value : node.path(field)) {
            if (value.isString() && !value.asString().isBlank()) {
                texts.add(value.asString().strip());
            }
        }
        return texts;
    }

    private static void noteProblems(JsonNode note, String at, List<String> problems) {
        refuseOtherFields(note, NOTE_FIELDS, at, problems);
        if (!isDay(note)) {
            problems.add(at + ".date is missing. Every note carries its date.");
        }
        requireText(note, "body", at, problems);
        String kind = textOf(note, "kind");
        PersonNoteKind noteKind = kind == null ? PersonNoteKind.GENERAL : PersonNoteKind.fromValue(kind);
        if (noteKind == null) {
            problems.add(at + ".kind is not a kind of note.");
        }
        String outcome = textOf(note, "outcome");
        NoteOutcome noteOutcome = outcome == null ? null : ApiValueEnum.fromValue(NoteOutcome.class, outcome);
        if (outcome != null && (noteOutcome == null || noteKind == null || !noteOutcome.fits(noteKind))) {
            problems.add(at + ".outcome is not an outcome a note of its kind has.");
        }
    }

    private static void transcriptProblems(JsonNode transcript, String at, List<String> problems) {
        refuseOtherFields(transcript, TRANSCRIPT_FIELDS, at, problems);
        if (!isDay(transcript)) {
            problems.add(at + ".date is missing. Every call carries its date.");
        }
        JsonNode lines = transcript.get("lines");
        if (lines == null || !lines.isArray() || lines.isEmpty()) {
            problems.add(at + ".lines is missing. A call holds at least one line.");
            return;
        }
        for (int index = 0; index < lines.size(); index++) {
            JsonNode line = lines.get(index);
            String lineAt = at + ".lines[" + index + "]";
            refuseOtherFields(line, LINE_FIELDS, lineAt, problems);
            if (secondsOf(textOf(line, "at")) < 0) {
                problems.add(lineAt + ".at is missing. Every line carries its time into the call, as 03:10.");
            }
            requireText(line, "text", lineAt, problems);
            if (line.has("team") && !line.get("team").isBoolean()) {
                problems.add(lineAt + ".team is not true or false.");
            }
        }
    }

    private static void documentProblems(JsonNode document, String at, List<String> problems) {
        refuseOtherFields(document, DOCUMENT_FIELDS, at, problems);
        requireText(document, "title", at, problems);
        requireText(document, "text", at, problems);
        String category = textOf(document, "category");
        if (category != null && ApiValueEnum.fromValue(PersonDocumentCategory.class, category) == null) {
            problems.add(at + ".category is not a kind of document.");
        }
    }

    private static void briefProblems(JsonNode brief, List<String> problems) {
        String at = "input.brief";
        if (brief == null || !brief.isObject()) {
            problems.add(at + " is missing.");
            return;
        }
        refuseOtherFields(brief, BRIEF_FIELDS, at, problems);
        requireText(brief, "roleTitle", at, problems);
        String seniority = textOf(brief, "seniority");
        if (seniority != null && Seniority.fromValue(seniority) == null) {
            problems.add(at + ".seniority is not a seniority level.");
        }
        if (!isWeight(brief.get("technicalShare"))) {
            problems.add(at + ".technicalShare is not a whole number from 0 to 100.");
        }
        JsonNode criteria = brief.path("criteria");
        for (int index = 0; index < criteria.size(); index++) {
            String criterionAt = at + ".criteria[" + index + "]";
            requireText(criteria.get(index), "text", criterionAt, problems);
            if (!isNamed(CriterionMode.values(), textOf(criteria.get(index), "mode"))) {
                problems.add(criterionAt + ".mode is not REQUIRED or PREFERRED.");
            }
        }
        JsonNode competencies = brief.get("competencies");
        if (competencies == null || !competencies.isArray() || competencies.isEmpty()) {
            problems.add(at + ".competencies is missing. A case is scored against at least one competency.");
            return;
        }
        for (int index = 0; index < competencies.size(); index++) {
            JsonNode competency = competencies.get(index);
            String competencyAt = at + ".competencies[" + index + "]";
            requireText(competency, "name", competencyAt, problems);
            if (!isNamed(CompetencyPanel.values(), textOf(competency, "panel"))) {
                problems.add(competencyAt + ".panel is not TECHNICAL or BEHAVIOURAL.");
            }
            if (!isWeight(competency.get("weight"))) {
                problems.add(competencyAt + ".weight is not a whole number from 0 to 100.");
            }
        }
    }

    private static void each(JsonNode input, String field, List<String> problems, ItemCheck check) {
        JsonNode items = input.get(field);
        if (items == null || items.isNull()) {
            return;
        }
        if (!items.isArray()) {
            problems.add("input." + field + " is not a list.");
            return;
        }
        for (int index = 0; index < items.size(); index++) {
            check.problemsOf(items.get(index), "input." + field + "[" + index + "]", problems);
        }
    }

    private static void refuseOtherFields(JsonNode node, Set<String> fields, String at, List<String> problems) {
        if (node == null || !node.isObject()) {
            problems.add(at + " is not an object.");
            return;
        }
        for (Map.Entry<String, JsonNode> field : node.properties()) {
            if (!fields.contains(field.getKey())) {
                problems.add(at + " holds a field a case does not have.");
                return;
            }
        }
    }

    private static void requireText(JsonNode node, String field, String at, List<String> problems) {
        if (textOf(node, field) == null) {
            problems.add(at + "." + field + " is missing.");
        }
    }

    private static boolean isDay(JsonNode dated) {
        String date = textOf(dated, "date");
        if (date == null) {
            return false;
        }
        try {
            LocalDate.parse(date);
            return true;
        } catch (DateTimeParseException notADay) {
            return false;
        }
    }

    private static boolean isWeight(JsonNode value) {
        return value != null && value.isIntegralNumber() && value.asInt() >= 0 && value.asInt() <= MAX_WEIGHT;
    }

    private static boolean isNamed(Enum<?>[] constants, String name) {
        return Arrays.stream(constants).anyMatch(constant -> constant.name().equals(name));
    }

    @FunctionalInterface
    private interface ItemCheck {
        void problemsOf(JsonNode item, String at, List<String> problems);
    }
}
