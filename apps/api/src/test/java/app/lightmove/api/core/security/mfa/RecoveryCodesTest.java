package app.lightmove.api.core.security.mfa;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class RecoveryCodesTest {

    @Test
    @DisplayName("a set is ten distinct codes of sixteen Base32 characters in groups of four")
    void issuesTenGroupedCodes() {
        List<String> codes = RecoveryCodes.newSet();

        assertThat(codes).hasSize(RecoveryCodes.PER_SET);
        assertThat(new HashSet<>(codes)).hasSize(RecoveryCodes.PER_SET);
        assertThat(codes).allMatch(code -> code.matches("[A-Z2-7]{4}-[A-Z2-7]{4}-[A-Z2-7]{4}-[A-Z2-7]{4}"));
    }

    @Test
    @DisplayName("groups, spaces and case do not change what was typed")
    void normalisesWhatWasTyped() {
        assertThat(RecoveryCodes.normalise(" abcd-efgh ijkl-mnop ")).isEqualTo("ABCDEFGHIJKLMNOP");
        assertThat(RecoveryCodes.looksLikeOne("ABCDEFGHIJKLMNOP")).isTrue();
        assertThat(RecoveryCodes.looksLikeOne("ABCDEFGHIJKLMNO1")).isFalse();
        assertThat(RecoveryCodes.looksLikeOne("123456")).isFalse();
    }
}
