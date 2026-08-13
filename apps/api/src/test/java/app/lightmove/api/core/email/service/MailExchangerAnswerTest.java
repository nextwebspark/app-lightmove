package app.lightmove.api.core.email.service;

import static org.assertj.core.api.Assertions.assertThat;

import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.BasicAttribute;
import javax.naming.directory.BasicAttributes;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Reading a DNS answer, without asking a resolver for one.
 *
 * <p>Lives in the validator's own package because the rest of {@link EmailAddressValidator} is tested
 * from {@code app.lightmove.api.email} with MX checking switched off — a real lookup would make those
 * tests slow and network-dependent. Splitting the answer from the lookup is what makes this half
 * testable at all, and it is the half that was wrong: presence of an MX attribute was treated as
 * deliverability, so {@code example.com} — which publishes RFC 7505's "accepts no mail" — was accepted.
 */
class MailExchangerAnswerTest {

    @Test
    @DisplayName("a real mail exchanger means the domain accepts mail")
    void realMailExchangerIsDeliverable() throws NamingException {
        assertThat(EmailAddressValidator.acceptsMail(answer("10 mail.nextwebspark.com."))).isTrue();
    }

    @Test
    @DisplayName("a lone null MX means the domain has declared it accepts none")
    void nullMailExchangerIsNotDeliverable() throws NamingException {
        assertThat(EmailAddressValidator.acceptsMail(answer("0 ."))).isFalse();
    }

    @Test
    @DisplayName("a null MX alongside a real one still accepts mail")
    void aRealExchangerAmongThemWins() throws NamingException {
        assertThat(EmailAddressValidator.acceptsMail(answer("0 .", "10 mail.nextwebspark.com."))).isTrue();
    }

    @Test
    @DisplayName("no MX attribute at all is not deliverable")
    void noAttributeIsNotDeliverable() throws NamingException {
        assertThat(EmailAddressValidator.acceptsMail(new BasicAttributes())).isFalse();
    }

    @Test
    @DisplayName("an MX attribute carrying no records is not deliverable")
    void emptyAttributeIsNotDeliverable() throws NamingException {
        assertThat(EmailAddressValidator.acceptsMail(answer())).isFalse();
    }

    private static Attributes answer(String... records) {
        BasicAttribute mailExchangers = new BasicAttribute("MX");
        for (String record : records) {
            mailExchangers.add(record);
        }
        Attributes attributes = new BasicAttributes();
        attributes.put(mailExchangers);
        return attributes;
    }
}
