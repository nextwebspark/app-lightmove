package app.lightmove.api.enrichment.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.candidate.model.CandidateEmail;
import app.lightmove.api.candidate.model.FoundEmails;
import app.lightmove.api.candidate.model.FoundPhones;
import app.lightmove.api.core.resilience.constant.VendorFailureKind;
import app.lightmove.api.core.resilience.model.VendorCall;
import app.lightmove.api.core.resilience.model.VendorException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;

/** A Find email or Find phone press spends the workspace's contact credits only for what the provider found. */
@IntegrationTest
class ContactLookupCreditsIntegrationTest extends ContactCreditsFlowSupport {

    private static final FoundEmails EMAILS = new FoundEmails("contactout",
            List.of(new CandidateEmail("s.person@retailco.example", CandidateEmail.WORK, "Verified")));

    private static final FoundPhones PHONES = new FoundPhones("contactout", List.of("+12065550100"));

    @Test
    @DisplayName("a found email spends one credit and a found phone five, and the answer says what is left")
    void aFindSpendsItsPrice() throws Exception {
        grant(workspaceId, CreditGrantSource.MANUAL, 50, null);
        String candidateId = executive("found-person");
        finder.answerEmailsWith(EMAILS);
        finder.answerPhonesWith(PHONES);

        JsonNode email = body(press(candidateId, "email").andExpect(status().isOk()).andReturn());
        JsonNode phone = body(press(candidateId, "phone").andExpect(status().isOk()).andReturn());

        assertThat(email.get("creditsSpent").asLong()).isEqualTo(1);
        assertThat(email.get("creditsLeft").asLong()).isEqualTo(49);
        assertThat(phone.get("creditsSpent").asLong()).isEqualTo(5);
        assertThat(phone.get("creditsLeft").asLong()).isEqualTo(44);
        assertThat(holdsOf("CAPTURED")).isEqualTo(2);
        assertLedgerAddsUp(workspaceId);
    }

    @Test
    @DisplayName("every line of a spend names the person, the position and who pressed")
    void aSpendNamesWhoAndWhat() throws Exception {
        grant(workspaceId, CreditGrantSource.MANUAL, 10, null);
        String candidateId = executive("named-person");
        finder.answerEmailsWith(EMAILS);

        UUID personId = UUID.fromString(body(press(candidateId, "email").andExpect(status().isOk()).andReturn())
                .get("candidate").get("personId").asText());

        List<Map<String, Object>> lines = db.queryForList("""
                SELECT kind, user_id, project_id, person_id FROM app_lm_credit_entry
                WHERE workspace_id = ? AND hold_id IS NOT NULL ORDER BY id""", workspaceId);
        assertThat(lines).extracting(line -> line.get("kind")).containsExactly("HOLD", "CAPTURE");
        assertThat(lines).allSatisfy(line -> {
            assertThat(line.get("user_id")).isEqualTo(userId);
            assertThat(line.get("project_id")).isEqualTo(UUID.fromString(projectId));
            assertThat(line.get("person_id")).isEqualTo(personId);
        });
    }

    @Test
    @DisplayName("a miss spends nothing, and neither does the press after it, which is answered off the row")
    void aMissAndARepeatSpendNothing() throws Exception {
        grant(workspaceId, CreditGrantSource.MANUAL, 10, null);
        String candidateId = executive("missing-person");

        JsonNode miss = body(press(candidateId, "email").andExpect(status().isOk()).andReturn());
        JsonNode repeat = body(press(candidateId, "email").andExpect(status().isOk()).andReturn());

        assertThat(miss.get("outcome").asText()).isEqualTo("none");
        assertThat(miss.get("creditsSpent").asLong()).isZero();
        assertThat(repeat.get("creditsSpent").asLong()).isZero();
        assertThat(repeat.get("creditsLeft").asLong()).isEqualTo(10);
        assertThat(holdsOf("RELEASED")).isEqualTo(1);
        assertThat(finder.askedUrls()).hasSize(1);
        assertLedgerAddsUp(workspaceId);
    }

    @Test
    @DisplayName("a provider failure releases the hold, and the retry that finds something is charged once")
    void aFailureIsReleasedAndTheRetryChargedOnce() throws Exception {
        grant(workspaceId, CreditGrantSource.MANUAL, 10, null);
        String candidateId = executive("failing-person");
        finder.failWith(new VendorException(VendorCall.of("contactout", "people-linkedin-email"),
                VendorFailureKind.UNAVAILABLE, null));

        press(candidateId, "email").andExpect(status().is5xxServerError());
        assertThat(holdsOf("RELEASED")).isEqualTo(1);
        assertThat(ledger.balanceOf(workspaceId).available()).isEqualTo(10);

        finder.answerEmailsWith(EMAILS);
        JsonNode retry = body(press(candidateId, "email").andExpect(status().isOk()).andReturn());

        assertThat(retry.get("creditsSpent").asLong()).isEqualTo(1);
        assertThat(retry.get("creditsLeft").asLong()).isEqualTo(9);
        assertLedgerAddsUp(workspaceId);
    }

    @Test
    @DisplayName("two presses in flight on one person both reach the provider and spend once, whichever write wins")
    void racingPressesSpendOnce() throws Exception {
        grant(workspaceId, CreditGrantSource.MANUAL, 10, null);
        String candidateId = executive("raced-person");
        finder.answerPhonesWith(PHONES);
        finder.answerTogether(2);

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            Callable<Integer> press = () -> press(candidateId, "phone").andReturn().getResponse().getStatus();
            List<Future<Integer>> presses = List.of(pool.submit(press), pool.submit(press));
            List<Integer> statuses = List.of(presses.get(0).get(), presses.get(1).get());
            assertThat(statuses).contains(200).allMatch(answered -> answered == 200 || answered == 409);
        } finally {
            pool.shutdownNow();
        }

        assertThat(finder.askedUrls()).hasSize(2);
        assertThat(ledger.balanceOf(workspaceId).available()).isEqualTo(5);
        assertThat(holdsOf("CAPTURED")).isEqualTo(1);
        assertLedgerAddsUp(workspaceId);
    }

    @Test
    @DisplayName("with enforcement off, a find the credits cannot cover is recorded as an overdraft")
    void anUncoveredFindIsAnOverdraft() throws Exception {
        String candidateId = executive("overdrawn-person");
        finder.answerPhonesWith(PHONES);

        JsonNode phone = body(press(candidateId, "phone").andExpect(status().isOk()).andReturn());

        assertThat(phone.get("creditsSpent").asLong()).isEqualTo(5);
        assertThat(db.queryForObject("SELECT sum(overdraft) FROM app_lm_credit_entry WHERE workspace_id = ?",
                Long.class, workspaceId)).isEqualTo(5);
    }
}
