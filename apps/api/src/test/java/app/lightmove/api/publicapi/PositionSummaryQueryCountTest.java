package app.lightmove.api.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.core.security.apikey.ApiKeyScope;
import app.lightmove.api.core.security.apikey.PublicReader;
import app.lightmove.api.publicapi.service.PublicReadService;
import jakarta.persistence.EntityManagerFactory;
import java.util.Set;
import java.util.UUID;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.TestPropertySource;

/** The summary is a fixed number of queries however large the position. Its own class: statistics are off elsewhere. */
@IntegrationTest
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class PositionSummaryQueryCountTest extends FlowTestSupport {

    @Autowired PublicReadService reads;
    @Autowired EntityManagerFactory entityManagers;
    @Autowired JwtDecoder sessionTokens;

    @Test
    @DisplayName("a position with fifteen companies and executives costs the summary what one with one of each does")
    void noQueryPerRow() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Count Firm");
        String admin = login(alok);
        PublicReader reader = new PublicReader(UUID.fromString(sessionTokens.decode(admin).getClaimAsString("wsId")), null,
                Set.of(ApiKeyScope.PROJECTS_READ));

        UUID small = UUID.fromString(position(admin, 1));
        UUID large = UUID.fromString(position(admin, 15));

        assertThat(statementsFor(reader, large)).isEqualTo(statementsFor(reader, small));
    }

    private long statementsFor(PublicReader reader, UUID projectId) {
        Statistics statistics = entityManagers.unwrap(SessionFactory.class).getStatistics();
        reads.summary(reader, projectId);
        statistics.clear();
        reads.summary(reader, projectId);
        return statistics.getPrepareStatementCount();
    }

    private String position(String admin, int rows) throws Exception {
        String clientId = body(mvc.perform(post("/api/v1/clients").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"customName\":\"Client " + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects").header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"clientId\":\"" + clientId + "\",\"positionTitle\":\"CFO " + rows + "\"}"))
                .andExpect(status().isCreated()).andReturn()).get("id").asText();
        for (int index = 0; index < rows; index++) {
            String companyId = body(mvc.perform(post("/api/v1/projects/" + projectId + "/triage/capture")
                            .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"companyName\":\"Company " + rows + "-" + index + "\"}"))
                    .andExpect(status().isCreated()).andReturn()).get("id").asText();
            mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                            .header("Authorization", "Bearer " + admin).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"triageCompanyId\":\"" + companyId + "\",\"fullName\":\"Person " + rows
                                    + "-" + index + "\"}"))
                    .andExpect(status().isCreated());
        }
        return projectId;
    }
}
