package app.lightmove.api.position.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.common.constant.BaseSalaryMode;
import app.lightmove.api.common.constant.BenefitFrequency;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.constant.CompetencyPanel;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.EmploymentType;
import app.lightmove.api.common.constant.IncentiveType;
import app.lightmove.api.common.constant.NoticeUnit;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.constant.FieldSource;
import app.lightmove.api.position.constant.MandateReason;
import app.lightmove.api.position.model.CompensationPackage;
import app.lightmove.api.position.model.MandateContext;
import app.lightmove.api.position.model.Position;
import app.lightmove.api.position.model.PositionBenefit;
import app.lightmove.api.position.model.PositionCriterion;
import app.lightmove.api.position.model.PositionDetails;
import app.lightmove.api.position.model.PositionPriority;
import app.lightmove.api.position.model.ReportingStructure;
import app.lightmove.api.positiontemplate.constant.PositionDiscipline;
import app.lightmove.api.positiontemplate.model.PositionTemplate;
import app.lightmove.api.positiontemplate.model.PositionTemplateBenefit;
import app.lightmove.api.positiontemplate.model.PositionTemplateBody;
import app.lightmove.api.positiontemplate.model.PositionTemplateCompetency;
import app.lightmove.api.positiontemplate.model.PositionTemplateCriterion;
import app.lightmove.api.positiontemplate.model.PositionTemplateDraft;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.IntStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** What re-picking a template keeps of a brief somebody has already worked on. */
class PositionTemplateApplierTest {

    @Test
    @DisplayName("a typed band keeps its currency and period, and a typed incentive its type")
    void figuresKeepTheirUnits() {
        Position position = Position.forProject(UUID.randomUUID(), "Saudi Arabia");
        position.applyCompensation(new CompensationPackage("SAR", 40_000L, 50_000L, BaseSalaryMode.MONTHLY,
                null, null, IncentiveType.RSU, 200_000L, null, List.of()));

        PositionTemplateApplier.applyTo(position, template(body(null, "AED", BigDecimal.valueOf(30),
                BonusBasis.PERCENT_OF_BASE, List.of("Growth"), 0)));

        assertThat(position.getCurrency()).isEqualTo("SAR");
        assertThat(position.getBaseSalaryMode()).isEqualTo(BaseSalaryMode.MONTHLY);
        assertThat(position.getSalaryMin()).isEqualTo(40_000L);
        assertThat(position.getIncentiveType()).isEqualTo(IncentiveType.RSU);
        assertThat(position.getBonusValue()).isEqualByComparingTo("30");
        assertThat(position.getBonusBasis()).isEqualTo(BonusBasis.PERCENT_OF_BASE);
    }

    @Test
    @DisplayName("a brief with no figures takes the template's currency and period")
    void anEmptyPackageTakesTheTemplatesUnits() {
        Position position = Position.forProject(UUID.randomUUID(), null);

        PositionTemplateApplier.applyTo(position, template(body("Group Finance", "USD", null, null, List.of(), 0)));

        assertThat(position.getCurrency()).isEqualTo("USD");
        assertThat(position.getDepartment()).isEqualTo("Group Finance");
        assertThat(position.getFieldSources()).containsEntry("department", FieldSource.TEMPLATE);
    }

    @Test
    @DisplayName("what the template leaves blank keeps the brief's value, and every kept field its mark")
    void blanksKeepTypedValuesAndTheirProvenance() {
        Position position = Position.forProject(UUID.randomUUID(), null);
        position.applyDetails(new PositionDetails("Group Finance", "Riyadh", "Saudi Arabia",
                EmploymentType.FULL_TIME_PERMANENT, Seniority.C_SUITE, List.of(), "Typed narrative",
                Map.of("department", FieldSource.MANUAL, "locationCity", FieldSource.MANUAL,
                        "locationCountry", FieldSource.MANUAL, "narrative", FieldSource.MANUAL)));
        position.applyContext(new MandateContext(MandateReason.SUCCESSION, "Typed driver", List.of(), false, null,
                Map.of("mandateReason", FieldSource.MANUAL, "businessDriver", FieldSource.MANUAL)));
        position.applyReporting(new ReportingStructure(List.of(), "12", null, null,
                Map.of("teamSize", FieldSource.MANUAL)));
        position.applyCompensation(new CompensationPackage("AED", null, null, BaseSalaryMode.ANNUAL,
                BigDecimal.valueOf(20), BonusBasis.PERCENT_OF_BASE, null, null, "3 years", List.of()));

        PositionTemplateApplier.applyTo(position, template(body(null, "AED", null, null, List.of(), 0)));

        assertThat(position.getDepartment()).isEqualTo("Group Finance");
        assertThat(position.getNarrative()).isEqualTo("Typed narrative");
        assertThat(position.getBonusValue()).isEqualByComparingTo("20");
        assertThat(position.getIncentiveVesting()).isEqualTo("3 years");
        assertThat(position.getFieldSources())
                .containsEntry("department", FieldSource.MANUAL)
                .containsEntry("narrative", FieldSource.MANUAL)
                .containsEntry("locationCity", FieldSource.MANUAL)
                .containsEntry("locationCountry", FieldSource.MANUAL)
                .containsEntry("mandateReason", FieldSource.MANUAL)
                .containsEntry("businessDriver", FieldSource.MANUAL)
                .containsEntry("teamSize", FieldSource.MANUAL)
                .containsEntry("seniority", FieldSource.TEMPLATE);
    }

