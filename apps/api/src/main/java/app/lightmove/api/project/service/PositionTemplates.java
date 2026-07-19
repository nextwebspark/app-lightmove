package app.lightmove.api.project.service;

import app.lightmove.api.project.constant.CompetencyPanel;
import app.lightmove.api.project.constant.CriterionMode;
import app.lightmove.api.project.model.CompetencyRow;
import app.lightmove.api.project.model.PositionCriterion;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The role-template library: deterministic seed content for a new position brief, matched from the
 * project's position title by keyword. This is what makes a fresh brief arrive "drafted" instead of
 * blank — criteria carry {@code fromBrief = true} and both competency panels are pre-balanced to
 * exactly 100%, so a seeded position is lockable as-is. An AI drafter is planned to replace this
 * matching in a later phase; it will feed the same seed shape.
 *
 * <p>Matching contract: case-insensitive substring search over the title, first template whose any
 * keyword matches wins, in declaration order (most specific first). No match falls back to the
 * generic executive template.
 */
final class PositionTemplates {

    private PositionTemplates() {
    }

    /** Everything a template decides. Scalars the template doesn't know (location…) stay null. */
    record TemplateSeed(
            String reportsTo,
            String narrative,
            List<PositionCriterion> criteria,
            List<CompetencyRow> competencies
    ) {
    }

    static TemplateSeed forTitle(String positionTitle) {
        String title = positionTitle.toLowerCase(Locale.ROOT);
        for (Template template : CATALOG) {
            if (template.keywords.stream().anyMatch(title::contains)) {
                return template.seed;
            }
        }
        return GENERIC;
    }

    private record Template(List<String> keywords, TemplateSeed seed) {
    }

    private static PositionCriterion required(String text) {
        return PositionCriterion.of(text, CriterionMode.REQUIRED, true);
    }

    private static PositionCriterion preferred(String text) {
        return PositionCriterion.of(text, CriterionMode.PREFERRED, true);
    }

    private static List<CompetencyRow> panels(List<CompetencyRow> technical, List<CompetencyRow> behavioural) {
        return Stream.concat(technical.stream(), behavioural.stream()).toList();
    }

    private static CompetencyRow tech(String name, int weight) {
        return CompetencyRow.of(CompetencyPanel.TECHNICAL, name, weight);
    }

    private static CompetencyRow beh(String name, int weight) {
        return CompetencyRow.of(CompetencyPanel.BEHAVIOURAL, name, weight);
    }

    /** The shared behavioural backbone — executive searches weight leadership traits similarly. */
    private static List<CompetencyRow> executiveBehaviours() {
        return List.of(
                beh("Strategic Leadership", 30),
                beh("Stakeholder Influence", 25),
                beh("Change Management", 25),
                beh("Resilience under Ambiguity", 20));
    }

    private static final TemplateSeed GENERIC = new TemplateSeed(
            "Chief Executive Officer",
            "This is a senior leadership appointment with significant scope and visibility. The ideal "
                    + "candidate combines a strong operating track record at comparable scale with the "
                    + "presence to influence senior stakeholders from day one.",
            List.of(
                    required("Track record operating at comparable scale and scope"),
                    required("Experience leading through significant organisational change"),
                    preferred("Prior experience in the client's sector or an adjacent one")),
            panels(List.of(
                            tech("Functional Depth", 40),
                            tech("Commercial Acumen", 30),
                            tech("Operational Excellence", 30)),
                    executiveBehaviours()));

