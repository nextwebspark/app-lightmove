package app.lightmove.api.positiontemplate.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.BaseSalaryMode;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.positiontemplate.constant.PositionDiscipline;
import app.lightmove.api.positiontemplate.model.PositionTemplateBody;
import app.lightmove.api.positiontemplate.model.PositionTemplateDraft;
import app.lightmove.api.positiontemplate.model.TemplateProblem;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** A template is held to the brief's own rules, or applying it leaves a step that can never be saved. */
class PositionTemplateValidatorTest {

    private final PositionTemplateValidator validator = new PositionTemplateValidator();

    @Test
    @DisplayName("a currency outside the ISO list is refused, as the brief's own write refuses it")
    void refusesAnUnknownCurrency() {
        assertThat(problemsOf(body("XYZ", null, null, List.of()))).containsKey("body.currency");
        assertThat(problemsOf(body("SAR", null, null, List.of()))).doesNotContainKey("body.currency");
    }

    @Test
    @DisplayName("a bonus past what its basis reads as true is refused")
    void refusesABonusItsBasisDoesNotAdmit() {
        assertThat(problemsOf(body("AED", new BigDecimal("150"), BonusBasis.PERCENT_OF_BASE, List.of())))
                .containsEntry("body.bonusValue", "A bonus is at most 100% of base");
        assertThat(problemsOf(body("AED", new BigDecimal("150000"), BonusBasis.FIXED_AMOUNT, List.of())))
                .doesNotContainKey("body.bonusValue");
    }

    @Test
    @DisplayName("two strategic priorities differing only in case are refused")
    void refusesCaseDuplicatePriorities() {
        assertThat(problemsOf(body("AED", null, null, List.of("Growth", "growth"))))
                .containsKey("body.strategicPriorities");
    }

    private Map<String, String> problemsOf(PositionTemplateBody body) {
        PositionTemplateDraft draft = new PositionTemplateDraft("Chief Financial Officer", PositionDiscipline.FINANCE,
                Seniority.C_SUITE, null, List.of("cfo"), body).normalised();
        return validator.problemsOf(draft).stream()
                .collect(Collectors.toMap(TemplateProblem::field, TemplateProblem::message));
    }

    private static PositionTemplateBody body(String currency, BigDecimal bonusValue, BonusBasis bonusBasis,
                                             List<String> priorities) {
        return new PositionTemplateBody(null, null, null, List.of(), null, List.of(), priorities, null, null,
                currency, BaseSalaryMode.ANNUAL, bonusValue, bonusBasis, null, null, List.of(), List.of(), List.of());
    }
}
