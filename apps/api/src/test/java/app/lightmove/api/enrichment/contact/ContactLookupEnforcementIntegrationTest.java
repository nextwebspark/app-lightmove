package app.lightmove.api.enrichment.contact;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import app.lightmove.api.IntegrationTest;
import app.lightmove.api.billing.credit.constant.CreditGrantSource;
import app.lightmove.api.candidate.model.FoundPhones;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import tools.jackson.databind.JsonNode;

/** With enforcement on, a press the credits cannot cover is refused before the provider is asked. */
@IntegrationTest
@TestPropertySource(properties = "lightmove.billing.enforce=true")
class ContactLookupEnforcementIntegrationTest extends ContactCreditsFlowSupport {

    @Test
    @DisplayName("too few credits for a phone answers 402 with what it needs, and the provider is never asked")
    void tooFewCreditsAreRefusedBeforeTheProvider() throws Exception {
        grant(workspaceId, CreditGrantSource.MANUAL, 3, null);
        String candidateId = executive("unaffordable-person");
        finder.answerPhonesWith(new FoundPhones("contactout", List.of("+12065550100")));

        JsonNode refusal = body(press(candidateId, "phone").andExpect(status().isPaymentRequired()).andReturn());

        assertThat(refusal.get("code").asText()).isEqualTo("INSUFFICIENT_CREDITS");
        assertThat(refusal.get("required").asLong()).isEqualTo(5);
        assertThat(refusal.get("available").asLong()).isEqualTo(3);
        assertThat(finder.askedUrls()).isEmpty();
        assertThat(ledger.balanceOf(workspaceId).available()).isEqualTo(3);
    }

    @Test
    @DisplayName("an empty balance still lets an already-asked channel be read off the row")
    void anAnsweredChannelCostsNothingToRead() throws Exception {
        grant(workspaceId, CreditGrantSource.MANUAL, 5, null);
        String candidateId = executive("answered-person");
        finder.answerPhonesWith(new FoundPhones("contactout", List.of("+12065550100")));
        press(candidateId, "phone").andExpect(status().isOk());

        JsonNode again = body(press(candidateId, "phone").andExpect(status().isOk()).andReturn());

        assertThat(again.get("outcome").asText()).isEqualTo("held");
        assertThat(again.get("creditsLeft").asLong()).isZero();
        assertThat(finder.askedUrls()).hasSize(1);
    }
}
