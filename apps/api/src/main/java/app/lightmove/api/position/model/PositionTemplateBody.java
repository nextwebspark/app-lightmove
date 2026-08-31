package app.lightmove.api.position.model;

import app.lightmove.api.position.constant.BaseSalaryMode;
import app.lightmove.api.position.constant.BenefitFrequency;
import app.lightmove.api.position.constant.BonusBasis;
import app.lightmove.api.position.constant.EmploymentType;
import app.lightmove.api.position.constant.IncentiveType;
import app.lightmove.api.position.constant.NoticeUnit;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.List;

/**
 * Everything a role template drafts into a fresh brief — the whole document stored in
 * {@code app_lm_position_template.body}, and the only shape the seeding and apply paths read.
 *
 * <p><b>What a template deliberately does not carry.</b> The role title and the target date are the
 * mandate's (V8), the location is the client's, and the salary band is the client's budget — a
 * template asserting any of them would be inventing a fact about a search it has never seen. The
 * package it does carry is shape rather than money: the currency, the base period, a market bonus
 * target and the allowance lines a GCC offer is built from.
 *
 * <p>Null-tolerant on the way in, like {@code StrategyFilter}: this record is read back out of a jsonb
 * column that a workspace will eventually write through a form, and a document missing a key it did
 * not fill must read as "nothing drafted for that step" rather than throw on the next request. The two
 * defaults are the two columns the brief stores {@code NOT NULL}, so applying a template can never
 * leave the position unwritable.
 *
 * <p>{@code @JsonIgnoreProperties} is load-bearing for the same reason it is on {@code StrategyFilter}:
 * a field retired from this record must not make every stored template unreadable.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionTemplateBody(
        String department,
        EmploymentType employmentType,
        String narrative,
        List<String> responsibilities,

        /** The seat above the mandate's, drawn as the root of the seeded chart. */
        String reportsTo,

        /**
         * The seats typically beneath the mandate's, drawn as its children. Named titles rather than
         * placeholders — a CFO template that says "Head of Treasury" is stating what the role usually
         * owns, which is the whole point of a template, and a chart is a canvas a consultant deletes
         * from freely.
         */
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
        currency = currency == null ? "USD" : currency;
        baseSalaryMode = baseSalaryMode == null ? BaseSalaryMode.ANNUAL : baseSalaryMode;
    }

    /** What an unwritten template drafts: nothing, which is a blank brief rather than a broken one. */
    public static PositionTemplateBody empty() {
        return new PositionTemplateBody(null, null, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null);
    }

    private static <T> List<T> copyOrEmpty(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }

    /** The benefit lines as the brief stores them — the amount is the mandate's to fill in. */
    public List<PositionBenefit> briefBenefits() {
        return benefits.stream()
                .map(benefit -> PositionBenefit.of(benefit.name(), null, frequencyOf(benefit)))
                .toList();
    }

    private static BenefitFrequency frequencyOf(PositionTemplateBenefit benefit) {
        return benefit.frequency() == null ? BenefitFrequency.MONTHLY : benefit.frequency();
    }
}
