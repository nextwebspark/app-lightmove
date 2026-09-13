package app.lightmove.api.positiontemplate.service;

import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.positiontemplate.model.PositionTemplate;
import app.lightmove.api.positiontemplate.model.PositionTemplateBenefit;
import app.lightmove.api.positiontemplate.model.PositionTemplateBody;
import app.lightmove.api.positiontemplate.model.PositionTemplateCompetency;
import app.lightmove.api.positiontemplate.model.PositionTemplateCriterion;
import app.lightmove.api.positiontemplate.model.PositionTemplateDraft;
import app.lightmove.api.positiontemplate.model.TemplateProblem;
import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * The rules a template passes before it is stored, shared by the editor and the import.
 *
 * <p>They mirror the brief's own limits — its {@code Put*Request} DTOs and the position tables'
 * columns — because a template is applied into a brief every time a mandate is created. Content the
 * brief cannot hold would fail there, inside project creation, for every workspace that uses the
 * template.
 */
@Component
class PositionTemplateValidator {

    private static final Pattern CURRENCY = Pattern.compile("[A-Z]{3}");
    private static final BigDecimal MAX_BONUS = new BigDecimal("9999.99");
    private static final int MAX_COMPETENCIES_PER_PANEL = 10;

    /** Normalises the draft and refuses it with every problem at once, keyed by field. */
    PositionTemplateDraft requireValid(PositionTemplateDraft draft) {
        PositionTemplateDraft normalised = draft.normalised();
        Map<String, String> problems = problemsByField(normalised);
        if (!problems.isEmpty()) {
            throw ApiException.withFields(ErrorCode.VALIDATION_FAILED, problems);
        }
        return normalised;
    }

    static void requireVersion(PositionTemplate template, Long version) {
        if (version == null || version != template.getVersion()) {
            throw new ApiException(ErrorCode.TEMPLATE_STALE,
                    "Template " + template.getCode() + " is at version " + template.getVersion());
        }
    }

    /** Expects a normalised draft. */
    List<TemplateProblem> problemsOf(PositionTemplateDraft draft) {
        return problemsByField(draft).entrySet().stream()
                .map(problem -> new TemplateProblem(problem.getKey(), problem.getValue()))
                .toList();
    }

    /** At most one problem per field, the first found. */
    private static Map<String, String> problemsByField(PositionTemplateDraft draft) {
        Map<String, String> problems = new LinkedHashMap<>();

        if (draft.title() == null || draft.title().isEmpty()) {
            problems.put("title", "Give the template a title");
        }
        tooLong(problems, "title", draft.title(), 160, "That title is too long");
        if (draft.discipline() == null) {
            problems.put("discipline", "Choose a discipline");
        }
        if (draft.seniority() == null) {
            problems.put("seniority", "Choose a seniority");
        }
        tooLong(problems, "summary", draft.summary(), 300, "That summary is too long");
        list(problems, "keywords", draft.keywords(), 20, 80, "That is too many keywords",
                "A keyword is too long");

        PositionTemplateBody body = draft.body();
        tooLong(problems, "body.department", body.department(), 160, "That department name is too long");
        tooLong(problems, "body.narrative", body.narrative(), 4000, "That profile is too long");
        list(problems, "body.responsibilities", body.responsibilities(), 20, 200,
                "That is too many responsibilities", "A responsibility is too long");
        tooLong(problems, "body.reportsTo", body.reportsTo(), 160, "That seat title is too long");
        list(problems, "body.directReports", body.directReports(), 30, 160,
                "That is too many direct reports", "A direct report's title is too long");
        list(problems, "body.strategicPriorities", body.strategicPriorities(), 20, 120,
                "That is too many strategic priorities", "A strategic priority is too long");
        if (body.noticeValue() != null && (body.noticeValue() < 0 || body.noticeValue() > 999)) {
            problems.put("body.noticeValue", "Notice is a number from 0 to 999");
        }
        if (!CURRENCY.matcher(body.currency()).matches()) {
            problems.put("body.currency", "Use a three-letter currency code");
        }
        BigDecimal bonus = body.bonusValue();
        if (bonus != null && (bonus.signum() < 0 || bonus.compareTo(MAX_BONUS) > 0
                || bonus.stripTrailingZeros().scale() > 2)) {
            problems.put("body.bonusValue", "A bonus is between 0 and 9999.99, to two decimal places");
        }
        tooLong(problems, "body.incentiveVesting", body.incentiveVesting(), 200,
                "That vesting schedule is too long");

        benefits(problems, body.benefits());
        criteria(problems, body.criteria());
        competencies(problems, body.competencies());
        return problems;
    }

