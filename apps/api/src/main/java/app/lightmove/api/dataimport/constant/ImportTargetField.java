package app.lightmove.api.dataimport.constant;

import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import java.util.List;

/**
 * Every field of a Companies-grid row a spreadsheet column can be mapped onto — the catalogue the
 * mapping step offers and the heuristic matches against.
 *
 * <p>One enum rather than a company list and a candidate list, because a mapping is a flat decision
 * per header: "Employer" and "Ethnicity" are answered from the same dropdown, and the target is what
 * says which half of the row each one lands on. {@link #synonyms} are what the heuristic matches
 * before it ever reaches token overlap, and they are the real ones — the headers consultants' files
 * actually carry, not a tidy alias per field.
 *
 * <p>What is deliberately absent: anything the mandate decides rather than the file. Triage stage is
 * not here because the import lands every company In universe, and the candidate's status is not here
 * because a spreadsheet's "status" column is the sender's pipeline, not this mandate's.
 */
public enum ImportTargetField {

    COMPANY_NAME(CustomColumnTarget.COMPANY, "companyName", "Company",
            List.of("company", "company name", "companyname", "organisation", "organization",
                    "org", "employer", "account", "account name", "firm", "business", "entity")),
    COMPANY_INDUSTRY(CustomColumnTarget.COMPANY, "companyIndustry", "Sector",
            List.of("industry", "sector", "vertical", "market", "category")),
    COMPANY_COUNTRY(CustomColumnTarget.COMPANY, "companyCountry", "Country",
            List.of("country", "company country", "hq country", "headquarters country", "nation",
                    "geo", "geography", "market country")),
    COMPANY_CITY(CustomColumnTarget.COMPANY, "companyCity", "City",
            List.of("city", "company city", "hq", "hq city", "headquarters", "town", "location",
                    "company location")),
    COMPANY_EMPLOYEES(CustomColumnTarget.COMPANY, "companyEmployees", "Employees",
            List.of("employees", "employee count", "headcount", "head count", "staff", "size",
                    "company size", "num employees", "no of employees")),
    COMPANY_REVENUE(CustomColumnTarget.COMPANY, "companyRevenue", "Revenue",
            List.of("revenue", "annual revenue", "turnover", "sales", "revenue usd")),
    COMPANY_WEBSITE(CustomColumnTarget.COMPANY, "companyWebsite", "Website",
            List.of("website", "web site", "url", "domain", "company website", "site", "homepage")),
    COMPANY_LINKEDIN(CustomColumnTarget.COMPANY, "companyLinkedin", "Company LinkedIn",
            List.of("company linkedin", "company linkedin url", "linkedin company",
                    "company profile", "linkedin company page")),
    COMPANY_FOUNDED(CustomColumnTarget.COMPANY, "companyFounded", "Founded",
            List.of("founded", "founded year", "year founded", "established", "inception",
                    "incorporated")),
    COMPANY_DESCRIPTION(CustomColumnTarget.COMPANY, "companyDescription", "Description",
            List.of("description", "company description", "about", "overview", "summary of company",
                    "short description", "blurb")),
    COMPANY_NOTE(CustomColumnTarget.COMPANY, "companyNote", "Company note",
            List.of("company note", "company notes", "company comment", "company remarks")),

