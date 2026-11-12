package app.lightmove.api.position.service;

import app.lightmove.api.common.constant.BenefitFrequency;
import app.lightmove.api.common.constant.BonusBasis;
import app.lightmove.api.common.constant.CriterionMode;
import app.lightmove.api.position.constant.FieldSource;
import app.lightmove.api.position.constant.PositionFieldKeys;
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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Writes a template onto a brief, for seeding and a re-pick alike: what it carries replaces and is stamped
 * {@code TEMPLATE}; what it leaves blank keeps the brief's value and provenance. Merged lists stay within what the
 * step's write accepts, or the step could never be saved again.
 */
final class PositionTemplateApplier {

    /** {@code PutMandateContextRequest} and {@code PutCriteriaRequest}'s own ceilings. */
    static final int MAX_PRIORITIES = 20;
    static final int MAX_CRITERIA = 30;

    private PositionTemplateApplier() {
    }

    static void applyTo(Position position, PositionTemplate template) {
        PositionTemplateBody body = template.getBody();
        Map<String, FieldSource> sources = position.getFieldSources();

        position.applyDetails(new PositionDetails(
                orKept(body.department(), position.getDepartment()),
                position.getLocationCity(), position.getLocationCountry(),
                orKept(body.employmentType(), position.getEmploymentType()),
                orKept(template.getSeniority(), position.getSeniority()),
                body.responsibilities().isEmpty()
                        ? List.copyOf(position.getResponsibilities())
                        : draftedResponsibilities(body),
                orKept(body.narrative(), position.getNarrative()),
                claimed(sources, PositionFieldKeys.DETAILS, Map.of(
                        "department", body.department() != null,
                        "employmentType", body.employmentType() != null,
                        "seniority", template.getSeniority() != null,
                        "narrative", body.narrative() != null))));

        position.applyContext(new MandateContext(
                position.getMandateReason(), position.getBusinessDriver(),
                mergedPriorities(position.getStrategicPriorities(), body.strategicPriorities()),
                position.isConfidential(), position.getInternalContext(),
                claimed(sources, PositionFieldKeys.CONTEXT, Map.of())));

        boolean templateStatesNotice = body.noticeValue() != null;
        boolean templateDrawsChart = (body.reportsTo() != null && !body.reportsTo().isBlank())
                || body.directReports().stream().anyMatch(title -> title != null && !title.isBlank());
        position.applyReporting(new ReportingStructure(
                templateDrawsChart || position.getOrgChart().isEmpty()
                        ? seededChart(body)
                        : List.copyOf(position.getOrgChart()),
                position.getTeamSize(),
                templateStatesNotice ? body.noticeValue() : position.getNoticeValue(),
                templateStatesNotice ? body.noticeUnit() : position.getNoticeUnit(),
                claimed(sources, PositionFieldKeys.REPORTING, Map.of(
                        "noticeValue", templateStatesNotice,
                        "noticeUnit", templateStatesNotice && body.noticeUnit() != null))));

        position.applyCompensation(compensationOf(position, template));

        position.replaceCriteria(draftedCriteria(position, body));
        if (!body.competencies().isEmpty()) {
            position.replaceCompetencies(body.competencies().stream()
                    .map(competency -> PositionCompetency.of(competency.panel(), competency.name(),
                            competency.description(), competency.weight(), FieldSource.TEMPLATE))
                    .toList(), null);
        }
    }

    /**
     * A figure keeps the currency it was typed in, and a band its period: re-picking a template once relabelled
     * a monthly SAR band as the same numbers in annual AED, a twelfth of the package on the client's report.
     * The shared library's currency is nobody's choice, so the workspace's stands; a firm's own template's is.
     */
    private static CompensationPackage compensationOf(Position position, PositionTemplate template) {
        PositionTemplateBody body = template.getBody();
        boolean briefKeepsCurrency = holdsMoney(position) || template.isSharedLibrary();
        boolean bandTyped = position.getSalaryMin() != null || position.getSalaryMax() != null;
        boolean templateStatesBonus = body.bonusValue() != null || body.bonusBasis() != null;
        boolean incentiveTyped = position.getIncentiveAmount() != null;
        return new CompensationPackage(
                briefKeepsCurrency ? position.getCurrency() : body.currency(),
                position.getSalaryMin(), position.getSalaryMax(),
                bandTyped ? position.getBaseSalaryMode() : body.baseSalaryMode(),
                templateStatesBonus ? body.bonusValue() : position.getBonusValue(),
                templateStatesBonus ? body.bonusBasis() : position.getBonusBasis(),
                incentiveTyped ? position.getIncentiveType() : orKept(body.incentiveType(), position.getIncentiveType()),
                position.getIncentiveAmount(),
                orKept(body.incentiveVesting(), position.getIncentiveVesting()),
                body.benefits().isEmpty() ? List.copyOf(position.getBenefits()) : draftedBenefits(body));
    }

    /** Whether any figure on the brief is money in its currency. */
    private static boolean holdsMoney(Position position) {
        return position.getSalaryMin() != null || position.getSalaryMax() != null
                || position.getIncentiveAmount() != null
                || (position.getBonusBasis() == BonusBasis.FIXED_AMOUNT && position.getBonusValue() != null)
                || position.getBenefits().stream().anyMatch(benefit -> benefit.getAmount() != null);
    }

