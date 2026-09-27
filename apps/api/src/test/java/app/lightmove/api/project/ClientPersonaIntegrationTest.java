package app.lightmove.api.project;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.ApolloUniverse;
import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/** An agency client's persona: seeded from the picked company, edited in the drawer, and audited. */
@IntegrationTest
class ClientPersonaIntegrationTest extends FlowTestSupport {

    @Autowired JdbcTemplate db;

    @BeforeEach
    void seedUniverse() {
        ApolloUniverse universe = new ApolloUniverse(db);
        universe.reset();
        universe.company("apollo-moh", "Ministry of Health").industry("government administration")
                .country("Saudi Arabia").city("Riyadh").website("https://moh.gov.sa").employees(206000).insert();
    }

    @Test
    @DisplayName("a client picked from the universe names its company and starts from its sector and country")
    void universeClientIsSeeded() throws Exception {
        String admin = agencyAdmin("Seeded Agency");

        String clientId = createUniverseClient(admin);

        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.apolloAccountId").value("apollo-moh"))
                .andExpect(jsonPath("$.persona.sectors", Matchers.hasItem("Government Administration")))
                .andExpect(jsonPath("$.persona.geographies[0]").value("Saudi Arabia"));
    }

    @Test
    @DisplayName("a client typed in by hand has no universe id and an empty persona")
    void typedClientHasNoUniverseId() throws Exception {
        String admin = agencyAdmin("Typed Agency");

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Harbour Group","customDomain":"harbour.example"}"""))
                .andExpect(status().isCreated())
                .andReturn()).at("/id").asText();

        mvc.perform(get("/api/v1/clients/" + clientId).header("Authorization", "Bearer " + admin))
                .andExpect(jsonPath("$.apolloAccountId").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.persona.sectors.length()").value(0));
    }

    @Test
    @DisplayName("a member saves the persona, tidied, and the write is on the record")
    void memberSavesPersona() throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Persona Agency", "AGENCY");
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");
        String clientId = createUniverseClient(admin);

        mvc.perform(put("/api/v1/clients/" + clientId + "/persona")
                        .header("Authorization", "Bearer " + login(sara))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"summary":"  Public health provider for the Kingdom ",
                                 "sectors":["Hospitals"," hospitals ","Health Care",""],
                                 "competitors":["Dallah Health"],"geographies":["KSA"],"notes":" "}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.persona.summary").value("Public health provider for the Kingdom"))
                .andExpect(jsonPath("$.persona.sectors.length()").value(2))
                .andExpect(jsonPath("$.persona.competitors[0]").value("Dallah Health"))
                .andExpect(jsonPath("$.persona.notes").value(Matchers.nullValue()));

        assertThat(db.queryForObject("""
                SELECT count(*) FROM app_lm_audit_event
                WHERE event_type = 'CLIENT_UPDATED' AND target_id = ? AND metadata ->> 'section' = 'persona'
                """, Integer.class, clientId)).isEqualTo(1);
    }

    @Test
    @DisplayName("another workspace's client answers 404, never a hint that it exists")
    void foreignClientIsNotFound() throws Exception {
        String clientId = createUniverseClient(agencyAdmin("Owning Agency"));
        String outsider = agencyAdmin("Other Agency", "omar@" + domain);

        mvc.perform(put("/api/v1/clients/" + clientId + "/persona")
                        .header("Authorization", "Bearer " + outsider)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"summary":"Taken over"}"""))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("an over-long list is refused before anything is written")
    void refusesAnOverlongList() throws Exception {
        String admin = agencyAdmin("Capped Agency");
        String clientId = createUniverseClient(admin);
        String sectors = String.join(",", java.util.stream.IntStream.range(0, 21)
                .mapToObj(index -> "\"Sector " + index + "\"").toList());

        mvc.perform(put("/api/v1/clients/" + clientId + "/persona")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sectors\":[" + sectors + "]}"))
                .andExpect(status().isBadRequest());
    }

    private String agencyAdmin(String workspaceName) throws Exception {
        return agencyAdmin(workspaceName, "alok@" + domain);
    }

    private String agencyAdmin(String workspaceName, String email) throws Exception {
        createWorkspace(verifiedUser("Alok Kumar", email), workspaceName, "AGENCY");
        return login(email);
    }

    private String createUniverseClient(String bearerToken) throws Exception {
        return body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + bearerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"company":{"apolloAccountId":"apollo-moh"},"sector":"government administration"}"""))
                .andExpect(status().isCreated())
                .andReturn()).at("/id").asText();
    }
}
