package app.lightmove.api.email;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.email.service.EmailAddressValidator;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.error.constant.ErrorCode;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * The work-email rule, which is the gate between "anyone with a browser" and "someone at a firm".
 *
 * <p>MX checking is switched off throughout: it makes a real DNS query, which would make these tests
 * slow, flaky, and dependent on the network. It is exercised separately against a real resolver.
 */
class EmailAddressValidatorTest {

    @Nested
    @DisplayName("consumer email domains")
    class ConsumerDomains {

        @Test
        @DisplayName("are rejected when blocking is on")
        void rejectsConsumerDomains() {
            EmailAddressValidator validator = validatorWith(defaults());

            assertThatThrownBy(() -> validator.validateWorkEmail("alok@gmail.com"))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo(ErrorCode.EMAIL_NOT_WORK_ADDRESS);
        }

        /**
         * The regression test for a bug that shipped and silently disabled the whole control.
         *
         * <p>Spring binds {@code @DefaultValue("")} on a {@code List<String>} to a list holding one
         * empty string — not an empty list. The validator read that as "the operator supplied an
         * override list", used it instead of the bundled one, filtered the blank entry away, and was
         * left with an empty blocklist. Gmail signups sailed through, and nothing anywhere looked
         * wrong: no error, no warning, just a security control quietly doing nothing.
         */
        @Test
        @DisplayName("are still rejected when the override list binds to [\"\"] rather than []")
        void treatsBlankOverrideListAsUnset() {
            EmailAddressValidator validator = validatorWith(validation(
                    true, List.of(""), List.of()));

            assertThatThrownBy(() -> validator.validateWorkEmail("alok@gmail.com"))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo(ErrorCode.EMAIL_NOT_WORK_ADDRESS);
        }

        @Test
        @DisplayName("are accepted when blocking is switched off")
        void allowsConsumerDomainsWhenDisabled() {
            EmailAddressValidator validator = validatorWith(validation(
                    false, List.of(), List.of()));

            assertThat(validator.validateWorkEmail("alok@gmail.com")).isEqualTo("gmail.com");
        }

        @Test
        @DisplayName("can be extended by configuration")
        void honoursExtraPublicDomains() {
            EmailAddressValidator validator = validatorWith(validation(
                    true, List.of(), List.of("mycompanyisfake.com")));

            assertThatThrownBy(() -> validator.validateWorkEmail("x@mycompanyisfake.com"))
                    .isInstanceOf(ApiException.class);

            // The bundled list still applies alongside the addition.
            assertThatThrownBy(() -> validator.validateWorkEmail("x@gmail.com"))
                    .isInstanceOf(ApiException.class);
        }
    }

    @Nested
    @DisplayName("work email domains")
    class WorkDomains {

        @Test
        @DisplayName("are accepted, and the domain is returned lower-cased")
        void acceptsWorkEmail() {
            EmailAddressValidator validator = validatorWith(defaults());

            assertThat(validator.validateWorkEmail("alok.kumar@NextWebSpark.com"))
                    .isEqualTo("nextwebspark.com");
        }

        @Test
        @DisplayName("reject disposable inboxes")
        void rejectsDisposable() {
            EmailAddressValidator validator = validatorWith(defaults());

            assertThatThrownBy(() -> validator.validateWorkEmail("x@mailinator.com"))
                    .isInstanceOf(ApiException.class)
                    .extracting(ex -> ((ApiException) ex).getCode())
                    .isEqualTo(ErrorCode.EMAIL_DISPOSABLE);
        }

        @Test
        @DisplayName("reject malformed addresses")
        void rejectsMalformed() {
            EmailAddressValidator validator = validatorWith(defaults());

            for (String bad : List.of("no-at-sign", "two@@at.com", "trailing@dot.", "@nodomain.com", "bare@tld")) {
                assertThatThrownBy(() -> validator.validateWorkEmail(bad))
                        .as("should reject %s", bad)
                        .isInstanceOf(ApiException.class)
                        .extracting(ex -> ((ApiException) ex).getCode())
                        .isEqualTo(ErrorCode.EMAIL_UNDELIVERABLE);
            }
        }
    }

    // ── fixtures ──────────────────────────────────────────────────────────────

    private static EmailAddressValidator validatorWith(LightMoveProperties.Email.Validation validation) {
        return new EmailAddressValidator(new LightMoveProperties(
                null,
                new LightMoveProperties.Email("log", "LightMove", "noreply@lightmove.app", null, validation),
                null,
                null));
    }

    private static LightMoveProperties.Email.Validation defaults() {
        return validation(true, List.of(), List.of());
    }

    private static LightMoveProperties.Email.Validation validation(
            boolean blockPublic, List<String> publicDomains, List<String> extraPublic) {
        return new LightMoveProperties.Email.Validation(
                false,             // mxCheckEnabled — off: a real DNS lookup would make these flaky
                blockPublic,
                publicDomains,
                extraPublic,
                true,              // blockDisposableDomains
                List.of());
    }
}
