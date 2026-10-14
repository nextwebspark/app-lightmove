package app.lightmove.api.candidate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * A consultant's assessment of an executive (V135): scored against the brief as it stands, kept through a
 * rename of the brief, refused when stale, a draft again after any change to a reviewed one, and never read or
 * written by a client seat.
 */
@IntegrationTest
class CandidateScorecardIntegrationTest extends FlowTestSupport {

    private String admin;

    @Test
    @DisplayName("scores are tallied against the brief, and survive a rename of the criterion as retired")
    void scoresFollowTheBrief() throws Exception {
        firm("Scorecard Firm");
        String cfo = mandate("Chief Financial Officer");
        competencies(cfo, """
                {"technical":[{"name":"Treasury","weight":60},{"name":"Reporting","weight":40}],
                 "behavioural":[{"name":"Leadership","weight":100}],"technicalShare":60}""");
        String candidateId = add(cfo, "Fatima Al Mazrouei");

        JsonNode blank = read(admin, cfo, candidateId);
        assertThat(blank.get("version").isNull()).isTrue();
        assertThat(blank.get("status").asText()).isEqualTo("draft");
        assertThat(blank.get("criteriaCount").asInt()).isEqualTo(3);
        assertThat(blank.get("scoredCount").asInt()).isZero();

        JsonNode saved = save(admin, cfo, candidateId, """
                {"version":null,"markReviewed":false,
                 "scores":[{"panel":"technical","name":"treasury","score":10,"evidence":"Ran group treasury"},
                           {"panel":"behavioural","name":"Leadership","score":5}],
                 "report":{"careerSummary":"Twenty years in GCC finance."}}""");
        assertThat(saved.get("overall").asDouble()).isEqualTo(56.0);
        assertThat(saved.get("panels").get(0).get("criteria").get(0).get("name").asText()).isEqualTo("Treasury");
        assertThat(saved.get("report").get("careerSummary").asText()).isEqualTo("Twenty years in GCC finance.");
        assertThat(saved.get("updatedByName").asText()).isEqualTo("Alok Kumar");

        competencies(cfo, """
                {"technical":[{"name":"Group Treasury","weight":60},{"name":"Reporting","weight":40}],
                 "behavioural":[{"name":"Leadership","weight":100}]}""");

        JsonNode afterRename = read(admin, cfo, candidateId);
        assertThat(afterRename.get("retired").get(0).get("name").asText()).isEqualTo("Treasury");
        assertThat(afterRename.get("retired").get(0).get("score").asInt()).isEqualTo(10);
        assertThat(afterRename.get("overall").asDouble()).isEqualTo(20.0);
    }

    @Test
    @DisplayName("a stale save is refused, and changing a reviewed assessment makes it a draft again")
    void staleSavesAreRefusedAndReviewIsUndoneByAnyChange() throws Exception {
        firm("Review Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, "Khalid Al Harbi");

        JsonNode reviewed = save(admin, cfo, candidateId, """
                {"version":null,"markReviewed":true,"scores":[],"report":{"concerns":"Notice period"}}""");
        assertThat(reviewed.get("status").asText()).isEqualTo("reviewed");
        assertThat(reviewed.get("reviewedByName").asText()).isEqualTo("Alok Kumar");
        long version = reviewed.get("version").asLong();

        String stale = codeOf(mvc.perform(put(url(cfo, candidateId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":null,"markReviewed":false,"scores":[],"report":{}}"""))
                .andExpect(status().isConflict())
                .andReturn());
        assertThat(stale).isEqualTo("CANDIDATE_ASSESSMENT_STALE");

        JsonNode redrafted = save(admin, cfo, candidateId, """
                {"version":%d,"markReviewed":false,"scores":[],"report":{"concerns":"Six months' notice"}}"""
                .formatted(version));
        assertThat(redrafted.get("status").asText()).isEqualTo("draft");
        assertThat(redrafted.get("reviewedAt").isNull()).isTrue();

        JsonNode timeline = body(mvc.perform(get("/api/v1/projects/" + cfo + "/candidates/" + candidateId
                        + "/timeline").header("Authorization", "Bearer " + admin))
                .andExpect(status().isOk())
                .andReturn());
        assertThat(timeline.get("entries").findValuesAsString("kind")).contains("ASSESSMENT_REVIEWED");
    }

    @Test
    @DisplayName("a score outside 0–10 is a 400, and a client seat neither reads nor writes an assessment")
    void outOfRangeAndClientSeatsAreRefused() throws Exception {
        firm("Refusal Firm");
        String cfo = mandate("Chief Financial Officer");
        String candidateId = add(cfo, "Hamad Al Suwaidi");

        mvc.perform(put(url(cfo, candidateId))
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":null,"markReviewed":false,
                                 "scores":[{"panel":"technical","name":"Anything","score":11}],"report":{}}"""))
                .andExpect(status().isBadRequest());

        String client = clientOn(cfo);
        mvc.perform(get(url(cfo, candidateId)).header("Authorization", "Bearer " + client))
                .andExpect(status().isForbidden());
        mvc.perform(put(url(cfo, candidateId))
                        .header("Authorization", "Bearer " + client)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"version":null,"markReviewed":false,"scores":[],"report":{}}"""))
                .andExpect(status().isForbidden());
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private void firm(String name) throws Exception {
        String address = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", address), name);
        admin = login(address);
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

    private void competencies(String projectId, String body) throws Exception {
        mvc.perform(put("/api/v1/projects/" + projectId + "/position/competencies")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk());
    }

    private String clientOn(String projectId) throws Exception {
        String clientEmail = "client@scorecard-client-" + domain;
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives/invitations")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"A Client","position":"Chair","email":"%s"}
                                """.formatted(clientEmail)))
                .andExpect(status().isOk());
        return body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"A Client","password":"%s"}
                                """.formatted(email.latestTokenFor(clientEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
    }

    private String add(String projectId, String fullName) throws Exception {
        return body(mvc.perform(post("/api/v1/projects/" + projectId + "/candidates")
                        .header("Authorization", "Bearer " + admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s"}""".formatted(fullName)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private JsonNode read(String token, String projectId, String candidateId) throws Exception {
        return body(mvc.perform(get(url(projectId, candidateId)).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn());
    }

    private JsonNode save(String token, String projectId, String candidateId, String body) throws Exception {
        return body(mvc.perform(put(url(projectId, candidateId))
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andReturn());
    }

    private static String url(String projectId, String candidateId) {
        return "/api/v1/projects/" + projectId + "/candidates/" + candidateId + "/assessment";
    }
}
