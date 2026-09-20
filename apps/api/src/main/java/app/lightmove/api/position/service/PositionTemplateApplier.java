package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.BenefitFrequency;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.position.constant.FieldSource;
import app.lightmove.api.position.model.CompensationPackage;
import app.lightmove.api.position.model.MandateContext;
import app.lightmove.api.position.model.Position;
import app.lightmove.api.position.model.PositionBenefit;
import app.lightmove.api.position.model.PositionCompetency;
import app.lightmove.api.position.model.PositionCriterion;
import app.lightmove.api.position.model.PositionDetails;
import app.lightmove.api.position.model.PositionOrgNode;
import app.lightmove.api.position.model.PositionPriority;
import app.lightmove.api.position.model.PositionResponsibility;
import app.lightmove.api.position.model.ReportingStructure;
import app.lightmove.api.positiontemplate.model.PositionTemplate;
import app.lightmove.api.positiontemplate.model.PositionTemplateBody;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes a template's content onto a brief. One class for both callers on purpose: a mandate seeded
 * at creation and one whose consultant picked a different template afterwards must end up with the
 * same brief.
 *
 * <p><b>What the template writes, and what survives it.</b> Everything the template speaks for is
 * replaced — the responsibilities, the narrative, the org chart, the package's shape, the criteria
 * and both competency panels, every one of them stamped {@code TEMPLATE}. What survives is what is
 * not the template's to have an opinion about: every field a template does not carry (the location,
 * the salary band, the publication stamp); a criterion somebody wrote or a document reading filled in,
 * which {@code source} has marked since V7 (as {@code fromBrief}) for exactly this; and which
 * strategic priorities are lit.
 *
 * <p>The org chart is rebuilt rather than merged: a chart is a tree of seats around <i>this</i> role,
 * and a merge of two roles' charts is neither.
 */
final class PositionTemplateApplier {

    private PositionTemplateApplier() {
    }

    static void applyTo(Position position, PositionTemplate template) {
        PositionTemplateBody body = template.getBody();

        position.applyDetails(new PositionDetails(
                body.department(), position.getLocation(), body.employmentType(),
                template.getSeniority(), draftedResponsibilities(body), body.narrative(),
                detailsFieldSources(body, template.getSeniority())));

        position.applyContext(new MandateContext(
                position.getMandateReason(), position.getBusinessDriver(),
                mergedPriorities(position.getStrategicPriorities(), body.strategicPriorities()),
                position.isConfidential(), position.getInternalContext(), Map.of()));

        position.applyReporting(new ReportingStructure(
                seededChart(body), position.getTeamSize(), body.noticeValue(), body.noticeUnit(),
                reportingFieldSources(body)));

        position.applyCompensation(new CompensationPackage(
                body.currency(), position.getSalaryMin(), position.getSalaryMax(), body.baseSalaryMode(),
                body.bonusValue(), body.bonusBasis(), body.incentiveType(),
                position.getIncentiveAmount(), body.incentiveVesting(), draftedBenefits(body)));

        position.replaceCriteria(draftedCriteria(position, body));
        position.replaceCompetencies(body.competencies().stream()
                .map(competency -> PositionCompetency.of(competency.panel(), competency.name(),
                        competency.description(), competency.weight(), FieldSource.TEMPLATE))
                .toList());
    }

    private static List<PositionResponsibility> draftedResponsibilities(PositionTemplateBody body) {
        return body.responsibilities().stream()
                .map(text -> PositionResponsibility.of(text, FieldSource.TEMPLATE))
                .toList();
    }

    /** Only the scalars the template actually carries — never location, mandateReason, businessDriver
     * or teamSize, which this applier always leaves exactly as it found them. */
    private static Map<String, FieldSource> detailsFieldSources(PositionTemplateBody body, Seniority seniority) {
        Map<String, FieldSource> sources = new LinkedHashMap<>();
        claim(sources, "department", body.department());
        claim(sources, "employmentType", body.employmentType());
        claim(sources, "seniority", seniority);
        claim(sources, "narrative", body.narrative());
        return sources;
    }

