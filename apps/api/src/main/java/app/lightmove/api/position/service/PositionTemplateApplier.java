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
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Writes a template's content onto a brief. One class for both callers on purpose: a mandate seeded
 * at creation and one whose consultant picked a different template afterwards must end up with the
 * same brief.
 *
 * <p><b>What the template writes, and what survives it.</b> Everything the template speaks for is
 * replaced — the responsibilities, the narrative, the org chart, the package's shape, the criteria,
 * both competency panels and the split between them, every one of them stamped {@code TEMPLATE}.
 * The reason for hire and confidentiality are written only where the template states one. What
 * survives is what is not the template's to have an opinion about: every field a template does not
 * carry (the department, the location, the salary band, the strategic priorities, the publication
 * stamp); and a criterion somebody wrote or a document reading filled in, which {@code source} has
 * marked since V7 (as {@code fromBrief}) for exactly this.
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
                position.getDepartment(), position.getLocationCity(), position.getLocationCountry(), body.employmentType(),
                template.getSeniority(), draftedResponsibilities(body), body.narrative(),
                detailsFieldSources(position, body, template.getSeniority())));

        position.applyContext(new MandateContext(
                body.mandateReason() != null ? body.mandateReason() : position.getMandateReason(),
                position.getBusinessDriver(), keptPriorities(position.getStrategicPriorities()),
                body.confidential() != null ? body.confidential() : position.isConfidential(),
                position.getInternalContext(), contextFieldSources(position, body)));

        position.applyReporting(new ReportingStructure(
                seededChart(body), position.getTeamSize(), body.noticeValue(), body.noticeUnit(),
                reportingFieldSources(position, body)));

        position.applyCompensation(new CompensationPackage(
                body.currency(), position.getSalaryMin(), position.getSalaryMax(), body.baseSalaryMode(),
                body.bonusValue(), body.bonusBasis(), body.incentiveType(),
                position.getIncentiveAmount(), body.incentiveVesting(), draftedBenefits(body)));

        position.replaceCriteria(draftedCriteria(position, body));
        position.replaceCompetencies(body.competencies().stream()
                .map(competency -> PositionCompetency.of(competency.panel(), competency.name(),
                        competency.description(), competency.weight(), FieldSource.TEMPLATE))
                .toList(), body.technicalShare());
    }

    private static List<PositionResponsibility> draftedResponsibilities(PositionTemplateBody body) {
        return body.responsibilities().stream()
                .map(text -> PositionResponsibility.of(text, FieldSource.TEMPLATE))
                .toList();
    }

    /**
     * The scalars the template carries are claimed; the ones it leaves alone keep whatever source they
     * had, since each step's write replaces its whole slice of keys and would otherwise drop them.
     */
    private static Map<String, FieldSource> detailsFieldSources(Position position, PositionTemplateBody body,
                                                                Seniority seniority) {
        Map<String, FieldSource> sources = new LinkedHashMap<>();
        keep(sources, position, "department");
        keep(sources, position, "locationCity");
        keep(sources, position, "locationCountry");
        claim(sources, "employmentType", body.employmentType());
        claim(sources, "seniority", seniority);
        claim(sources, "narrative", body.narrative());
        return sources;
    }

    private static Map<String, FieldSource> contextFieldSources(Position position, PositionTemplateBody body) {
        Map<String, FieldSource> sources = new LinkedHashMap<>();
        keep(sources, position, "mandateReason");
        claim(sources, "mandateReason", body.mandateReason());
        keep(sources, position, "businessDriver");
        return sources;
    }

    private static Map<String, FieldSource> reportingFieldSources(Position position, PositionTemplateBody body) {
        Map<String, FieldSource> sources = new LinkedHashMap<>();
        keep(sources, position, "teamSize");
        claim(sources, "noticeValue", body.noticeValue());
        claim(sources, "noticeUnit", body.noticeUnit());
        return sources;
    }

    private static void keep(Map<String, FieldSource> sources, Position position, String key) {
        FieldSource current = position.getFieldSources().get(key);
        if (current != null) {
            sources.put(key, current);
        }
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
     * The template's seats under fresh ids, the mandate seat first — {@code Position#mandateSeatFirst}
     * explains why that matters. A template's ids are its own short strings, meaningless to the brief.
     */
    private static List<PositionOrgNode> seededChart(PositionTemplateBody body) {
        Map<String, UUID> idOfSeat = new HashMap<>();
        body.orgChart().forEach(seat -> idOfSeat.put(seat.id(), UUID.randomUUID()));
        return body.orgChart().stream()
                .sorted(Comparator.comparing(seat -> !seat.mandateSeat()))
                .map(seat -> seat.mandateSeat()
                        ? PositionOrgNode.mandateSeat(idOfSeat.get(seat.id()), idOfSeat.get(seat.parentId()),
                                FieldSource.TEMPLATE)
                        : PositionOrgNode.of(idOfSeat.get(seat.id()), idOfSeat.get(seat.parentId()), seat.title(),
                                null, false, null, null, FieldSource.TEMPLATE))
                .toList();
    }

    /** Copied rather than handed back, because {@code applyContext} replaces the list it reads from. */
    private static List<PositionPriority> keptPriorities(List<PositionPriority> current) {
        return current.stream()
                .map(priority -> PositionPriority.of(priority.getName(), priority.isSelected(), priority.getSource()))
                .toList();
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
