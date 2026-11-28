package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * What an executive expects of a move (V249): saved with the rest of the Compensation section, read back by both
 * drawers, and refused where the drawer would never send it.
 */
@IntegrationTest
class MoveExpectationsIntegrationTest extends FlowTestSupport {

    private String admin;
    private String position;

    @BeforeEach
    void setUp() throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), "Expectations Firm");
        admin = login(address);
        position = mandate("Chief Financial Officer");
    }

    @Test
    @DisplayName("the Compensation save keeps what they expect beside the package, and both drawers read it back")
    void expectationsRoundTripOnBothDrawers() throws Exception {
        JsonNode filed = add("""
                {"fullName":"Fatima Al Mazrouei",
                 "compensation":{"currency":"AED","baseSalary":1500000,"noticePeriod":"3 months"}}""");

        save(filed.get("id").asText(), """
                {"fullName":"Fatima Al Mazrouei",
                 "compensation":{"currency":"AED","baseSalary":1500000,"noticePeriod":"3 months",
                                 "expectedBaseSalary":1700000,"expectedTotalPackage":4000000,
                                 "expectedPackageNote":" Wants the LTIP to carry over ","availableFrom":"Jan 2027",
                                 "mobility":"region","mobilityNote":"GCC; family stays in Abu Dhabi"}}""");

        JsonNode onPosition = read(filed.get("id").asText()).get("compensation");
        JsonNode onRecord = body(mvc.perform(get("/api/v1/candidates/" + filed.get("personId").asText())
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn()).get("compensation");
        for (JsonNode compensation : List.of(onPosition, onRecord)) {
            assertThat(compensation.get("baseSalary").asLong()).isEqualTo(1_500_000L);
            assertThat(compensation.get("noticePeriod").asText()).isEqualTo("3 months");
            assertThat(compensation.get("expectedBaseSalary").asLong()).isEqualTo(1_700_000L);
            assertThat(compensation.get("expectedTotalPackage").asLong()).isEqualTo(4_000_000L);
            assertThat(compensation.get("expectedPackageNote").asText()).isEqualTo("Wants the LTIP to carry over");
            assertThat(compensation.get("availableFrom").asText()).isEqualTo("Jan 2027");
            assertThat(compensation.get("mobility").asText()).isEqualTo("region");
            assertThat(compensation.get("mobilityNote").asText()).isEqualTo("GCC; family stays in Abu Dhabi");
        }
    }

    @Test
    @DisplayName("a nought is kept as stated, and a save without them clears them, a blank mobility not established")
    void aSaveWithoutThemClearsThem() throws Exception {
        JsonNode filed = add("""
                {"fullName":"Omar Haddad",
                 "compensation":{"currency":"AED","expectedTotalPackage":0,"mobility":"none",
                                 "availableFrom":"Immediately"}}""");
        assertThat(filed.at("/compensation/expectedTotalPackage").isNumber()).isTrue();
        assertThat(filed.at("/compensation/expectedTotalPackage").asLong()).isZero();
        assertThat(filed.at("/compensation/mobility").asText()).isEqualTo("none");

        save(filed.get("id").asText(), """
                {"fullName":"Omar Haddad","compensation":{"currency":"AED","mobility":"","availableFrom":" "}}""");

        JsonNode compensation = read(filed.get("id").asText()).get("compensation");
        assertThat(compensation.get("expectedTotalPackage").isNull()).isTrue();
        assertThat(compensation.get("mobility").isNull()).isTrue();
        assertThat(compensation.get("availableFrom").isNull()).isTrue();
        assertThat(compensation.get("currency").asText()).isEqualTo("AED");
    }

    @Test
    @DisplayName("a negative expected figure, an over-long note and a mobility the drawer does not offer are refused")
    void outOfRangeValuesAreRefused() throws Exception {
        assertThat(refused("""
                {"fullName":"Lina Said","compensation":{"expectedBaseSalary":-1}}""")
                .get("fieldErrors").has("compensation.expectedBaseSalary")).isTrue();
        assertThat(refused("""
                {"fullName":"Lina Said","compensation":{"expectedTotalPackage":-1}}""")
                .get("fieldErrors").has("compensation.expectedTotalPackage")).isTrue();
        assertThat(refused("""
                {"fullName":"Lina Said","compensation":{"mobilityNote":"%s"}}""".formatted("x".repeat(201)))
                .get("fieldErrors").has("compensation.mobilityNote")).isTrue();
        assertThat(refused("""
                {"fullName":"Lina Said","compensation":{"availableFrom":"%s"}}""".formatted("x".repeat(61)))
                .get("fieldErrors").has("compensation.availableFrom")).isTrue();

        JsonNode unoffered = refused("""
                {"fullName":"Lina Said","compensation":{"mobility":"anywhere"}}""");
        assertThat(unoffered.get("code").asText()).isEqualTo("VALIDATION_FAILED");
        assertThat(unoffered.get("detail").asText()).isEqualTo("That is not a valid mobility");
    }

    private JsonNode add(String candidate) throws Exception {
        return body(mvc.perform(post(candidatesUrl())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidate))
                .andExpect(status().isCreated())
                .andReturn());
    }

    private JsonNode refused(String candidate) throws Exception {
        return body(mvc.perform(post(candidatesUrl())
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidate))
                .andExpect(status().isBadRequest())
                .andReturn());
    }

    private void save(String candidateId, String candidate) throws Exception {
        mvc.perform(put(candidatesUrl() + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(candidate))
                .andExpect(status().isOk());
    }

    private JsonNode read(String candidateId) throws Exception {
        return body(mvc.perform(get(candidatesUrl() + "/" + candidateId)
                        .header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
    }

    private String mandate(String positionTitle) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"%s Unit"}""".formatted(positionTitle)))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"%s"}""".formatted(clientId, positionTitle)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String candidatesUrl() {
        return "/api/v1/projects/" + position + "/candidates";
    }
}
