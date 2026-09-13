package app.lightmove.api.positiontemplate.service;

import app.lightmove.api.common.constant.BaseSalaryMode;
import app.lightmove.api.common.constant.BenefitFrequency;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.common.constant.IncentiveType;
import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.config.PositionTemplateSettings;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.positiontemplate.constant.PositionDiscipline;
import app.lightmove.api.positiontemplate.model.ImportedTemplate;
import app.lightmove.api.positiontemplate.model.PositionTemplate;
import app.lightmove.api.positiontemplate.model.PositionTemplateBenefit;
import app.lightmove.api.positiontemplate.model.PositionTemplateBody;
import app.lightmove.api.positiontemplate.model.PositionTemplateCompetency;
import app.lightmove.api.positiontemplate.model.PositionTemplateCriterion;
import app.lightmove.api.positiontemplate.model.PositionTemplateDraft;
import app.lightmove.api.positiontemplate.model.TemplateProblem;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/**
 * The template file ({@code lightmove.position-templates}, version 1): what Export writes, what Import
 * reads, and what {@code position-templates.schema.json} publishes for anyone — or any model — writing
 * one outside the app.
 *
 * <p>Reading checks the file against the format before binding it, because binding alone forgives too
 * much: {@link PositionTemplateBody} ignores unknown keys so a retired field never breaks a stored
 * template, and that would let a misspelt key in a hand-written file vanish without a word.
 *
 * <p>Its limits are {@link PositionTemplateSettings}. There is no content-type check: the bytes
 * are parsed as JSON whatever the part claims.
 */
@Component
@RequiredArgsConstructor
class PositionTemplateExchange {

    static final String FORMAT = "lightmove.position-templates";
    static final int FORMAT_VERSION = 1;
    static final String SCHEMA_RESOURCE = "positiontemplate/position-templates.schema.json";

    private static final String UNTITLED = "Untitled template";
    private static final Set<String> TEMPLATE_FIELDS =
            Set.of("code", "title", "discipline", "seniority", "summary", "keywords", "body");
    private static final Set<String> BODY_FIELDS = fieldsOf(PositionTemplateBody.class);
    private static final Set<String> BENEFIT_FIELDS = fieldsOf(PositionTemplateBenefit.class);
    private static final Set<String> CRITERION_FIELDS = fieldsOf(PositionTemplateCriterion.class);
    private static final Set<String> COMPETENCY_FIELDS = fieldsOf(PositionTemplateCompetency.class);

    private final ObjectMapper json;
    private final PositionTemplateValidator validator;
    private final LightMoveProperties properties;

    byte[] write(List<PositionTemplate> templates) {
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("format", FORMAT);
        file.put("formatVersion", FORMAT_VERSION);
        file.put("exportedAt", Instant.now().toString());
        file.put("templates", templates.stream().map(PositionTemplateExchange::entryOf).toList());
        return json.writerWithDefaultPrettyPrinter().writeValueAsBytes(file);
    }

    byte[] schema() {
        try (InputStream schema = new ClassPathResource(SCHEMA_RESOURCE).getInputStream()) {
            return schema.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException("The template schema is missing from the build", e);
        }
    }

    /** One entry per template in the file, in file order. File-level faults refuse the whole file. */
    List<ImportedTemplate> read(MultipartFile file) {
        PositionTemplateSettings limits = properties.position().template();
        if (file.getSize() > limits.maxImportFileSizeBytes()) {
            throw ApiException.userFacing(ErrorCode.FILE_TOO_LARGE,
                    "A template file can be at most " + sizeOf(limits.maxImportFileSizeBytes()));
        }
        Object document;
        try {
            document = json.readValue(file.getBytes(), Object.class);
        } catch (IOException | JacksonException e) {
            throw new ApiException(ErrorCode.TEMPLATE_FILE_UNREADABLE, "Template import is not JSON");
        }
        if (!(document instanceof Map<?, ?> root) || !FORMAT.equals(root.get("format"))) {
            throw new ApiException(ErrorCode.TEMPLATE_FILE_UNREADABLE, "Template import carries no format marker");
        }
        if (!(root.get("formatVersion") instanceof Number version) || version.intValue() != FORMAT_VERSION) {
            throw ApiException.userFacing(ErrorCode.TEMPLATE_FILE_UNREADABLE,
                    "That template file is in a format version LightMove does not read");
        }
        if (!(root.get("templates") instanceof List<?> entries)) {
            throw new ApiException(ErrorCode.TEMPLATE_FILE_UNREADABLE, "Template import has no templates list");
        }
        if (entries.size() > limits.maxImportTemplates()) {
            throw ApiException.userFacing(ErrorCode.TEMPLATE_FILE_UNREADABLE,
                    "A template file can hold at most " + limits.maxImportTemplates() + " templates");
        }
        Set<String> codesSeen = new HashSet<>();
        return entries.stream().map(entry -> readTemplate(entry, codesSeen)).toList();
    }