    private static Map<String, FieldSource> reportingFieldSources(PositionTemplateBody body) {
        Map<String, FieldSource> sources = new LinkedHashMap<>();
        claim(sources, "noticeValue", body.noticeValue());
        claim(sources, "noticeUnit", body.noticeUnit());
        return sources;
    }

    private static void claim(Map<String, FieldSource> sources, String key, Object value) {
        if (value != null) {
            sources.put(key, FieldSource.TEMPLATE);
        }
    }

    /** The benefit lines as the brief stores them — the amount is the mandate's to fill in. */
    private static List<PositionBenefit> draftedBenefits(PositionTemplateBody body) {
        return body.benefits().stream()
                .map(benefit -> PositionBenefit.of(benefit.name(), null,
                        benefit.frequency() == null ? BenefitFrequency.MONTHLY : benefit.frequency(),
                        FieldSource.TEMPLATE))
                .toList();
    }

    /**
     * The chart a template draws: the seat above, the mandate's own, and the seats the role usually
     * owns beneath it. A template that names no manager makes the mandate seat the root rather than
     * hanging it under an empty box.
     */
    private static List<PositionOrgNode> seededChart(PositionTemplateBody body) {
        boolean hasManager = body.reportsTo() != null && !body.reportsTo().isBlank();
        UUID managerId = hasManager ? UUID.randomUUID() : null;
        UUID seatId = UUID.randomUUID();

        // The mandate seat leads the list — Position#mandateSeatFirst explains why that matters.
        List<PositionOrgNode> chart = new ArrayList<>();
        chart.add(PositionOrgNode.mandateSeat(seatId, managerId, FieldSource.TEMPLATE));
        if (hasManager) {
            chart.add(PositionOrgNode.of(managerId, null, body.reportsTo(), null, false, null, null,
                    FieldSource.TEMPLATE));
        }
        body.directReports().stream()
                .filter(title -> title != null && !title.isBlank())
                .forEach(title -> chart.add(PositionOrgNode.of(UUID.randomUUID(), seatId, title, null,
                        false, null, null, FieldSource.TEMPLATE)));
        return chart;
    }

    /**
     * The template's palette, keeping every choice already made against it — selection and
     * provenance alike — with anything the consultant added of their own appended.
     *
     * <p>Matched on the lower-cased name, the identity {@code PositionService} enforces uniqueness
     * on: merging on anything looser would produce the same-looking pair that write refuses.
     */
    private static List<PositionPriority> mergedPriorities(List<PositionPriority> current,
                                                           List<String> palette) {
        Map<String, PositionPriority> currentByName = current.stream()
                .collect(Collectors.toMap(priority -> normalised(priority.getName()), priority -> priority,
                        (first, second) -> first));
        Set<String> drafted = palette.stream()
                .map(PositionTemplateApplier::normalised)
                .collect(Collectors.toSet());

        List<PositionPriority> merged = new ArrayList<>(palette.stream()
                .map(name -> {
                    PositionPriority existing = currentByName.get(normalised(name));
                    boolean selected = existing != null && existing.isSelected();
                    FieldSource source = existing != null ? existing.getSource() : FieldSource.TEMPLATE;
                    return PositionPriority.of(name, selected, source);
                })
                .toList());
        current.stream()
                .filter(priority -> !drafted.contains(normalised(priority.getName())))
                .forEach(priority -> merged.add(
                        PositionPriority.of(priority.getName(), priority.isSelected(), priority.getSource())));
        return merged;
    }

    private static String normalised(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** The template's criteria, then whatever the consultant wrote or a document reading filled in. */
    private static List<PositionCriterion> draftedCriteria(Position position, PositionTemplateBody body) {
        List<PositionCriterion> criteria = new ArrayList<>(body.criteria().stream()
                .map(criterion -> PositionCriterion.of(
                        criterion.text(), modeOf(criterion.mode()), FieldSource.TEMPLATE))
                .toList());
        position.getCriteria().stream()
                .filter(criterion -> !criterion.isTemplateDrafted())
                .forEach(criteria::add);
        return criteria;
    }

    private static CriterionMode modeOf(CriterionMode mode) {
        return mode == null ? CriterionMode.REQUIRED : mode;
    }
}
