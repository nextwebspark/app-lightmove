package app.lightmove.api.enrichment.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.FlowTestSupport;
import app.lightmove.api.IntegrationTest;
import app.lightmove.api.RecordingContactFinder;
import app.lightmove.api.candidate.dto.CandidateResponse;
import app.lightmove.api.candidate.model.CandidateEmail;
import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;
import app.lightmove.api.candidate.service.CandidateService;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;

/**
 * Pressing Find email or Find phone on an executive.
 *
 * <p>What must hold, in order of what it costs to get wrong: a value already held is never bought
 * again, a provider that has nothing is asked once and remembered, a spent quota leaves the row
 * untouched so a top-up makes the same press work, and someone who may only read the mandate may not
 * spend its credits.
 */
@IntegrationTest
class ContactLookupIntegrationTest extends FlowTestSupport {

    private static final FoundEmails EMAILS = new FoundEmails("contactout", List.of(
            new CandidateEmail("s.person@retailco.example", CandidateEmail.WORK, null),
            new CandidateEmail("sample.person@holdings.example", CandidateEmail.WORK, "Verified"),
            new CandidateEmail("sample.person@gmail.example", CandidateEmail.PERSONAL, null)));

    private static final FoundPhones PHONES =
            new FoundPhones("contactout", List.of("+12065550100", "651-555-0142"));

    @Autowired private RecordingContactFinder finder;
    @Autowired private CandidateService candidates;

    private String adminToken;

    @BeforeEach
    void resetTheProvider() {
        finder.clear();
    }

    @Test
    @DisplayName("finding an email fills the tiles and promotes the verified work address onto the row")
    void findingAnEmailPromotesTheVerifiedWorkAddress() throws Exception {
        String projectId = mandate("Contact Lookup Firm");
        String candidateId = executive(projectId, "Sample Person", "sample-profile");
        finder.answerEmailsWith(EMAILS);

        JsonNode answer = lookup(projectId, candidateId, "email", status().isOk());

        assertThat(answer.get("outcome").asText()).isEqualTo("found");
        JsonNode candidate = answer.get("candidate");
        assertThat(candidate.get("email").asText()).isEqualTo("sample.person@holdings.example");
        assertThat(candidate.get("contacts").get("emails")).hasSize(3);
        assertThat(candidate.get("contacts").get("emails").get(1).get("status").asText())
                .isEqualTo("Verified");
        assertThat(candidate.get("contacts").get("source").asText()).isEqualTo("contactout");
        assertThat(candidate.get("contacts").get("emailsLookedUpAt").isNull()).isFalse();
        assertThat(candidate.get("contacts").get("phonesLookedUpAt").isNull()).isTrue();
    }

    @Test
    @DisplayName("a second press does not ask the provider again")
    void aSecondPressDoesNotAskAgain() throws Exception {
        String projectId = mandate("Twice Asked Firm");
        String candidateId = executive(projectId, "Sample Person", "sample-profile");
        finder.answerEmailsWith(EMAILS);

        lookup(projectId, candidateId, "email", status().isOk());
        JsonNode second = lookup(projectId, candidateId, "email", status().isOk());

        assertThat(second.get("outcome").asText()).isEqualTo("held");
        assertThat(second.get("candidate").get("email").asText())
                .isEqualTo("sample.person@holdings.example");
        assertThat(finder.askedUrls()).hasSize(1);
    }

    @Test
    @DisplayName("a profile the provider has nothing on is remembered as a miss and never asked twice")
    void aMissIsAskedOnce() throws Exception {
        String projectId = mandate("Nothing On Record Firm");
        String candidateId = executive(projectId, "Sample Person", "sample-profile");

        JsonNode first = lookup(projectId, candidateId, "email", status().isOk());
        JsonNode second = lookup(projectId, candidateId, "email", status().isOk());

        assertThat(first.get("outcome").asText()).isEqualTo("none");
        assertThat(second.get("outcome").asText()).isEqualTo("none");
        assertThat(second.get("candidate").get("contacts").get("emailsLookedUpAt").isNull()).isFalse();
        assertThat(finder.askedUrls()).hasSize(1);
    }