    private ImportedTemplate readTemplate(Object entry, Set<String> codesSeen) {
        if (!(entry instanceof Map<?, ?> fields)) {
            return new ImportedTemplate(null, UNTITLED, null,
                    List.of(new TemplateProblem("template", "Each template must be an object")));
        }
        String title = fields.get("title") instanceof String text && !text.isBlank() ? text.trim() : UNTITLED;
        List<TemplateProblem> problems = new ArrayList<>();
        String code = codeOf(fields.get("code"), title, problems);
        if (code != null && !codesSeen.add(code)) {
            problems.add(new TemplateProblem("code", "Another template in this file has the same code"));
        }
        checkStructure(fields, problems);
        if (!problems.isEmpty()) {
            return new ImportedTemplate(code, title, null, problems);
        }
        PositionTemplateDraft draft;
        try {
            draft = bind(fields).normalised();
        } catch (IllegalArgumentException | JacksonException e) {
            return new ImportedTemplate(code, title, null,
                    List.of(new TemplateProblem("body", "The template could not be read")));
        }
        problems.addAll(validator.problemsOf(draft));
        return new ImportedTemplate(code, title, problems.isEmpty() ? draft : null, problems);
    }

    /** A file may leave the code out; the title's slug is then the code, so a library title reuses its template. */
    private static String codeOf(Object given, String title, List<TemplateProblem> problems) {
        if (given == null) {
            return PositionTemplateCodes.slugOf(title);
        }
        if (given instanceof String code && PositionTemplateCodes.isValid(code)) {
            return code;
        }
        problems.add(new TemplateProblem("code", "Use lower-case words joined by hyphens, at most 64 characters"));
        return null;
    }

    /**
     * Types and field names, before anything is bound. A competency's weight is required here because a
     * missing one would bind to 0 rather than fail, and quietly unbalance the panel.
     */
    private static void checkStructure(Map<?, ?> fields, List<TemplateProblem> problems) {
        unknownFields("", fields, TEMPLATE_FIELDS, problems);
        text("title", fields.get("title"), problems);
        text("summary", fields.get("summary"), problems);
        enumName("discipline", fields.get("discipline"), PositionDiscipline.class, problems);
        if (fields.get("seniority") != null && seniorityOf(fields.get("seniority")) == null) {
            problems.add(new TemplateProblem("seniority", "Must be one of " + names(Seniority.class)));
        }
        textList("keywords", fields.get("keywords"), problems);

        Object body = fields.get("body");
        if (!(body instanceof Map<?, ?> content)) {
            problems.add(new TemplateProblem("body", body == null ? "The template has no body" : "Must be an object"));
            return;
        }
        unknownFields("body.", content, BODY_FIELDS, problems);
        for (String field : List.of("department", "narrative", "reportsTo", "currency", "incentiveVesting")) {
            text("body." + field, content.get(field), problems);
        }
        for (String field : List.of("responsibilities", "directReports", "strategicPriorities")) {
            textList("body." + field, content.get(field), problems);
        }
        enumName("body.employmentType", content.get("employmentType"), EmploymentType.class, problems);
        enumName("body.noticeUnit", content.get("noticeUnit"), NoticeUnit.class, problems);
        enumName("body.baseSalaryMode", content.get("baseSalaryMode"), BaseSalaryMode.class, problems);
        enumName("body.bonusBasis", content.get("bonusBasis"), BonusBasis.class, problems);
        enumName("body.incentiveType", content.get("incentiveType"), IncentiveType.class, problems);
        wholeNumber("body.noticeValue", content.get("noticeValue"), problems);
        if (content.get("bonusValue") != null && !(content.get("bonusValue") instanceof Number)) {
            problems.add(new TemplateProblem("body.bonusValue", "Must be a number"));
        }
        objects("body.benefits", content.get("benefits"), BENEFIT_FIELDS, problems, (path, item) -> {
            text(path + ".name", item.get("name"), problems);
            enumName(path + ".frequency", item.get("frequency"), BenefitFrequency.class, problems);
        });
        objects("body.criteria", content.get("criteria"), CRITERION_FIELDS, problems, (path, item) -> {
            text(path + ".text", item.get("text"), problems);
            enumName(path + ".mode", item.get("mode"), CriterionMode.class, problems);
        });
        objects("body.competencies", content.get("competencies"), COMPETENCY_FIELDS, problems, (path, item) -> {
            enumName(path + ".panel", item.get("panel"), CompetencyPanel.class, problems);
            text(path + ".name", item.get("name"), problems);
            text(path + ".description", item.get("description"), problems);
            if (item.get("weight") == null) {
                problems.add(new TemplateProblem(path + ".weight", "Give every competency a weight"));
            } else {
                wholeNumber(path + ".weight", item.get("weight"), problems);
            }
        });
    }

