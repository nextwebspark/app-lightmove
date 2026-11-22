package app.lightmove.api.position.service;

import app.lightmove.api.core.llm.model.Pseudonyms;
import java.util.Map;

/** The position description every extraction Try it reads: an invented company and an invented role. */
final class FictionalPositionDescription {

    static final String TEXT = """
            Position description: Chief Operating Officer
            Company: Larkfield Marine Services (an invented company)

            Department: Operations
            Location: Muscat, Oman
            Employment type: Permanent, full-time
            Reports to: Chief Executive Officer
            Notice period: 3 months

            About the role
            This is a newly created role. Larkfield Marine Services runs port and vessel support at four harbours and
            is expanding into two more over the next three years. The founder has run operations alongside the
            chief executive role until now, and the board wants a dedicated executive to own delivery at every site.

            Why the role exists
            Growth has outpaced the operating model: each harbour plans and reports its own way. The board's
            priorities are one operating standard across all sites, a safety record with no lost-time incidents,
            and readiness to open the two new harbours on schedule.

            Team
            The Chief Operating Officer leads a team of about 420 people. Direct reports: Head of Port Operations,
            Head of Fleet Maintenance, Head of Health and Safety, Head of Procurement.

            Key responsibilities
            - Run day-to-day operations across all harbours
            - Introduce one operating standard and one set of performance measures
            - Lead the opening of the two new harbour sites
            - Own the operations budget and the capital plan for the fleet
            - Build a succession bench for each head of function

            Requirements
            - Required: at least ten years leading multi-site operations in ports, shipping or logistics
            - Required: has held full budget responsibility for an operation of 300 people or more
            - Preferred: experience opening a new site in the Gulf region
            - Preferred: a degree in engineering or maritime studies

            Competencies
            Technical: Multi-site operations management (40%), Safety and compliance (30%), Capital planning (30%)
            Behavioural: Leading change (50%), Developing leaders (30%), Board communication (20%)
            """;

    static final Pseudonyms NOTHING_REDACTED = new Pseudonyms(Map.of());

    private FictionalPositionDescription() {
    }
}
