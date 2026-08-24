package app.lightmove.api.triagecompany;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Capturing a company from a web page into a mandate — what the browser extension writes.
 *
 * <p>The behaviour that matters is the fork. A page the Apollo universe publishes must produce a row
 * <b>indistinguishable</b> from one the Strategy screen wrote, snapshot and all, or the same company
 * captured here and added there becomes two rows in one mandate. A page it does not publish must still
 * produce a row, keyed on its domain — that is the whole reason the extension exists, since most of
 * what a consultant browses in the GCC is not in the universe.
 *
 * <p>The rest is the promotion rule: a capture may move a company up and never down, and may not
 * revive one the team has declined.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class CompanyCaptureIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    private ApolloUniverse universe;

    @BeforeEach
    void freshUniverse() {
        universe = new ApolloUniverse(db);
        universe.reset();
    }

    @Test
    @DisplayName("a captured page the universe publishes is filed under its Apollo identity, with Apollo's own figures")
    void captureResolvesAgainstTheUniverse() throws Exception {
        String admin = adminOf("Capture Resolve Firm");
        String projectId = project(admin);
        universe.company("a1", "Al Rawabi Dairy Company")
                .industry("food & beverages").country("United Arab Emirates").city("Dubai")
                .employees(1200).website("https://www.alrawabidairy.ae").insert();

        // The page said something different about almost every field. None of it is stored: the
        // snapshot is resolved server-side, exactly as "Add to Universe" resolves it.
        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"inUniverse","companyName":"Al Rawabi (as the page called it)",
                                 "website":"https://alrawabidairy.ae/en/about","industry":"Dairy",
                                 "companyCity":"Sharjah","numEmployees":9,
                                 "sourceUrl":"https://alrawabidairy.ae/en/about"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apolloAccountId").value("a1"))
                .andExpect(jsonPath("$.origin").value("STRATEGY"))
                .andExpect(jsonPath("$.companyName").value("Al Rawabi Dairy Company"))
                .andExpect(jsonPath("$.companyCity").value("Dubai"))
                .andExpect(jsonPath("$.numEmployees").value(1200));
    }

    @Test
    @DisplayName("a company the universe does not publish is captured under its domain, from the page")
    void captureFallsBackToThePage() throws Exception {
        String admin = adminOf("Capture Fallback Firm");
        String projectId = project(admin);

        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"shortlisted","companyName":"Desert Foods LLC",
                                 "website":"https://www.desertfoods.qa/about","industry":"Food",
                                 "companyCity":"Doha","companyCountry":"Qatar","numEmployees":140,
                                 "tags":["Family owned","family owned "],
                                 "note":"Met at Gulfood.",
                                 "sourceUrl":"https://www.desertfoods.qa/about"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apolloAccountId").doesNotExist())
                .andExpect(jsonPath("$.origin").value("CAPTURE"))
                .andExpect(jsonPath("$.companyName").value("Desert Foods LLC"))
                .andExpect(jsonPath("$.status").value("shortlisted"))
                .andExpect(jsonPath("$.note").value("Met at Gulfood."))
                // Tags are de-duplicated case-insensitively, so the popup cannot produce two chips
                // that come back as one row.
                .andExpect(jsonPath("$.tags.length()").value(1));

        assertThat(db.queryForObject(
                "SELECT capture_key FROM app_lm_project_triage_company WHERE company_name = 'Desert Foods LLC'",
                String.class))
                .isEqualTo("desertfoods.qa");
    }

    @Test
    @DisplayName("capturing the same page twice is one row, and the second capture may only promote it")
    void captureIsIdempotentAndPromotesOnly() throws Exception {
        String admin = adminOf("Capture Idempotent Firm");
        String projectId = project(admin);

        String first = capture(admin, projectId, "inUniverse");
        String promoted = capture(admin, projectId, "shortlisted");
        assertThat(promoted).isEqualTo(first);

        mvc.perform(get(triageUrl(projectId)).param("status", "shortlisted")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(1));

        // Back to the universe button on an already-shortlisted company. A capture is a coarse
        // signal from a popup; demoting on it would let a stray click undo a triage decision.
        capture(admin, projectId, "inUniverse");
        mvc.perform(get(triageUrl(projectId)).param("status", "shortlisted")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(1));
        mvc.perform(get(triageUrl(projectId)).param("status", "inUniverse")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.totalCount").value(0));
    }

    @Test
    @DisplayName("a company the mandate declined is refused, not quietly revived")
    void captureWillNotResurrectADeclinedCompany() throws Exception {
        String admin = adminOf("Capture Declined Firm");
        String projectId = project(admin);

        String triageId = capture(admin, projectId, "inUniverse");
        mvc.perform(patch(triageUrl(projectId) + "/" + triageId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"declined"}"""))
                .andExpect(status().isOk());

        MvcResult refused = mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CAPTURE_BODY.formatted("inUniverse")))
                .andExpect(status().isConflict())
                .andReturn();
        assertThat(codeOf(refused)).isEqualTo("TRIAGE_COMPANY_DECLINED");
    }

    @Test
    @DisplayName("a capture with no domain is refused: there would be nothing to key the row on")
    void captureNeedsADomainWhenTheUniverseHasNoMatch() throws Exception {
        String admin = adminOf("Capture No Domain Firm");
        String projectId = project(admin);

        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"inUniverse","companyName":"Nameless Holding"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.detail").value(
                        "A website is required for a company that is not in the universe."));
    }

    @Test
    @DisplayName("declined is not a destination a capture may name")
    void captureCannotDeclineACompany() throws Exception {
        String admin = adminOf("Capture Decline Guard Firm");
        String projectId = project(admin);

        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CAPTURE_BODY.formatted("declined")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("an off-limits company cannot be captured, however the page describes it")
    void captureRespectsTheOffLimitsBar() throws Exception {
        String admin = adminOf("Capture Off Limits Firm");
        String projectId = project(admin);
        universe.company("a9", "Barred Holding").website("https://barred.ae").employees(500).insert();

        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/off-limits")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["a9"]}"""))
                .andExpect(status().isOk());

        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"inUniverse","companyName":"Barred Holding",
                                 "website":"https://barred.ae"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This company is off-limits for this mandate."));
    }

    @Test
    @DisplayName("an unresolvable Apollo id falls through to the page's own identity, bar and all")
    void captureFallsThroughWhenTheNamedAccountDoesNotResolve() throws Exception {
        String admin = adminOf("Capture Stale Id Firm");
        String projectId = project(admin);
        universe.company("a9", "Barred Holding").website("https://barred.ae").employees(500).insert();

        mvc.perform(put("/api/v1/projects/" + projectId + "/strategy/off-limits")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"apolloAccountIds":["a9"]}"""))
                .andExpect(status().isOk());

        // The hole this closes: an unresolvable id used to short-circuit the web-identity lookup, so a
        // company the universe publishes was filed as a capture — under a name the request chose, and
        // without ever meeting the off-limits bar. A stale id must not be a way past either rule.
        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"inUniverse","apolloAccountId":"no-longer-published",
                                 "companyName":"Totally Different Name Ltd","website":"https://barred.ae",
                                 "numEmployees":99999}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("This company is off-limits for this mandate."));
    }

    @Test
    @DisplayName("a stale Apollo id still resolves the snapshot from the universe, not from the page")
    void captureWithAStaleIdStillTakesApollosSnapshot() throws Exception {
        String admin = adminOf("Capture Stale Snapshot Firm");
        String projectId = project(admin);
        universe.company("a3", "Zenith Industrial").website("https://zenith-industrial.sa")
                .employees(800).industry("industrial").insert();

        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"inUniverse","apolloAccountId":"stale-and-gone",
                                 "companyName":"Whatever The Page Said","website":"https://zenith-industrial.sa",
                                 "numEmployees":7}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.apolloAccountId").value("a3"))
                .andExpect(jsonPath("$.origin").value("STRATEGY"))
                .andExpect(jsonPath("$.companyName").value("Zenith Industrial"))
                .andExpect(jsonPath("$.numEmployees").value(800));
    }

    @Test
    @DisplayName("a re-capture leaves a note somebody already wrote alone")
    void captureDoesNotEraseAnExistingNote() throws Exception {
        String admin = adminOf("Capture Note Firm");
        String projectId = project(admin);

        String triageId = capture(admin, projectId, "inUniverse");
        mvc.perform(patch(triageUrl(projectId) + "/" + triageId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":"CFO retiring Q3, confirmed by Sara."}"""))
                .andExpect(status().isOk());

        // The popup's note box starts empty on every open, so the ordinary "capture it again to
        // shortlist it" gesture sends no note at all. Omitting a field is not asking to erase it.
        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CAPTURE_BODY.formatted("shortlisted")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("shortlisted"))
                .andExpect(jsonPath("$.note").value("CFO retiring Q3, confirmed by Sara."));

        // An explicit empty string is how a note is cleared, and that still works.
        mvc.perform(patch(triageUrl(projectId) + "/" + triageId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"note":""}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.note").doesNotExist());
    }

    @Test
    @DisplayName("the universe lookup matches on a domain or a LinkedIn slug, however the page spelled it")
    void resolveMatchesOnEitherIdentity() throws Exception {
        String admin = adminOf("Capture Resolve Lookup Firm");
        universe.company("a2", "Zenith Industrial")
                .website("http://www.zenith-industrial.sa/en")
                .linkedin("https://www.linkedin.com/company/zenith-industrial/")
                .employees(800).insert();

        mvc.perform(get("/api/v1/companies/resolve").param("domain", "https://zenith-industrial.sa/careers")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.company.apolloAccountId").value("a2"));

        // A country subdomain and a trailing sub-page are the same company page.
        mvc.perform(get("/api/v1/companies/resolve")
                        .param("linkedinUrl", "https://sa.linkedin.com/company/zenith-industrial/about")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(true))
                .andExpect(jsonPath("$.company.apolloAccountId").value("a2"));

        // A miss is an answer, not an error: the capture goes ahead with the page's own fields.
        mvc.perform(get("/api/v1/companies/resolve").param("domain", "https://nowhere.example")
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matched").value(false))
                .andExpect(jsonPath("$.company").doesNotExist());
    }

    @Test
    @DisplayName("a client representative may read a mandate's triage but never capture into it")
    void captureIsRefusedToAReadOnlySeat() throws Exception {
        String admin = adminOf("Capture Client Firm");
        String projectId = project(admin);
        String representative = attachRepresentative(admin, projectId);

        mvc.perform(get(triageUrl(projectId)).header("Authorization", "Bearer " + representative))
                .andExpect(status().isOk());

        mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + representative)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CAPTURE_BODY.formatted("inUniverse")))
                .andExpect(status().isForbidden());
    }

    /** One company, described the way a page would describe it. The status is the caller's to fill in. */
    private static final String CAPTURE_BODY = """
            {"status":"%s","companyName":"Desert Foods LLC","website":"https://desertfoods.qa",
             "sourceUrl":"https://desertfoods.qa/about"}""";

    private String capture(String token, String projectId, String destination) throws Exception {
        return body(mvc.perform(post(captureUrl(projectId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CAPTURE_BODY.formatted(destination)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    /**
     * A client representative seated on the mandate — the one role that holds WORK_VIEW without
     * WORK_EXECUTE, and therefore the one that proves capture is gated on the write action rather than
     * on being able to see the mandate at all.
     */
    private String attachRepresentative(String adminToken, String projectId) throws Exception {
        String representativeEmail = "rep@capture-client.example";
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Client Rep","position":"Chair","email":"%s"}"""
                                .formatted(representativeEmail)))
                .andExpect(status().isOk());

        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Client Rep","password":"%s"}"""
                                .formatted(email.latestTokenFor(representativeEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }

    private static String triageUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/triage";
    }

    private static String captureUrl(String projectId) {
        return triageUrl(projectId) + "/captures";
    }

    private String adminOf(String workspaceName) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", "alok@" + domain), workspaceName);
        return login("alok@" + domain);
    }

    private String project(String token) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Capture Client %s"}""".formatted(UUID.randomUUID())))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }
}
