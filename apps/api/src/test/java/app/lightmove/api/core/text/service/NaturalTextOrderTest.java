package app.lightmove.api.core.text.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NaturalTextOrderTest {

    private static List<String> sorted(String... names) {
        return Stream.of(names).sorted(NaturalTextOrder.INSTANCE).toList();
    }

    @Test
    @DisplayName("case does not decide the order: a lower-case name sits among the rest, not after them")
    void caseInsensitive() {
        assertThat(sorted("Zenith", "alpha", "Bravo", "charlie")).containsExactly("alpha", "Bravo", "charlie", "Zenith");
    }

    @Test
    @DisplayName("a run of digits sorts by its value, so 2 comes before 10")
    void numbersByValue() {
        assertThat(sorted("Fund 10", "Fund 2", "Fund 1", "Fund 02b")).containsExactly("Fund 1", "Fund 2", "Fund 02b", "Fund 10");
    }

    @Test
    @DisplayName("leading spaces, quotes and punctuation are not read, so an imported name sorts under its first letter")
    void leadingPunctuationIgnored() {
        assertThat(sorted("Bravo", "  \"Alpha\"", "'Charlie")).containsExactly("  \"Alpha\"", "Bravo", "'Charlie");
    }

    @Test
    @DisplayName("an accented letter sits beside its plain one rather than after Z")
    void accentsBesideTheirLetter() {
        assertThat(sorted("Zain", "Émaar", "Emirates NBD")).containsExactly("Émaar", "Emirates NBD", "Zain");
    }

    @Test
    @DisplayName("two spellings of one name still compare unequal, so the order is total")
    void totalOrder() {
        assertThat(NaturalTextOrder.INSTANCE.compare("acme", "Acme")).isNotZero();
        assertThat(NaturalTextOrder.INSTANCE.compare("Acme", "Acme")).isZero();
    }
}