    private PositionTemplateDraft bind(Map<?, ?> fields) {
        List<String> keywords = fields.get("keywords") instanceof List<?> items
                ? items.stream().map(String.class::cast).toList()
                : List.of();
        return new PositionTemplateDraft(
                (String) fields.get("title"),
                enumValue(PositionDiscipline.class, fields.get("discipline")),
                seniorityOf(fields.get("seniority")),
                (String) fields.get("summary"),
                keywords,
                json.convertValue(fields.get("body"), PositionTemplateBody.class));
    }

    private static Map<String, Object> entryOf(PositionTemplate template) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("code", template.getCode());
        entry.put("title", template.getTitle());
        entry.put("discipline", template.getDiscipline());
        entry.put("seniority", template.getSeniority());
        entry.put("summary", template.getSummary());
        entry.put("keywords", List.copyOf(template.getKeywords()));
        entry.put("body", template.getBody());
        return entry;
    }

    /** The enum name, or the token a consultant writes ("C-Suite", "N-1"). */
    private static Seniority seniorityOf(Object value) {
        Seniority byName = enumValue(Seniority.class, value);
        if (byName != null || !(value instanceof String token)) {
            return byName;
        }
        return Seniority.fromValue(token);
    }

    private static void unknownFields(String prefix, Map<?, ?> fields, Set<String> known,
                                      List<TemplateProblem> problems) {
        fields.keySet().stream()
                .map(String::valueOf)
                .filter(name -> !known.contains(name))
                .forEach(name -> problems.add(new TemplateProblem(prefix + shortened(name), "Not a template field")));
    }

    private static void text(String path, Object value, List<TemplateProblem> problems) {
        if (value != null && !(value instanceof String)) {
            problems.add(new TemplateProblem(path, "Must be text"));
        }
    }

    private static void textList(String path, Object value, List<TemplateProblem> problems) {
        if (value != null && !(value instanceof List<?> items && items.stream().allMatch(String.class::isInstance))) {
            problems.add(new TemplateProblem(path, "Must be a list of text"));
        }
    }

    private static void wholeNumber(String path, Object value, List<TemplateProblem> problems) {
        if (value == null) {
            return;
        }
        if (!(value instanceof Number number) || number.doubleValue() != Math.rint(number.doubleValue())
                || Math.abs(number.doubleValue()) > Integer.MAX_VALUE) {
            problems.add(new TemplateProblem(path, "Must be a whole number"));
        }
    }

    private static void objects(String path, Object value, Set<String> known, List<TemplateProblem> problems,
                                BiConsumer<String, Map<?, ?>> checkItem) {
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> items)) {
            problems.add(new TemplateProblem(path, "Must be a list"));
            return;
        }
        for (int index = 0; index < items.size(); index++) {
            String itemPath = path + "[" + index + "]";
            if (!(items.get(index) instanceof Map<?, ?> item)) {
                problems.add(new TemplateProblem(itemPath, "Must be an object"));
                continue;
            }
            unknownFields(itemPath + ".", item, known, problems);
            checkItem.accept(itemPath, item);
        }
    }

    private static <E extends Enum<E>> void enumName(String path, Object value, Class<E> type,
                                                    List<TemplateProblem> problems) {
        if (value != null && enumValue(type, value) == null) {
            problems.add(new TemplateProblem(path, "Must be one of " + names(type)));
        }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, Object value) {
        if (!(value instanceof String name)) {
            return null;
        }
        return Arrays.stream(type.getEnumConstants())
                .filter(constant -> constant.name().equals(name))
                .findFirst()
                .orElse(null);
    }

    private static String names(Class<? extends Enum<?>> type) {
        return Arrays.stream(type.getEnumConstants()).map(Enum::name).collect(Collectors.joining(", "));
    }

    private static String sizeOf(long bytes) {
        return bytes >= 1_048_576 ? bytes / 1_048_576 + " MB" : Math.max(1, bytes / 1024) + " KB";
    }

    private static String shortened(String name) {
        return name.length() > 60 ? name.substring(0, 60) + "…" : name;
    }

    private static Set<String> fieldsOf(Class<? extends Record> type) {
        return Arrays.stream(type.getRecordComponents())
                .map(RecordComponent::getName)
                .collect(Collectors.toUnmodifiableSet());
    }
}