    @Test
    @DisplayName("running out of credits is reported and leaves the row untouched, so a retry still works")
    void anExhaustedQuotaLeavesTheRowRetryable() throws Exception {
        String projectId = mandate("No Credits Firm");
        String candidateId = executive(projectId, "Sample Person", "sample-profile");
        finder.failWith(new VendorException(VendorCall.of("contactout", "people-linkedin-email"),
                VendorFailureKind.QUOTA_EXHAUSTED, null));

        assertThat(codeOf(mvc.perform(post(lookupUrl(projectId, candidateId, "email"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andReturn()))
                .isEqualTo("CONTACT_LOOKUP_NO_CREDITS");

        finder.answerEmailsWith(EMAILS);
        JsonNode afterTopUp = lookup(projectId, candidateId, "email", status().isOk());

        assertThat(afterTopUp.get("outcome").asText()).isEqualTo("found");
        assertThat(afterTopUp.get("candidate").get("email").asText())
                .isEqualTo("sample.person@holdings.example");
    }

    @Test
    @DisplayName("an executive with no LinkedIn profile is refused before anything is spent")
    void noProfileIsRefusedBeforeSpending() throws Exception {
        String projectId = mandate("No Profile Firm");
        String candidateId = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Urlless Person"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();

        assertThat(codeOf(mvc.perform(post(lookupUrl(projectId, candidateId, "email"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict())
                .andReturn()))
                .isEqualTo("CONTACT_LOOKUP_NO_PROFILE");
        assertThat(finder.askedUrls()).isEmpty();
    }

    @Test
    @DisplayName("refusals do not eat the budget that guards what a lookup costs")
    void refusalsDoNotEatTheBudget() throws Exception {
        String projectId = mandate("Unspent Budget Firm");
        String urlless = body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Urlless Person"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        finder.answerEmailsWith(EMAILS);

        // Comfortably past lookups-per-user-per-minute, and not one of them can spend a credit.
        for (int refusal = 0; refusal < 25; refusal++) {
            mvc.perform(post(lookupUrl(projectId, urlless, "email"))
                            .header("Authorization", "Bearer " + adminToken))
                    .andExpect(status().isConflict());
        }

        String researchable = executive(projectId, "Sample Person", "sample-profile");
        assertThat(lookup(projectId, researchable, "email", status().isOk()).get("outcome").asText())
                .isEqualTo("found");
    }

    @Test
    @DisplayName("a write that loses the race is answered with what the winner stored")
    void theLoserOfARaceIsAnsweredWithTheWinnersRow() throws Exception {
        String projectId = mandate("Raced Write Firm");
        String candidateId = executive(projectId, "Sample Person", "sample-profile");

        // Both presses pass the guard before either writes, so both reach here with their own answer.
        candidates.applyFoundEmails(UUID.fromString(projectId), UUID.fromString(candidateId), EMAILS);
        CandidateResponse loser = candidates.applyFoundEmails(UUID.fromString(projectId),
                UUID.fromString(candidateId),
                new FoundEmails("contactout",
                        List.of(new CandidateEmail("later@retailco.example", CandidateEmail.WORK, null))));

        // The second write is dropped, and the row it answers with is the first's — which is what the
        // endpoint now reads its outcome from, so the two halves of one response cannot disagree.
        assertThat(loser.email()).isEqualTo("sample.person@holdings.example");
        assertThat(loser.contacts().emails()).hasSize(3);
    }

    @Test
    @DisplayName("finding a phone does not spend the email lookup")
    void theTwoChannelsAreIndependent() throws Exception {
        String projectId = mandate("Two Channels Firm");
        String candidateId = executive(projectId, "Sample Person", "sample-profile");
        finder.answerPhonesWith(PHONES);

        JsonNode answer = lookup(projectId, candidateId, "phone", status().isOk());

        assertThat(answer.get("outcome").asText()).isEqualTo("found");
        JsonNode contacts = answer.get("candidate").get("contacts");
        assertThat(answer.get("candidate").get("phone").asText()).isEqualTo("+12065550100");
        assertThat(contacts.get("phones")).hasSize(2);
        assertThat(contacts.get("emailsLookedUpAt").isNull()).isTrue();
        assertThat(answer.get("candidate").get("email").isNull()).isTrue();
    }

    @Test
    @DisplayName("a deployment with no contact account reports the capability off and refuses the endpoint")
    void anUnconfiguredDeploymentOffersNothing() throws Exception {
        String projectId = mandate("No Provider Firm");
        String candidateId = executive(projectId, "Sample Person", "sample-profile");
        finder.offer(false);

        assertThat(body(mvc.perform(get("/api/v1/contact-lookup/config")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("enabled").asBoolean()).isFalse();

        assertThat(codeOf(mvc.perform(post(lookupUrl(projectId, candidateId, "email"))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isServiceUnavailable())
                .andReturn()))
                .isEqualTo("CONTACT_LOOKUP_UNAVAILABLE");
        assertThat(finder.askedUrls()).isEmpty();
    }

    @Test
    @DisplayName("the capability read says the buttons are offered when a provider is configured")
    void aConfiguredDeploymentOffersTheButtons() throws Exception {
        mandate("Configured Provider Firm");

        assertThat(body(mvc.perform(get("/api/v1/contact-lookup/config")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn()).get("enabled").asBoolean()).isTrue();
    }

    @Test
    @DisplayName("a client representative may read the mandate and may not spend its credits")
    void aClientRepresentativeMayNotSpend() throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), "Client Rep Firm");
        adminToken = login(alok);

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Rep Client"}"""))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String projectId = body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Group CFO"}
                                """.formatted(clientId)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String candidateId = executive(projectId, "Sample Person", "sample-profile");

        String repEmail = "chair@rep-client.example";
        String representativeId = body(mvc.perform(post("/api/v1/clients/" + clientId + "/representatives")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"Ext Rep","position":"Chair","email":"%s"}
                                """.formatted(repEmail)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
        String rep = body(mvc.perform(post("/api/v1/onboarding/accept-invitation-signup")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","fullName":"Ext Rep","password":"%s"}
                                """.formatted(email.latestTokenFor(repEmail), PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn()).get("accessToken").asText();
        mvc.perform(post("/api/v1/projects/" + projectId + "/representatives")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"representativeId":"%s"}""".formatted(representativeId)))
                .andExpect(status().isOk());

        // They hold WORK_VIEW, so the mandate's people are theirs to read...
        mvc.perform(get(candidatesUrl(projectId)).header("Authorization", "Bearer " + rep))
                .andExpect(status().isOk());

        // ...and the buttons are WORK_EXECUTE, because pressing one writes to the row and bills us.
        mvc.perform(post(lookupUrl(projectId, candidateId, "email"))
                        .header("Authorization", "Bearer " + rep))
                .andExpect(status().isForbidden());
        assertThat(finder.askedUrls()).isEmpty();
    }

    private JsonNode lookup(String projectId, String candidateId, String channel,
                            org.springframework.test.web.servlet.ResultMatcher expected) throws Exception {
        return body(mvc.perform(post(lookupUrl(projectId, candidateId, channel))
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(expected)
                .andReturn());
    }

    private static String lookupUrl(String projectId, String candidateId, String channel) {
        return candidatesUrl(projectId) + "/" + candidateId + "/contact/" + channel;
    }

    private static String candidatesUrl(String projectId) {
        return "/api/v1/projects/" + projectId + "/candidates";
    }

    private String executive(String projectId, String fullName, String slug) throws Exception {
        return body(mvc.perform(post(candidatesUrl(projectId))
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"fullName":"%s",
                                 "linkedinUrl":"https://www.linkedin.com/in/%s"}
                                """.formatted(fullName, slug)))
                .andExpect(status().isCreated())
                .andReturn()).get("id").asText();
    }

    private String mandate(String firmName) throws Exception {
        String alok = "alok@" + domain;
        createWorkspace(verifiedUser("Alok Kumar", alok), firmName);
        adminToken = login(alok);

        String clientId = body(mvc.perform(post("/api/v1/clients")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"customName":"Lookup Client"}"""))
                .andReturn()).get("id").asText();
        return body(mvc.perform(post("/api/v1/projects")
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clientId":"%s","positionTitle":"Group CFO"}
                                """.formatted(clientId)))
                .andReturn()).get("id").asText();
    }
}
