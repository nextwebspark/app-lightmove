package app.lightmove.api.workspace;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** Signup's organisation step: the firm is picked from the company universe, or typed in by hand. */
@IntegrationTest
class WorkspaceCompanyIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    @BeforeEach
    void seedUniverse() {
        ApolloUniverse universe = new ApolloUniverse(db);
        universe.reset();
        universe.company("apollo-af", "Al-Futtaim").industry("retail").country("United Arab Emirates")
                .city("Dubai").website("https://alfuttaim.com").logo("https://logos.example/af.png")
                .linkedin("https://www.linkedin.com/company/al-futtaim").employees(42000).insert();
    }

    @Test
    @DisplayName("a user with no workspace yet can search the universe for their firm")
    void searchBeforeAnyWorkspace() throws Exception {
        String token = verifiedUser("Alok Kumar", "alok@" + domain);

        mvc.perform(get("/api/v1/onboarding/companies").param("q", "al-fut")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies[0].apolloAccountId").value("apollo-af"))
                .andExpect(jsonPath("$.companies[0].logoUrl").value("https://logos.example/af.png"));

        mvc.perform(get("/api/v1/onboarding/companies").param("q", "a")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies").isEmpty());
    }

    @Test
    @DisplayName("a picked firm is filed under the universe's name and snapshot, not the request's")
    void pickedCompanyIsResolvedServerSide() throws Exception {
        String token = verifiedUser("Alok Kumar", "alok@" + domain);

        mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Anything Typed","apolloAccountId":"apollo-af",
                                 "companySize":"200+ people","primaryRegion":"GCC","teamFocus":"Mixed"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.workspace.name").value("Al-Futtaim"))
                .andExpect(jsonPath("$.workspace.company.apolloAccountId").value("apollo-af"))
                .andExpect(jsonPath("$.workspace.company.industry").value("retail"))
                .andExpect(jsonPath("$.workspace.company.city").value("Dubai"))
                .andExpect(jsonPath("$.workspace.company.country").value("United Arab Emirates"))
                .andExpect(jsonPath("$.workspace.company.website").value("https://alfuttaim.com"))
                .andExpect(jsonPath("$.workspace.company.linkedinUrl")
                        .value("https://www.linkedin.com/company/al-futtaim"))
                .andExpect(jsonPath("$.workspace.company.logoUrl").value("https://logos.example/af.png"));
    }

    @Test
    @DisplayName("an id the universe does not hold is refused rather than filed as a typed name")
    void unknownCompanyIsRefused() throws Exception {
        String token = verifiedUser("Alok Kumar", "alok@" + domain);

        mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Ghost Co","apolloAccountId":"apollo-missing"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("going back and typing a name by hand clears the picked firm's snapshot")
    void typedNameClearsSnapshot() throws Exception {
        String alok = "alok@" + domain;
        String token = verifiedUser("Alok Kumar", alok);
        mvc.perform(post("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Al-Futtaim","apolloAccountId":"apollo-af"}"""))
                .andExpect(status().isCreated());

        mvc.perform(patch("/api/v1/onboarding/workspace")
                        .header("Authorization", "Bearer " + login(alok))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Nimbus Partners","apolloAccountId":null}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workspace.name").value("Nimbus Partners"))
                .andExpect(jsonPath("$.workspace.company").value(org.hamcrest.Matchers.nullValue()));
    }
}