    private static <T> T orKept(T fromTemplate, T kept) {
        return fromTemplate != null ? fromTemplate : kept;
    }

    /**
     * The step's provenance as it stands, with the keys the template now speaks for stamped {@code TEMPLATE}. A
     * key left out would be dropped (see {@code Position.mergeFieldSources}), and a dropped mark reads as a field
     * nobody chose: the next document reading overwrote the consultant's typed city and business driver.
     */
    private static Map<String, FieldSource> claimed(Map<String, FieldSource> current, Set<String> stepKeys,
                                                    Map<String, Boolean> claims) {
        Map<String, FieldSource> slice = new LinkedHashMap<>();
        stepKeys.forEach(key -> {
            if (current.containsKey(key)) {
                slice.put(key, current.get(key));
            }
        });
        claims.forEach((key, isClaimed) -> {
            if (isClaimed) {
                slice.put(key, FieldSource.TEMPLATE);
            }
        });
        return slice;
    }

    private static List<PositionResponsibility> draftedResponsibilities(PositionTemplateBody body) {
        return body.responsibilities().stream()
                .map(text -> PositionResponsibility.of(text, FieldSource.TEMPLATE))
                .toList();
    }

    /** The amount is the mandate's to fill in. */
    private static List<PositionBenefit> draftedBenefits(PositionTemplateBody body) {
        return body.benefits().stream()
                .map(benefit -> PositionBenefit.of(benefit.name(), null,
                        benefit.frequency() == null ? BenefitFrequency.MONTHLY : benefit.frequency(),
                        FieldSource.TEMPLATE))
                .toList();
    }

    /** Rebuilt, never merged; a template naming no manager makes the mandate seat the root. */
    private static List<PositionOrgNode> seededChart(PositionTemplateBody body) {
        boolean hasManager = body.reportsTo() != null && !body.reportsTo().isBlank();
        UUID managerId = hasManager ? UUID.randomUUID() : null;
        UUID seatId = UUID.randomUUID();

        // The mandate seat leads the list — see Position#mandateSeatFirst.
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
     * The template's palette, once per name, keeping existing selection and provenance, then the consultant's own.
     * An unlit priority an earlier template drafted is let go rather than piling up with every re-pick, and the
     * template's unlit ones give way first when the list would pass what a save of the step accepts. Matched on
     * the lower-cased name — the identity {@code PositionService} refuses duplicates on.
     */
    private static List<PositionPriority> mergedPriorities(List<PositionPriority> current,
                                                           List<String> palette) {
        Map<String, PositionPriority> currentByName = current.stream()
                .collect(Collectors.toMap(priority -> normalised(priority.getName()), priority -> priority,
                        (first, second) -> first));
        Map<String, String> paletteByName = new LinkedHashMap<>();
        palette.forEach(name -> paletteByName.putIfAbsent(normalised(name), name));

        List<PositionPriority> merged = new ArrayList<>(paletteByName.entrySet().stream()
                .map(entry -> {
                    PositionPriority existing = currentByName.get(entry.getKey());
                    boolean selected = existing != null && existing.isSelected();
                    FieldSource source = existing != null ? existing.getSource() : FieldSource.TEMPLATE;
                    return PositionPriority.of(entry.getValue(), selected, source);
                })
                .toList());
        Set<String> taken = new HashSet<>(paletteByName.keySet());
        current.stream()
                .filter(priority -> !paletteByName.containsKey(normalised(priority.getName())))
                .filter(priority -> priority.isSelected() || priority.getSource() != FieldSource.TEMPLATE)
                .filter(priority -> taken.add(normalised(priority.getName())))
                .forEach(priority -> merged.add(
                        PositionPriority.of(priority.getName(), priority.isSelected(), priority.getSource())));
        for (int index = merged.size() - 1; merged.size() > MAX_PRIORITIES && index >= 0; index--) {
            PositionPriority priority = merged.get(index);
            if (!priority.isSelected() && priority.getSource() == FieldSource.TEMPLATE) {
                merged.remove(index);
            }
        }
        return merged.size() > MAX_PRIORITIES ? new ArrayList<>(merged.subList(0, MAX_PRIORITIES)) : merged;
    }

    private static String normalised(String name) {
        return name.trim().toLowerCase(Locale.ROOT);
    }

    /** The template's criteria, then everyone else's; the template's give way first past the step's ceiling. */
    private static List<PositionCriterion> draftedCriteria(Position position, PositionTemplateBody body) {
        List<PositionCriterion> kept = position.getCriteria().stream()
                .filter(criterion -> !criterion.isTemplateDrafted())
                .toList();
        List<PositionCriterion> criteria = new ArrayList<>(body.criteria().stream()
                .limit(Math.max(0, MAX_CRITERIA - kept.size()))
                .map(criterion -> PositionCriterion.of(
                        criterion.text(), modeOf(criterion.mode()), FieldSource.TEMPLATE))
                .toList());
        criteria.addAll(kept);
        return criteria;
    }

    private static CriterionMode modeOf(CriterionMode mode) {
        return mode == null ? CriterionMode.REQUIRED : mode;
    }
}