    CANDIDATE_NAME(CustomColumnTarget.CANDIDATE, "candidateName", "Name",
            List.of("name", "full name", "fullname", "candidate", "candidate name", "person",
                    "executive", "contact", "contact name", "individual")),
    CANDIDATE_FIRST_NAME(CustomColumnTarget.CANDIDATE, "candidateFirstName", "First name",
            List.of("first name", "firstname", "given name", "forename", "first")),
    CANDIDATE_LAST_NAME(CustomColumnTarget.CANDIDATE, "candidateLastName", "Last name",
            List.of("last name", "lastname", "surname", "family name", "last")),
    CANDIDATE_TITLE(CustomColumnTarget.CANDIDATE, "candidateTitle", "Title",
            List.of("title", "job title", "position", "role", "designation", "current title",
                    "current role")),
    CANDIDATE_SENIORITY(CustomColumnTarget.CANDIDATE, "candidateSeniority", "Level",
            List.of("level", "seniority", "seniority level", "tier", "grade", "band", "layer")),
    CANDIDATE_EMAIL(CustomColumnTarget.CANDIDATE, "candidateEmail", "Email",
            List.of("email", "e mail", "email address", "work email", "business email",
                    "contact email", "mail")),
    CANDIDATE_PHONE(CustomColumnTarget.CANDIDATE, "candidatePhone", "Phone",
            List.of("phone", "telephone", "mobile", "cell", "phone number", "contact number", "tel")),
    CANDIDATE_LINKEDIN(CustomColumnTarget.CANDIDATE, "candidateLinkedin", "LinkedIn",
            List.of("linkedin", "linkedin url", "linkedin profile", "li url", "profile url",
                    "linkedin link")),
    CANDIDATE_COUNTRY(CustomColumnTarget.CANDIDATE, "candidateCountry", "Person country",
            List.of("candidate country", "person country", "based in", "resident country",
                    "current country", "lives in")),
    CANDIDATE_CITY(CustomColumnTarget.CANDIDATE, "candidateCity", "Person city",
            List.of("candidate city", "person city", "current city", "based city", "residence")),
    CANDIDATE_NATIONALITY(CustomColumnTarget.CANDIDATE, "candidateNationality", "Nationality",
            List.of("nationality", "passport", "citizenship", "national")),
    CANDIDATE_YEARS_EXPERIENCE(CustomColumnTarget.CANDIDATE, "candidateYearsExperience", "Experience",
            List.of("years experience", "experience", "yrs experience", "years of experience",
                    "total experience", "exp")),
    CANDIDATE_SUMMARY(CustomColumnTarget.CANDIDATE, "candidateSummary", "Profile summary",
            List.of("profile", "profile summary", "candidate summary", "bio", "biography",
                    "background")),
    CANDIDATE_NOTE(CustomColumnTarget.CANDIDATE, "candidateNote", "Note",
            List.of("note", "notes", "comment", "comments", "remarks", "researcher note")),
    CANDIDATE_CURRENCY(CustomColumnTarget.CANDIDATE, "candidateCurrency", "Currency",
            List.of("currency", "ccy", "salary currency", "compensation currency")),
    CANDIDATE_BASE_SALARY(CustomColumnTarget.CANDIDATE, "candidateBaseSalary", "Base salary",
            List.of("base", "base salary", "salary", "basic", "annual salary", "fixed pay")),
    CANDIDATE_BONUS(CustomColumnTarget.CANDIDATE, "candidateBonus", "Bonus",
            List.of("bonus", "annual bonus", "variable", "incentive", "sti")),
    CANDIDATE_ALLOWANCES(CustomColumnTarget.CANDIDATE, "candidateAllowances", "Allowances",
            List.of("allowances", "allowance", "benefits", "housing allowance", "other allowances")),
    CANDIDATE_LONG_TERM_INCENTIVE(CustomColumnTarget.CANDIDATE, "candidateLongTermIncentive", "LTIP",
            List.of("ltip", "lti", "long term incentive", "equity", "shares", "stock")),
    CANDIDATE_NOTICE_PERIOD(CustomColumnTarget.CANDIDATE, "candidateNoticePeriod", "Notice period",
            List.of("notice", "notice period", "availability", "notice weeks", "notice months"));

    private final CustomColumnTarget target;
    private final String wireToken;
    private final String label;
    private final List<String> synonyms;

    ImportTargetField(CustomColumnTarget target, String wireToken, String label, List<String> synonyms) {
        this.target = target;
        this.wireToken = wireToken;
        this.label = label;
        this.synonyms = synonyms;
    }

    /** Which half of the row this field lands on. */
    public CustomColumnTarget target() {
        return target;
    }

    /** The wire value; the mapping dropdown speaks these. */
    public String value() {
        return wireToken;
    }

    /** What the mapping step shows beside the file's own header. */
    public String label() {
        return label;
    }

    /** Lower-cased, space-separated header spellings this field answers to. */
    public List<String> synonyms() {
        return synonyms;
    }

    /** Resolve a wire value to its field, or {@code null} if unknown. */
    public static ImportTargetField fromValue(String value) {
        for (ImportTargetField field : values()) {
            if (field.wireToken.equals(value)) {
                return field;
            }
        }
        return null;
    }
}
