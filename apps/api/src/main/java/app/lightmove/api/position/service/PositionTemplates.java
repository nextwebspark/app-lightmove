package app.lightmove.api.position.service;

import app.lightmove.api.position.constant.CompetencyPanel;
import app.lightmove.api.position.constant.CriterionMode;
import app.lightmove.api.position.constant.PositionSeniority;
import app.lightmove.api.position.model.PositionCompetency;
import app.lightmove.api.position.model.PositionCriterion;
import app.lightmove.api.position.model.PositionSeed;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * The role-template library: deterministic seed content for a new position brief, matched from the
 * mandate's role title by keyword. This is what makes a fresh brief arrive drafted instead of blank —
 * criteria carry {@code fromBrief = true}, responsibilities are stated, and both competency panels are
 * pre-balanced to exactly 100%, so a seeded brief reads as ready on the day it is created. An AI
 * drafter is planned to replace this matching later; it will feed the same seed shape.
 *
 * <p>Matching contract: case-insensitive substring search over the title, first template whose any
 * keyword matches wins, in declaration order (most specific first). No match falls back to the
 * generic executive template.
 */
final class PositionTemplates {

    private PositionTemplates() {
    }

    static PositionSeed forTitle(String positionTitle) {
        String title = positionTitle.toLowerCase(Locale.ROOT);
        for (Template template : CATALOG) {
            if (template.keywords.stream().anyMatch(title::contains)) {
                return template.seed;
            }
        }
        return GENERIC;
    }

    private record Template(List<String> keywords, PositionSeed seed) {
    }

    private static PositionCriterion required(String text) {
        return PositionCriterion.of(text, CriterionMode.REQUIRED, true);
    }

    private static PositionCriterion preferred(String text) {
        return PositionCriterion.of(text, CriterionMode.PREFERRED, true);
    }

    private static List<PositionCompetency> panels(List<PositionCompetency> technical,
                                                   List<PositionCompetency> behavioural) {
        return Stream.concat(technical.stream(), behavioural.stream()).toList();
    }

    private static PositionCompetency tech(String name, String description, int weight) {
        return PositionCompetency.of(CompetencyPanel.TECHNICAL, name, description, weight);
    }

    private static PositionCompetency beh(String name, String description, int weight) {
        return PositionCompetency.of(CompetencyPanel.BEHAVIOURAL, name, description, weight);
    }

    /** The shared behavioural backbone — executive searches weight leadership traits similarly. */
    private static List<PositionCompetency> executiveBehaviours() {
        return List.of(
                beh("Strategic Leadership", "Sets direction and carries a transformation at group level", 30),
                beh("Stakeholder Influence", "Credible with the shareholder, the board and the executive committee", 25),
                beh("Change Management", "Builds the function's capability while running the day job", 25),
                beh("Resilience under Ambiguity", "Operates through market shifts and incomplete information", 20));
    }

    private static final PositionSeed GENERIC = new PositionSeed(
            PositionSeniority.N_MINUS_1,
            "Chief Executive Officer",
            "This is a senior leadership appointment with significant scope and visibility. The ideal "
                    + "candidate combines a strong operating track record at comparable scale with the "
                    + "presence to influence senior stakeholders from day one.",
            List.of("Functional ownership and delivery",
                    "Leadership team contribution",
                    "Budget and headcount accountability",
                    "Stakeholder and executive reporting"),
            List.of(
                    required("Track record operating at comparable scale and scope"),
                    required("Experience leading through significant organisational change"),
                    preferred("Prior experience in the client's sector or an adjacent one")),
            panels(List.of(
                            tech("Functional Depth", "Command of the discipline the role is accountable for", 40),
                            tech("Commercial Acumen", "Connects functional decisions to commercial outcomes", 30),
                            tech("Operational Excellence", "Runs the function through systems rather than heroics", 30)),
                    executiveBehaviours()));

