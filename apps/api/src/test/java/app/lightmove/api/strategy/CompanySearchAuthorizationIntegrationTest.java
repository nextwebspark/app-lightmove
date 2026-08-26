package app.lightmove.api.strategy;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingEmailSender;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;

/**
 * The market's own shape is workspace reference data, gated {@code PROJECT_BROWSE}: staff see it,
 * a pure client representative does not.
 *
 * <p><b>The refusal is the point of this file.</b> Nothing else holds that gate in place. The facet
 * tests prove the counts are right and that an anonymous caller is turned away, but a signed-in
 * caller <i>without</i> the action had no test at all — so widening {@code PROJECT_BROWSE} to CLIENT,
 * or dropping the annotation, would hand a hiring company the industries, the size bands and the
 * name of every company in the universe, with the suite still green.
 *
 * <p>Note that these two endpoints take no {@code AuthPrincipal} parameter, unlike every other
 * controller here: they are scoped to no workspace and write no audit event, so there is nothing to
 * pass. {@code principal} in the {@code @PreAuthorize} expression comes from the security expression
 * root rather than from a method argument, which is exactly what these tests exercise — a principal
 * that failed to resolve to an {@code AuthPrincipal} would fail the staff case too, not just the
 * client one.
 */
@IntegrationTest
@Import(RecordingEmailSender.Config.class)
class CompanySearchAuthorizationIntegrationTest extends FlowTestSupport {

    private static final String FACETS_URL = "/api/v1/companies/facets";
    private static final String SEARCH_URL = "/api/v1/companies/search";
    private static final String KEYWORDS_URL = "/api/v1/companies/keywords";

    @Test
    @DisplayName("a pure client representative reads neither the facets nor the company search")
    void pureClientIsRefusedTheMarket() throws Exception {
        String rep = pureClientRepresentative("Market Fence Firm");

        // What industries and size bands the universe holds is a map of where this firm can look.
        mvc.perform(get(FACETS_URL).header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden());

        // And the picker would answer "which companies do you know of called…" one query at a time.
        mvc.perform(get(SEARCH_URL).param("q", "power").header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden());

        mvc.perform(get(KEYWORDS_URL).param("q", "saas").header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an ordinary member reads both: the market is shared reference data, not team content")
    void anyStaffMemberReadsTheMarket() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Market Staff Firm");
        inviteAndAccept(login(alok), "Sara Al-Mansour", sara, "MEMBER");
        String member = login(sara);

        // No seat on any project, and none needed. A mandate's *chosen* scope is team content behind
        // WORK_VIEW; the shape of the market it was chosen from is not.
        mvc.perform(get(FACETS_URL).header("Authorization", "Bearer " + member))
                .andExpect(status().isOk());
        mvc.perform(get(SEARCH_URL).param("q", "power").header("Authorization", "Bearer " + member))
                .andExpect(status().isOk());
        mvc.perform(get(KEYWORDS_URL).param("q", "saas").header("Authorization", "Bearer " + member))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("a signed-out caller reads neither")
    void anonymousIsRefused() throws Exception {
        mvc.perform(get(FACETS_URL)).andExpect(status().isUnauthorized());
        mvc.perform(get(SEARCH_URL).param("q", "power")).andExpect(status().isUnauthorized());
        mvc.perform(get(KEYWORDS_URL).param("q", "saas")).andExpect(status().isUnauthorized());
    }

    /**
     * A representative with no staff role: invited against a client, accepted, and never seated. This
     * is the one caller the workspace lets in without letting them look around.
     *
     * <p>The address is derived from {@code domain} rather than written out. A user is unique by
     * email across the whole database, the suite shares one container, and a literal
     * {@code chair@northwind.example} here collides with the one {@code ClientAccessIntegrationTest}
     * already uses — passing alone and 409-ing in the full run, which is the worst way to find out.
     */
    private String pureClientRepresentative(String firmName) throws Exception {
        String alok = "alok@" + domain;
        String repEmail = "chair@client-" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        String admin = login(alok);

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Northwind"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ext Rep","position":"Chair","email":"%s"}
                                """.formatted(repEmail)))
                .andExpect(status().isCreated());

        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Ext Rep","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }
}
