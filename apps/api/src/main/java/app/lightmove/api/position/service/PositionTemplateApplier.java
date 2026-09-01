package app.lightmove.api.position.service;

import app.lightmove.api.position.constant.CriterionMode;
import app.lightmove.api.position.model.CompensationPackage;
import app.lightmove.api.position.model.MandateContext;
import app.lightmove.api.position.model.Position;
import app.lightmove.api.position.model.PositionCompetency;
import app.lightmove.api.position.model.PositionCriterion;
import app.lightmove.api.position.model.PositionDetails;
import app.lightmove.api.position.model.PositionOrgNode;
import app.lightmove.api.position.model.PositionPriority;
import app.lightmove.api.position.model.PositionTemplate;
import app.lightmove.api.position.model.PositionTemplateBody;
import app.lightmove.api.position.model.ReportingStructure;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes a template's content onto a brief. One class for both callers on purpose: a mandate seeded at
 * creation and a mandate whose consultant picked a different template afterwards must end up with the
 * same brief, and two code paths drafting the same document would not stay identical for long.
 *
 * <p><b>What the template writes, and what survives it.</b> Choosing a template is choosing a draft, so
 * everything the template speaks for is replaced — the responsibilities, the narrative, the org chart,
 * the package's shape, the criteria and both competency panels. What a person put in by hand survives,
 * because none of it is the template's to have an opinion about:
 *
 * <ul>
 *   <li>every field a template does not carry — the location, the salary band, why the mandate exists,
 *       the internal context, the team size, the publication stamp and the attached document;</li>
 *   <li>criteria a consultant wrote themselves. {@code fromBrief} has marked the drafted ones since
 *       V7 for exactly this: the template's own rows are replaced and hand-written ones are kept;</li>
 *   <li>which strategic priorities are lit. The palette becomes the new template's, but a priority
 *       already selected stays selected, and one somebody added by hand is carried across.</li>
 * </ul>
 *
 * <p>The org chart is rebuilt rather than merged. A chart is a tree of seats around <i>this</i> role,
 * and a merge of two role's charts is neither — the consultant who has arranged one and then changes
 * template is asking for the other one.
 */
final class PositionTemplateApplier {

    private PositionTemplateApplier() {
    }

    static void applyTo(Position position, PositionTemplate template) {
        PositionTemplateBody body = template.getBody();

        position.applyDetails(new PositionDetails(
                body.department(), position.getLocation(), body.employmentType(),
                template.getSeniority(), body.responsibilities(), body.narrative()));

        position.applyContext(new MandateContext(
                position.getMandateReason(), position.getBusinessDriver(),
                mergedPriorities(position.getStrategicPriorities(), body.strategicPriorities()),
                position.isConfidential(), position.getInternalContext()));

        position.applyReporting(new ReportingStructure(
                seededChart(body), position.getTeamSize(), body.noticeValue(), body.noticeUnit()));

        position.applyCompensation(new CompensationPackage(
                body.currency(), position.getSalaryMin(), position.getSalaryMax(), body.baseSalaryMode(),
                body.bonusValue(), body.bonusBasis(), body.incentiveType(),
                position.getIncentiveAmount(), body.incentiveVesting(), body.briefBenefits()));

        position.replaceCriteria(draftedCriteria(position, body));
        position.replaceCompetencies(body.competencies().stream()
                .map(competency -> PositionCompetency.of(competency.panel(), competency.name(),
                        competency.description(), competency.weight()))
                .toList());
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
        chart.add(PositionOrgNode.mandateSeat(seatId, managerId));
        if (hasManager) {
            chart.add(PositionOrgNode.of(managerId, null, body.reportsTo(), null, false, null, null));
        }
        body.directReports().stream()
                .filter(title -> title != null && !title.isBlank())
                .forEach(title -> chart.add(
                        PositionOrgNode.of(UUID.randomUUID(), seatId, title, null, false, null, null)));
        return chart;
    }

    /**
     * The template's palette, keeping every choice already made against it, with anything the
     * consultant added of their own appended.
     *
     * <p>Matched on the lower-cased name because that is the identity {@code PositionService} enforces
     * uniqueness on — merging on anything looser would produce the pair of same-looking chips that
     * write refuses.
     */
    private static List<PositionPriority> mergedPriorities(List<PositionPriority> current,
                                                           List<String> palette) {
        Set<String> selected = current.stream()
                .filter(PositionPriority::isSelected)
                .map(priority -> normalised(priority.getName()))
                .collect(Collectors.toSet());
        Set<String> drafted = palette.stream()
                .map(PositionTemplateApplier::normalised)
                .collect(Collectors.toSet());

        List<PositionPriority> merged = new ArrayList<>(palette.stream()
                .map(name -> PositionPriority.of(name, selected.contains(normalised(name))))
                .toList());
        current.stream()
                .filter(priority -> !drafted.contains(normalised(priority.getName())))
                .forEach(priority -> merged.add(
                        PositionPriority.of(priority.getName(), priority.isSelected())));
        return merged;
    }

    private static String normalised(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** The template's criteria, then whatever the consultant wrote for this mandate themselves. */
    private static List<PositionCriterion> draftedCriteria(Position position, PositionTemplateBody body) {
        List<PositionCriterion> criteria = new ArrayList<>(body.criteria().stream()
                .map(criterion -> PositionCriterion.of(criterion.text(), modeOf(criterion.mode()), true))
                .toList());
        position.getCriteria().stream()
                .filter(criterion -> !criterion.isFromBrief())
                .forEach(criteria::add);
        return criteria;
    }

    private static CriterionMode modeOf(CriterionMode mode) {
        return mode == null ? CriterionMode.REQUIRED : mode;
    }
}
