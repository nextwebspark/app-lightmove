package app.lightmove.api.positiontemplate.model;

import app.lightmove.api.common.constant.BaseSalaryMode;
import app.lightmove.api.common.constant.BenefitFrequency;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.DefaultCurrency;
import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.common.constant.IncentiveType;
import app.lightmove.api.common.constant.NoticeUnit;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

/**
 * Everything a role template drafts into a fresh brief — never a title, date, location or salary band.
 *
 * <p>{@code @JsonIgnoreProperties} is load-bearing: a retired field must not make stored templates
 * unreadable. The two defaults are the brief's {@code NOT NULL} columns.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionTemplateBody(
        String department,
        EmploymentType employmentType,
        String narrative,
        List<String> responsibilities,

        String reportsTo,
        List<String> directReports,

        List<String> strategicPriorities,
        Integer noticeValue,
        NoticeUnit noticeUnit,
        String currency,
        BaseSalaryMode baseSalaryMode,
        BigDecimal bonusValue,
        BonusBasis bonusBasis,
        IncentiveType incentiveType,
        String incentiveVesting,
        List<PositionTemplateBenefit> benefits,
        List<PositionTemplateCriterion> criteria,
        List<PositionTemplateCompetency> competencies
) {

    public PositionTemplateBody {
        responsibilities = copyOrEmpty(responsibilities);
        directReports = copyOrEmpty(directReports);
        strategicPriorities = copyOrEmpty(strategicPriorities);
        benefits = copyOrEmpty(benefits);
        criteria = copyOrEmpty(criteria);
        competencies = copyOrEmpty(competencies);
        currency = currency == null ? DefaultCurrency.CODE : currency;
        baseSalaryMode = baseSalaryMode == null ? BaseSalaryMode.ANNUAL : baseSalaryMode;
    }

    public static PositionTemplateBody empty() {
        return new PositionTemplateBody(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private static <T> List<T> copyOrEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    private static BenefitFrequency frequencyOf(PositionTemplateBenefit benefit) {
        return benefit.frequency() == null ? BenefitFrequency.MONTHLY : benefit.frequency();
    }

    /** Trimmed and defaults made explicit, so equal content compares equal. */
    public PositionTemplateBody normalised() {
        return new PositionTemplateBody(
                blankToNull(department), employmentType, blankToNull(narrative), trimmed(responsibilities),
                blankToNull(reportsTo), trimmed(directReports), trimmed(strategicPriorities),
                noticeValue, noticeUnit, currency.trim().toUpperCase(Locale.ROOT), baseSalaryMode,
                atBriefScale(bonusValue), bonusBasis, incentiveType, blankToNull(incentiveVesting),
                benefits.stream()
                        .map(benefit -> new PositionTemplateBenefit(trim(benefit.name()), frequencyOf(benefit)))
                        .toList(),
                criteria.stream()
                        .map(criterion -> new PositionTemplateCriterion(trim(criterion.text()),
                                criterion.mode() == null ? CriterionMode.REQUIRED : criterion.mode()))
                        .toList(),
                competencies.stream()
                        .map(competency -> new PositionTemplateCompetency(competency.panel(),
                                trim(competency.name()), blankToNull(competency.description()),
                                competency.weight()))
                        .toList());
    }

    /** A bonus finer than the brief's numeric(6,2) is left for the validator to refuse, never rounded. */
    private static BigDecimal atBriefScale(BigDecimal value) {
        return value == null || value.stripTrailingZeros().scale() > 2 ? value : value.setScale(2);
    }

    private static List<String> trimmed(List<String> values) {
        return values.stream().map(String::trim).filter(value -> !value.isEmpty()).toList();
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
