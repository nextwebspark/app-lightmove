package app.lightmove.api.core.security.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TypedDomainTest {

    @ParameterizedTest
    @CsvSource(nullValues = "null", value = {
            "jane@firm.example, firm.example",
            "Jane@Firm.Example, firm.example",
            "a@b@firm.example, firm.example",
            "jane@, null",
            "@firm.example, null",
            "no-at-sign, null"})
    @DisplayName("an unknown sign-in keeps the domain after the last @, or nothing")
    void readsTheDomainAfterTheLastAt(String typed, String domain) {
        assertThat(AuthenticationService.typedDomainOrNull(typed)).isEqualTo(domain);
    }
}
