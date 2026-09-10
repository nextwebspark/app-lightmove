package app.lightmove.api.talentmap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.StubGeocoder;
import java.util.Locale;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * The map read: the same gate as the grid, every row of the stage with a point where one could be
 * resolved, and a place asked of the vendor once and never again.
 *
 * <p>City names carry the test's own namespace because the geocoding cache is global by design — a
 * "Riyadh" one test placed would answer the next test from the cache and hide the call it asserts on.
 */
@IntegrationTest
class TalentMapIntegrationTest extends FlowTestSupport {

    @Autowired StubGeocoder geocoder;

    @Test
    @DisplayName("reading the map follows the seat, exactly as reading the grid does")
    void readGateFollowsTheSeat() throws Exception {
        Fixture f = fixture("Map Read Gate Firm");

        mvc.perform(get(mapUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk());
        mvc.perform(get(mapUrl(f.projectId)).header("Authorization", "Bearer " + login(f.saraEmail)))
                .andExpect(status().isForbidden());

        seat(f.admin, f.projectId, f.saraId, "RESEARCHER");
        mvc.perform(get(mapUrl(f.projectId)).header("Authorization", "Bearer " + login(f.saraEmail)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("the poll's read is the same map, the same gate, and the points without the rows")
    void locationsReadIsTheSameMapWithoutTheRows() throws Exception {
        Fixture f = fixture("Map Poll Firm");
        String jeddah = "Jeddah " + domain;
        geocoder.placeCity(jeddah, 21.4858, 39.1925);
        String acwa = capture(f.admin, f.projectId, "ACWA Power", jeddah, "Saudi Arabia");

        mvc.perform(get(locationsUrl(f.projectId)).header("Authorization", "Bearer " + login(f.saraEmail)))
                .andExpect(status().isForbidden());

        JsonNode points = body(mvc.perform(get(locationsUrl(f.projectId))
                        .header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.geocodingPending").value(0))
                .andExpect(jsonPath("$.locations." + acwa + ".latitude").value(21.4858))
                .andReturn());
        assertThat(points.has("companies")).isFalse();
        assertThat(points.has("candidates")).isFalse();
    }

    @Test
    @DisplayName("every row of the stage travels, located where the vendor placed it, and a place is asked once")
    void rowsAreLocatedAndPlacesAreCached() throws Exception {
        Fixture f = fixture("Map Located Firm");
        String riyadh = "Riyadh " + domain;
        String dubai = "Dubai " + domain;
        String muscat = "Muscat " + domain;
        geocoder.placeCity(riyadh, 24.7136, 46.6753);
        geocoder.placeCity(dubai, 25.2769, 55.2962);
        geocoder.placeCountry("Oman", 21.0, 57.0);

        String acwa = capture(f.admin, f.projectId, "ACWA Power", riyadh, "Saudi Arabia");
        String almarai = capture(f.admin, f.projectId, "Almarai", riyadh, "Saudi Arabia");
        String nowhere = capture(f.admin, f.projectId, "Gulf Trader", null, null);
        String yasmin = candidate(f.admin, f.projectId, """
                {"triageCompanyId":"%s","fullName":"Yasmin El-Sayed","title":"VP Finance",
                 "locationCity":"%s","locationCountry":"United Arab Emirates"}""".formatted(acwa, dubai));
        String omar = candidate(f.admin, f.projectId, """
                {"triageCompanyId":"%s","fullName":"Omar Haddad","title":"CFO"}""".formatted(almarai));
        String lina = candidate(f.admin, f.projectId, """
                {"fullName":"Lina Said","employerName":"Somewhere Untriaged",
                 "locationCity":"%s","locationCountry":"Oman"}""".formatted(muscat));

        JsonNode map = body(mvc.perform(get(mapUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCompanies").value(3))
                .andExpect(jsonPath("$.totalCandidates").value(3))
                .andExpect(jsonPath("$.geocodingPending").value(0))
                .andReturn());

        assertThat(map.get("companies")).hasSize(3);
        assertThat(map.get("candidates")).hasSize(3);
        JsonNode locations = map.get("locations");
        // ACWA's own HQ is Riyadh, but Yasmin is mapped at it from Dubai — and a company is drawn
        // where its people are, exactly as the grid's Location column reads them.
        assertThat(locations.get(acwa).get("latitude").asDouble()).isEqualTo(25.2769);
        assertThat(locations.get(acwa).get("precision").asText()).isEqualTo("CITY");
        assertThat(locations.get(acwa).get("placeLabel").asText()).endsWith(", United Arab Emirates");
        // Almarai's only executive carries no location of his own, so it keeps its own HQ.
        assertThat(locations.get(almarai).get("longitude").asDouble()).isEqualTo(46.6753);
        assertThat(locations.get(yasmin).get("latitude").asDouble()).isEqualTo(25.2769);
        // Muscat could not be placed, so Lina sits on Oman at country precision.
        assertThat(locations.get(lina).get("precision").asText()).isEqualTo("COUNTRY");
        assertThat(locations.get(lina).get("latitude").asDouble()).isEqualTo(21.0);
        // No city and no country is no point; a person with no location of their own is seated by the
        // screen at their company, not invented here.
        assertThat(locations.has(nowhere)).isFalse();
        assertThat(locations.has(omar)).isFalse();

        // Two companies in one city cost one vendor call, and a second read costs none. The vendor is
        // asked with the normalised key — lower-cased — which is what makes "Riyadh" and "riyadh" one call.
        assertThat(geocoder.asked()).containsExactlyInAnyOrder(
                "city:" + riyadh.toLowerCase(Locale.ROOT), "city:" + dubai.toLowerCase(Locale.ROOT),
                "city:" + muscat.toLowerCase(Locale.ROOT), "country:oman");
        geocoder.clear();
        mvc.perform(get(mapUrl(f.projectId)).header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                // Dubai again, because ACWA is drawn where Yasmin is — the same rule, off the cache.
                .andExpect(jsonPath("$.locations." + acwa + ".latitude").value(25.2769));
        assertThat(geocoder.asked()).isEmpty();
    }

    @Test
    @DisplayName("another stage carries its own companies and never the unmapped people")
    void anotherStageIsItsOwnMap() throws Exception {
        Fixture f = fixture("Map Stage Firm");
        capture(f.admin, f.projectId, "ACWA Power", null, "Saudi Arabia");
        candidate(f.admin, f.projectId, """
                {"fullName":"Lina Said","employerName":"Somewhere Untriaged"}""");

        mvc.perform(get(mapUrl(f.projectId) + "?status=shortlisted")
                        .header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companies").isEmpty())
                .andExpect(jsonPath("$.candidates").isEmpty());
        mvc.perform(get(mapUrl(f.projectId) + "?status=sideways")
                        .header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("the config says the map is on and hands a signed-in user the browser token")
    void configIsServedToASignedInUser() throws Exception {
        Fixture f = fixture("Map Config Firm");

        mvc.perform(get("/api/v1/talent-map/config").header("Authorization", "Bearer " + f.admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enabled").value(true))
                .andExpect(jsonPath("$.publicToken").value("pk.test"));
        mvc.perform(get("/api/v1/talent-map/config")).andExpect(status().isUnauthorized());
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private static String mapUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/talent-map";
    }

    private static String locationsUrl(String projectId) {
        return mapUrl(projectId) + "/locations";
    }

    private String capture(String token, String projectId, String name, String city, String country)
            throws Exception {
        String cityField = city == null ? "" : ",\"companyCity\":\"" + city + "\"";
        String countryField = country == null ? "" : ",\"companyCountry\":\"" + country + "\"";
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"companyName\":\"" + name + "\"" + cityField + countryField + "}"))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String candidate(String token, String projectId, String json) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private record Fixture(String admin, String projectId, String saraEmail, String saraId) {}

    private Fixture fixture(String firmName) throws Exception {
        String alok = "alok@" + domain;
        String sara = "sara@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        String admin = login(alok);
        inviteAndAccept(admin, "Sara Al-Mansour", sara, "MEMBER");

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Map Client"}"""))
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Head of Retail"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();

        return new Fixture(admin, projectId, sara, memberIdOf(admin, sara));
    }

    private void seat(String leadToken, String projectId, String memberId, String role) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/members/" + memberId)
                        .header("Authorization", "Bearer " + leadToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"role":"%s"}""".formatted(role)))
                .andExpect(status().isOk());
    }
}