    private static final List<Template> CATALOG = List.of(
            new Template(List.of("chief financial", "cfo", "finance director", "head of finance"),
                    new PositionSeed(
                            PositionSeniority.C_SUITE,
                            "Group CEO",
                            "The Chief Financial Officer will sit on the executive committee, reporting to "
                                    + "the Group CEO with board-level exposure. This is a hands-on leadership "
                                    + "role for someone who has operated at scale within a diversified or "
                                    + "multi-business-unit environment, and who can bring rigor to the function "
                                    + "while remaining a trusted advisor to the shareholder.",
                            List.of("Group P&L stewardship",
                                    "Capital structure & treasury",
                                    "Board & shareholder reporting",
                                    "Finance transformation"),
                            List.of(
                                    required("Track record leading finance or ops function through M&A or restructuring"),
                                    required("Experience reporting to a board or sovereign shareholder"),
                                    required("Prior P&L ownership above $500M revenue scope"),
                                    preferred("Sector experience relevant to the client's core business")),
                            panels(List.of(
                                            tech("Financial Reporting & Controls", "IFRS reporting, audit readiness and the control environment", 30),
                                            tech("M&A / Restructuring Experience", "Deal execution, carve-outs and post-merger integration", 30),
                                            tech("Treasury & Capital Markets", "Debt structuring, liquidity and lender relationships", 20),
                                            tech("Board & Investor Relations", "Board reporting and shareholder communication", 20)),
                                    executiveBehaviours()))),
            new Template(List.of("chief executive", "ceo", "managing director"),
                    new PositionSeed(
                            PositionSeniority.C_SUITE,
                            "Board of Directors",
                            "The Chief Executive Officer will own the full P&L and set the strategic agenda, "
                                    + "accountable to the board. The ideal candidate has led an organisation of "
                                    + "comparable scale end-to-end and pairs commercial instinct with the "
                                    + "credibility to carry shareholders, regulators and the leadership team.",
                            List.of("Strategy and the multi-year plan",
                                    "Full P&L ownership",
                                    "Executive team leadership",
                                    "Board and shareholder stewardship"),
                            List.of(
                                    required("Full P&L ownership as CEO, MD or business-unit head at comparable scale"),
                                    required("Track record setting and delivering a multi-year growth strategy"),
                                    preferred("Experience working with institutional or family shareholders")),
                            panels(List.of(
                                            tech("Strategy & Growth", "Sets a multi-year agenda and finds the growth in it", 35),
                                            tech("Commercial & P&L Management", "Owns the number and the levers that move it", 35),
                                            tech("Governance & Board Relations", "Runs the board relationship and the governance around it", 30)),
                                    executiveBehaviours()))),
            new Template(List.of("chief operating", "coo", "operations director"),
                    new PositionSeed(
                            PositionSeniority.C_SUITE,
                            "Chief Executive Officer",
                            "The Chief Operating Officer will run day-to-day operations across the group, "
                                    + "translating strategy into delivery. The ideal candidate has scaled "
                                    + "complex, multi-site operations and drives performance through systems "
                                    + "and people rather than heroics.",
                            List.of("Multi-site operational delivery",
                                    "Performance and productivity",
                                    "Supply chain and procurement",
                                    "Operating-model design"),
                            List.of(
                                    required("Led multi-site or multi-country operations at comparable scale"),
                                    required("Track record of measurable operational-efficiency improvement"),
                                    preferred("Experience in a transformation or turnaround context")),
                            panels(List.of(
                                            tech("Operational Strategy & Execution", "Turns the strategy into a delivery plan that holds", 35),
                                            tech("Process & Performance Management", "Runs the operation on measures rather than escalation", 35),
                                            tech("Supply Chain & Procurement", "End-to-end supply, supplier leverage and cost", 30)),
                                    executiveBehaviours()))),
            new Template(List.of("chief technology", "chief information", "cto", "cio", "technology director"),
                    new PositionSeed(
                            PositionSeniority.C_SUITE,
                            "Chief Executive Officer",
                            "The technology leader will own the technology strategy and delivery organisation. "
                                    + "The ideal candidate has built and led engineering or IT at scale, "
                                    + "balancing platform modernisation with commercial pragmatism.",
                            List.of("Technology strategy and architecture",
                                    "Delivery organisation and engineering standards",
                                    "Platform modernisation programme",
                                    "Cybersecurity and technology risk"),
                            List.of(
                                    required("Led a technology organisation of comparable scale"),
                                    required("Track record delivering large platform or transformation programmes"),
                                    preferred("Experience presenting technology strategy at board level")),
                            panels(List.of(
                                            tech("Technology Strategy & Architecture", "Chooses the platform direction and defends the trade-offs", 35),
                                            tech("Delivery & Engineering Leadership", "Ships at scale with engineering standards that survive growth", 35),
                                            tech("Cybersecurity & Risk", "Owns the security posture and the risk conversation around it", 30)),
                                    executiveBehaviours()))),
            new Template(List.of("chief human resources", "chief people", "chro", "people director", "hr director"),
                    new PositionSeed(
                            PositionSeniority.C_SUITE,
                            "Chief Executive Officer",
                            "The people leader will own talent, culture and organisation design across the "
                                    + "group. The ideal candidate has led HR through growth or restructuring at "
                                    + "comparable scale and operates as a true business partner to the CEO.",
                            List.of("Talent and succession across the group",
                                    "Organisation design and change",
                                    "Reward and performance framework",
                                    "Culture and employee experience"),
                            List.of(
                                    required("Led the HR function at comparable organisational scale"),
                                    required("Experience driving organisation design or restructuring"),
                                    preferred("Exposure to executive remuneration and board-level reporting")),
                            panels(List.of(
                                            tech("Talent & Succession", "Builds the bench the strategy will need", 35),
                                            tech("Organisation Design & Change", "Reshapes the organisation and carries people through it", 35),
                                            tech("Reward & Performance", "Reward, performance and the governance around executive pay", 30)),
                                    executiveBehaviours()))),
            new Template(List.of("chief marketing", "cmo", "marketing director"),
                    new PositionSeed(
                            PositionSeniority.C_SUITE,
                            "Chief Executive Officer",
                            "The marketing leader will own brand, demand and customer strategy. The ideal "
                                    + "candidate has built brands and growth engines at comparable scale and "
                                    + "connects marketing investment to commercial outcomes.",
                            List.of("Brand and communications strategy",
                                    "Demand generation and the growth engine",
                                    "Customer insight and segmentation",
                                    "Marketing budget and return"),
                            List.of(
                                    required("Led marketing at comparable scale with clear commercial accountability"),
                                    required("Track record building brand equity and measurable demand"),
                                    preferred("Experience across both digital and traditional channels in-region")),
                            panels(List.of(
                                            tech("Brand & Communications", "Builds and defends the brand across every channel", 35),
                                            tech("Digital & Performance Marketing", "Runs a measurable growth engine, not a campaign calendar", 35),
                                            tech("Customer Insight & Analytics", "Turns customer evidence into where the money goes", 30)),
                                    executiveBehaviours()))),
            new Template(List.of("chief revenue", "chief commercial", "cro", "sales director", "commercial director"),
                    new PositionSeed(
                            PositionSeniority.C_SUITE,
                            "Chief Executive Officer",
                            "The commercial leader will own revenue across all channels. The ideal candidate "
                                    + "has built and led high-performing sales organisations at comparable scale "
                                    + "and brings discipline to pipeline, pricing and key-account growth.",
                            List.of("Revenue ownership across channels",
                                    "Sales organisation and capability",
                                    "Pricing and commercial governance",
                                    "Key-account and partner growth"),
                            List.of(
                                    required("Owned a revenue number of comparable scale"),
                                    required("Track record building or turning around a sales organisation"),
                                    preferred("Established relationships in the client's key markets")),
                            panels(List.of(
                                            tech("Sales Strategy & Execution", "Sets the commercial plan and holds the organisation to it", 35),
                                            tech("Key Account & Channel Management", "Grows the accounts and channels the number depends on", 35),
                                            tech("Pricing & Commercial Operations", "Pricing discipline, pipeline hygiene and deal governance", 30)),
                                    executiveBehaviours()))));
}