    private static void benefits(Map<String, String> problems, List<PositionTemplateBenefit> benefits) {
        if (benefits.size() > 20) {
            problems.putIfAbsent("body.benefits", "That is too many benefits");
        }
        for (PositionTemplateBenefit benefit : benefits) {
            if (benefit.name() == null || benefit.name().isEmpty()) {
                problems.putIfAbsent("body.benefits", "Name every benefit");
            }
            tooLong(problems, "body.benefits", benefit.name(), 120, "A benefit name is too long");
        }
    }

    private static void criteria(Map<String, String> problems, List<PositionTemplateCriterion> criteria) {
        if (criteria.size() > 30) {
            problems.putIfAbsent("body.criteria", "That is too many criteria");
        }
        for (PositionTemplateCriterion criterion : criteria) {
            if (criterion.text() == null || criterion.text().isEmpty()) {
                problems.putIfAbsent("body.criteria", "Every criterion needs its text");
            }
            tooLong(problems, "body.criteria", criterion.text(), 300, "A criterion is too long");
        }
    }

    private static void competencies(Map<String, String> problems,
                                     List<PositionTemplateCompetency> competencies) {
        for (PositionTemplateCompetency competency : competencies) {
            if (competency.panel() == null) {
                problems.putIfAbsent("body.competencies", "Every competency needs a panel");
            }
        }
        for (CompetencyPanel panel : CompetencyPanel.values()) {
            String field = "body.competencies." + panel.name().toLowerCase(Locale.ROOT);
            String panelName = panel == CompetencyPanel.TECHNICAL ? "Technical" : "Behavioural";
            List<PositionTemplateCompetency> rows = competencies.stream()
                    .filter(competency -> competency.panel() == panel)
                    .toList();
            if (rows.size() > MAX_COMPETENCIES_PER_PANEL) {
                problems.putIfAbsent(field, "That is too many " + panelName.toLowerCase(Locale.ROOT)
                        + " competencies");
            }
            for (PositionTemplateCompetency competency : rows) {
                if (competency.name() == null || competency.name().isEmpty()) {
                    problems.putIfAbsent(field, "Name every competency");
                }
                tooLong(problems, field, competency.name(), 120, "A competency name is too long");
                tooLong(problems, field, competency.description(), 300, "A competency description is too long");
                if (competency.weight() < 0 || competency.weight() > 100) {
                    problems.putIfAbsent(field, "Weights are between 0 and 100");
                }
            }
            if (!rows.isEmpty() && rows.stream().mapToInt(PositionTemplateCompetency::weight).sum() != 100) {
                problems.putIfAbsent(field, panelName + " competency weights must total 100");
            }
        }
    }

    private static void list(Map<String, String> problems, String field, List<String> values, int maxItems,
                             int maxLength, String tooMany, String tooLongItem) {
        if (values.size() > maxItems) {
            problems.putIfAbsent(field, tooMany);
        }
        values.forEach(value -> tooLong(problems, field, value, maxLength, tooLongItem));
    }

    private static void tooLong(Map<String, String> problems, String field, String value, int maxLength,
                                String message) {
        if (value != null && value.length() > maxLength) {
            problems.putIfAbsent(field, message);
        }
    }
}
