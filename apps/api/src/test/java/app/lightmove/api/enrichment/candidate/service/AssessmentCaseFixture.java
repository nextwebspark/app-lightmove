package app.lightmove.api.enrichment.candidate.service;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** One invented assessment case and an answer a careful drafter would give it, for the tests to bend. */
final class AssessmentCaseFixture {

    static final JsonMapper JSON = JsonMapper.builder().build();

    static final String EMAIL = "layla.haddad@example.com";
    static final String PHONE = "+971 50 555 0147";

    private static final String INPUT = """
            {"profile": {"fullName": "Layla Haddad", "title": "Chief Financial Officer",
               "companyName": "Northwind Logistics", "locationCity": "Dubai",
               "locationCountry": "United Arab Emirates",
               "summary": "Finance chief of a listed regional logistics group.",
               "career": [{"company": "Northwind Logistics", "title": "Chief Financial Officer",
                           "period": "2021 - present", "location": "Dubai"}],
               "education": [], "skills": ["Treasury"], "languages": ["English", "Arabic"]},
             "notes": [
               {"date": "2026-10-02", "kind": "general",
                "body": "A former colleague describes her as calm in front of the board."},
               {"date": "2026-10-14", "kind": "call", "outcome": "connected",
                "body": "Spoke for twenty minutes. She ran the 2023 refinancing herself, USD 310m across four \
            lenders. Reach her on layla.haddad@example.com or +971 50 555 0147."}],
             "transcripts": [
               {"date": "2026-10-20", "title": "First interview", "lines": [
                 {"at": "00:40", "speaker": "Omar Nasser", "team": true,
                  "text": "You have run three turnarounds, I hear."},
                 {"at": "03:10", "speaker": "Layla Haddad",
                  "text": "When we bought the two carriers I moved both finance teams onto one close calendar \
            in four months."},
                 {"at": "07:45", "speaker": "Layla Haddad", "text": "I am a strong leader."}]}],
             "documents": [
               {"title": "Board paper March 2024", "category": "other",
                "text": "Proposes a single treasury policy and a hedging limit of 60% of forecast fuel spend."}],
             "brief": {"roleTitle": "Group Chief Financial Officer", "seniority": "C-Suite",
               "department": "Finance", "locationCity": "Riyadh", "locationCountry": "Saudi Arabia",
               "narrative": "Runs finance for a family-owned industrial group preparing to list.",
               "responsibilities": ["Lead the group's listing readiness"],
               "businessDriver": "The group plans a listing.",
               "strategicPriorities": ["Listing readiness"],
               "criteria": [{"text": "Has been the finance chief of a listed company", "mode": "REQUIRED"}],
               "competencies": [
                 {"panel": "TECHNICAL", "name": "Capital markets",
                  "description": "Raising and refinancing debt and equity", "weight": 50},
                 {"panel": "TECHNICAL", "name": "Financial reporting",
                  "description": "Public-company reporting and controls", "weight": 50},
                 {"panel": "BEHAVIOURAL", "name": "Leading change",
                  "description": "Takes a team through a change it did not ask for", "weight": 60},
                 {"panel": "BEHAVIOURAL", "name": "Board presence",
                  "description": "Holds the confidence of a board and its owners", "weight": 40}],
               "technicalShare": 60}}
            """;

    private static final String EXPECTED = """
            {"competencies": {
               "T1": {"band": [7, 9], "restsOn": "note"},
               "T2": {"band": null},
               "B1": {"band": [6, 8], "restsOn": "call"},
               "B2": {"band": [3, 7], "selfReportOnly": true}},
             "neverInAnswer": ["layla.haddad@example.com", "+971 50 555 0147"],
             "notEvidence": ["three turnarounds"]}
            """;

    private static final String ANSWER = """
            {"scores": [
               {"id": "T1", "score": 8, "evidence": "A call note of 14 Oct 2026 records that she ran the 2023 \
            refinancing herself, USD 310m across four lenders."},
               {"id": "T2", "score": null, "evidence": null},
               {"id": "B1", "score": 7, "evidence": "On the call 20 Oct 2026, 03:10 in, she describes moving \
            both finance teams onto one close calendar in four months."},
               {"id": "B2", "score": 6, "evidence": "Inferred from her career as finance chief of a listed \
            group since 2021; her own claim to lead well has no example behind it, so confirm at interview."}],
             "report": {"careerSummary": "A finance chief of a listed logistics group.",
               "coreValue": "She has refinanced debt at scale.", "concerns": "Reporting is not evidenced.",
               "workingStyle": "Calm in front of a board, by one account.", "motivation": "Not yet established."}}
            """;

    private AssessmentCaseFixture() {
    }

    static ObjectNode input() {
        return (ObjectNode) JSON.readTree(INPUT);
    }

    static ObjectNode expected() {
        return (ObjectNode) JSON.readTree(EXPECTED);
    }

    static ObjectNode answer() {
        return (ObjectNode) JSON.readTree(ANSWER);
    }

    /** The answer's entry for one competency, to change in place. */
    static ObjectNode scoreOf(JsonNode answer, String id) {
        for (JsonNode entry : answer.get("scores")) {
            if (id.equals(entry.get("id").asString())) {
                return (ObjectNode) entry;
            }
        }
        throw new AssertionError("No score for " + id);
    }
}