    @Test
    @DisplayName("merged lists stay within what a save of their step accepts, the template giving way first")
    void mergedListsStayWithinTheStepsCeilings() {
        Position position = Position.forProject(UUID.randomUUID(), null);
        List<PositionPriority> own = IntStream.range(0, 15)
                .mapToObj(index -> PositionPriority.of("Own " + index, true, FieldSource.MANUAL))
                .toList();
        position.applyContext(new MandateContext(MandateReason.NEW_ROLE, null, own, false, null, Map.of()));
        position.replaceCriteria(IntStream.range(0, 25)
                .mapToObj(index -> PositionCriterion.of("Own criterion " + index, CriterionMode.REQUIRED,
                        FieldSource.MANUAL))
                .toList());
        List<String> palette = IntStream.range(0, 20).mapToObj(index -> "Palette " + index).toList();

        PositionTemplateApplier.applyTo(position, template(body(null, "AED", null, null, palette, 30)));

        assertThat(position.getStrategicPriorities()).hasSize(PositionTemplateApplier.MAX_PRIORITIES);
        assertThat(position.getStrategicPriorities().stream().filter(PositionPriority::isSelected)).hasSize(15);
        assertThat(position.getCriteria()).hasSize(PositionTemplateApplier.MAX_CRITERIA);
        assertThat(position.getCriteria().stream().filter(criterion -> !criterion.isTemplateDrafted()))
                .hasSize(25);
    }

    @Test
    @DisplayName("a palette's case duplicates are drafted once, and an earlier template's unlit ones go")
    void palettesNeitherDuplicateNorPileUp() {
        Position position = Position.forProject(UUID.randomUUID(), null);
        position.applyContext(new MandateContext(MandateReason.NEW_ROLE, null, List.of(
                PositionPriority.of("Old template, unlit", false, FieldSource.TEMPLATE),
                PositionPriority.of("Old template, lit", true, FieldSource.TEMPLATE)), false, null, Map.of()));

        PositionTemplateApplier.applyTo(position, template(body(null, "AED", null, null,
                List.of("Growth", "growth"), 0)));

        assertThat(position.getStrategicPriorities()).extracting(PositionPriority::getName)
                .containsExactly("Growth", "Old template, lit");
    }

    @Test
    @DisplayName("a re-pick's benefits replace the old list; a template with none leaves typed benefits alone")
    void benefitsAreLeftAloneByATemplateWithNone() {
        Position position = Position.forProject(UUID.randomUUID(), null);
        position.applyCompensation(new CompensationPackage("AED", null, null, BaseSalaryMode.ANNUAL, null, null,
                null, null, null, List.of(PositionBenefit.of("Housing", 10_000L, BenefitFrequency.MONTHLY,
                        FieldSource.MANUAL))));

        PositionTemplateApplier.applyTo(position, template(body(null, "AED", null, null, List.of(), 0)));

        assertThat(position.getBenefits()).extracting(PositionBenefit::getName).containsExactly("Housing");
        assertThat(position.getCurrency()).isEqualTo("AED");
    }

    private static PositionTemplateBody body(String department, String currency, BigDecimal bonusValue,
                                             BonusBasis bonusBasis, List<String> priorities, int criteria) {
        return new PositionTemplateBody(department, null, null, List.of(), null, List.of(), priorities, null,
                NoticeUnit.MONTHS, currency, BaseSalaryMode.ANNUAL, bonusValue, bonusBasis, null, null,
                List.<PositionTemplateBenefit>of(),
                IntStream.range(0, criteria)
                        .mapToObj(index -> new PositionTemplateCriterion("Template criterion " + index,
                                CriterionMode.PREFERRED))
                        .toList(),
                List.<PositionTemplateCompetency>of());
    }

    private static PositionTemplate template(PositionTemplateBody body) {
        return PositionTemplate.forLibrary("chief-financial-officer", new PositionTemplateDraft(
                "Chief Financial Officer", PositionDiscipline.FINANCE, Seniority.C_SUITE, null, List.of("cfo"),
                body).normalised(), 0, null);
    }
}
