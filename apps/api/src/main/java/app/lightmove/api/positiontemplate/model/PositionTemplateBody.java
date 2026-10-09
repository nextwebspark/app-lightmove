package app.lightmove.api.positiontemplate.model;

import app.lightmove.api.common.constant.BaseSalaryMode;
import app.lightmove.api.common.constant.BenefitFrequency;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.DefaultCurrency;
import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.common.constant.IncentiveType;
import app.lightmove.api.common.constant.MandateReason;
import app.lightmove.api.common.constant.NoticeUnit;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Everything a role template drafts into a fresh brief — the whole document stored in
 * {@code app_lm_position_template.body}.
 *
 * <p>Its fields follow the brief's steps — Role Brief, Reporting, Compensation, Assessment — so a
 * template is written in the shape a consultant reads it in. It deliberately carries no role title,
 * target date, location or salary band: those are the mandate's and the client's, and a template
 * asserting them would be inventing a fact about a search it has never seen. The package it does
 * carry is shape rather than money.
 *
 * <p>Null-tolerant on the way in, and {@code @JsonIgnoreProperties} is load-bearing, both for
 * {@code StrategyFilter}'s reasons: a field retired from this record must not make every stored
 * template unreadable. The defaults are the columns the brief stores {@code NOT NULL}, so
 * applying a template can never leave the position unwritable. The import is the one reader that must
 * not forgive an unknown key, and checks for one itself before binding.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PositionTemplateBody(
        EmploymentType employmentType,
        MandateReason mandateReason,

        /** Null leaves the brief's own setting alone rather than declaring every mandate open. */
        Boolean confidential,

        Integer noticeValue,
        NoticeUnit noticeUnit,
        List<String> responsibilities,
        String narrative,

        /** The seats around the role, exactly one of them the role's own. */
        List<PositionTemplateSeat> orgChart,

        String currency,
        BaseSalaryMode baseSalaryMode,
        BigDecimal bonusValue,
        BonusBasis bonusBasis,
        IncentiveType incentiveType,
        String incentiveVesting,
        List<PositionTemplateBenefit> benefits,
        List<PositionTemplateCriterion> criteria,
        List<PositionTemplateCompetency> competencies,

        /** The technical panel's share of the assessment; the behavioural panel takes the rest. */
        Integer technicalShare
) {

    public static final int DEFAULT_TECHNICAL_SHARE = 50;

    public PositionTemplateBody {
        responsibilities = copyOrEmpty(responsibilities);
        orgChart = orgChart == null || orgChart.isEmpty()
                ? List.of(PositionTemplateSeat.ofMandate(null))
                : orgChart.stream().filter(Objects::nonNull).toList();
        benefits = copyOrEmpty(benefits);
        criteria = copyOrEmpty(criteria);
        competencies = copyOrEmpty(competencies);
        currency = currency == null ? DefaultCurrency.CODE : currency;
        baseSalaryMode = baseSalaryMode == null ? BaseSalaryMode.ANNUAL : baseSalaryMode;
        technicalShare = technicalShare == null ? DEFAULT_TECHNICAL_SHARE : technicalShare;
    }

    /** What an unwritten template drafts: nothing, which is a blank brief rather than a broken one. */
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

    /** Trimmed, blank entries dropped, defaults made explicit — so equal content compares equal. */
    public PositionTemplateBody normalised() {
        return new PositionTemplateBody(
                employmentType, mandateReason, confidential, noticeValue, noticeUnit,
                trimmed(responsibilities), blankToNull(narrative), normalisedChart(orgChart),
                currency.trim().toUpperCase(Locale.ROOT), baseSalaryMode,
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
                        .toList(),
                technicalShare);
    }

    /**
     * Titles trimmed, and an untitled seat other than the role's spliced out — its children re-attach
     * to its parent, the brief's own deletion rule — so an emptied box never survives as a blank seat.
     */
    private static List<PositionTemplateSeat> normalisedChart(List<PositionTemplateSeat> seats) {
        Map<String, String> parentOfSpliced = new HashMap<>();
        for (PositionTemplateSeat seat : seats) {
            if (!seat.mandateSeat() && blankToNull(seat.title()) == null && seat.id() != null) {
                parentOfSpliced.put(seat.id().trim(), trim(seat.parentId()));
            }
        }
        return seats.stream()
                .filter(seat -> seat.mandateSeat() || blankToNull(seat.title()) != null)
                .map(seat -> new PositionTemplateSeat(trim(seat.id()),
                        survivingParent(trim(seat.parentId()), parentOfSpliced),
                        seat.mandateSeat() ? null : seat.title().trim(), seat.mandateSeat()))
                .toList();
    }

    /** Bounded by the map's size, so a cycle among spliced seats ends rather than spins. */
    private static String survivingParent(String parentId, Map<String, String> parentOfSpliced) {
        String current = parentId;
        for (int hop = 0; current != null && parentOfSpliced.containsKey(current) && hop <= parentOfSpliced.size();
             hop++) {
            current = parentOfSpliced.get(current);
        }
        return current;
    }

    /** A bonus finer than the brief's numeric(14,2) is left for the validator to refuse, never rounded. */
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