    private static final List<Template> CATALOG = List.of(
            new Template(List.of("chief financial", "cfo", "finance director", "head of finance"),
                    new TemplateSeed(
                            "Group CEO",
                            "The Chief Financial Officer will sit on the executive committee, reporting to "
                                    + "the Group CEO with board-level exposure. This is a hands-on leadership "
                                    + "role for someone who has operated at scale within a diversified or "
                                    + "multi-business-unit environment, and who can bring rigor to the function "
                                    + "while remaining a trusted advisor to the shareholder.",
                            List.of(
                                    required("Track record leading finance or ops function through M&A or restructuring"),
                                    required("Experience reporting to a board or sovereign shareholder"),
                                    required("Prior P&L ownership above $500M revenue scope"),
                                    preferred("Sector experience relevant to the client's core business")),
                            panels(List.of(
                                            tech("Financial Reporting & Controls", 30),
                                            tech("M&A / Restructuring Experience", 30),
                                            tech("Treasury & Capital Markets", 20),
                                            tech("Board & Investor Relations", 20)),
                                    executiveBehaviours()))),
            new Template(List.of("chief executive", "ceo", "managing director"),
                    new TemplateSeed(
                            "Board of Directors",
                            "The Chief Executive Officer will own the full P&L and set the strategic agenda, "
                                    + "accountable to the board. The ideal candidate has led an organisation of "
                                    + "comparable scale end-to-end and pairs commercial instinct with the "
                                    + "credibility to carry shareholders, regulators and the leadership team.",
                            List.of(
                                    required("Full P&L ownership as CEO, MD or business-unit head at comparable scale"),
                                    required("Track record setting and delivering a multi-year growth strategy"),
                                    preferred("Experience working with institutional or family shareholders")),
                            panels(List.of(
                                            tech("Strategy & Growth", 35),
                                            tech("Commercial & P&L Management", 35),
                                            tech("Governance & Board Relations", 30)),
                                    executiveBehaviours()))),
            new Template(List.of("chief operating", "coo", "operations director"),
                    new TemplateSeed(
                            "Chief Executive Officer",
                            "The Chief Operating Officer will run day-to-day operations across the group, "
                                    + "translating strategy into delivery. The ideal candidate has scaled "
                                    + "complex, multi-site operations and drives performance through systems "
                                    + "and people rather than heroics.",
                            List.of(
                                    required("Led multi-site or multi-country operations at comparable scale"),
                                    required("Track record of measurable operational-efficiency improvement"),
                                    preferred("Experience in a transformation or turnaround context")),
                            panels(List.of(
                                            tech("Operational Strategy & Execution", 35),
                                            tech("Process & Performance Management", 35),
                                            tech("Supply Chain & Procurement", 30)),
                                    executiveBehaviours()))),
            new Template(List.of("chief technology", "chief information", "cto", "cio", "technology director"),
                    new TemplateSeed(
                            "Chief Executive Officer",
                            "The technology leader will own the technology strategy and delivery organisation. "
                                    + "The ideal candidate has built and led engineering or IT at scale, "
                                    + "balancing platform modernisation with commercial pragmatism.",
                            List.of(
                                    required("Led a technology organisation of comparable scale"),
                                    required("Track record delivering large platform or transformation programmes"),
                                    preferred("Experience presenting technology strategy at board level")),
                            panels(List.of(
                                            tech("Technology Strategy & Architecture", 35),
                                            tech("Delivery & Engineering Leadership", 35),
                                            tech("Cybersecurity & Risk", 30)),
                                    executiveBehaviours()))),
            new Template(List.of("chief human resources", "chief people", "chro", "people director", "hr director"),
                    new TemplateSeed(
                            "Chief Executive Officer",
                            "The people leader will own talent, culture and organisation design across the "
                                    + "group. The ideal candidate has led HR through growth or restructuring at "
                                    + "comparable scale and operates as a true business partner to the CEO.",
                            List.of(
                                    required("Led the HR function at comparable organisational scale"),
                                    required("Experience driving organisation design or restructuring"),
                                    preferred("Exposure to executive remuneration and board-level reporting")),
                            panels(List.of(
                                            tech("Talent & Succession", 35),
                                            tech("Organisation Design & Change", 35),
                                            tech("Reward & Performance", 30)),
                                    executiveBehaviours()))),
            new Template(List.of("chief marketing", "cmo", "marketing director"),
                    new TemplateSeed(
                            "Chief Executive Officer",
                            "The marketing leader will own brand, demand and customer strategy. The ideal "
                                    + "candidate has built brands and growth engines at comparable scale and "
                                    + "connects marketing investment to commercial outcomes.",
                            List.of(
                                    required("Led marketing at comparable scale with clear commercial accountability"),
                                    required("Track record building brand equity and measurable demand"),
                                    preferred("Experience across both digital and traditional channels in-region")),
                            panels(List.of(
                                            tech("Brand & Communications", 35),
                                            tech("Digital & Performance Marketing", 35),
                                            tech("Customer Insight & Analytics", 30)),
                                    executiveBehaviours()))),
            new Template(List.of("chief revenue", "chief commercial", "cro", "sales director", "commercial director"),
                    new TemplateSeed(
                            "Chief Executive Officer",
                            "The commercial leader will own revenue across all channels. The ideal candidate "
                                    + "has built and led high-performing sales organisations at comparable scale "
                                    + "and brings discipline to pipeline, pricing and key-account growth.",
                            List.of(
                                    required("Owned a revenue number of comparable scale"),
                                    required("Track record building or turning around a sales organisation"),
                                    preferred("Established relationships in the client's key markets")),
                            panels(List.of(
                                            tech("Sales Strategy & Execution", 35),
                                            tech("Key Account & Channel Management", 35),
                                            tech("Pricing & Commercial Operations", 30)),
                                    executiveBehaviours()))));
}
