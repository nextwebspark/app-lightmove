package app.lightmove.api.core.security.apikey;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.core.security.token.Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** The key format: recognisable by its prefix, stored only as a hash, and refused on a typo before any lookup. */
class ApiKeySecretsTest {

    @Test
    @DisplayName("a minted key carries its kind's prefix, is stored as its SHA-256, and its hint cannot be used")
    void mintedKeyShape() {
        MintedApiKey personal = ApiKeySecrets.mint(ApiKeyKind.PERSONAL);
        MintedApiKey service = ApiKeySecrets.mint(ApiKeyKind.SERVICE);

        assertThat(personal.secret()).startsWith("uncava_pat_").hasSize(60).matches("[A-Za-z0-9_]+");
        assertThat(service.secret()).startsWith("uncava_svc_");
        assertThat(personal.hash()).isEqualTo(Tokens.hash(personal.secret()));
        assertThat(personal.hint()).startsWith("uncava_pat_").contains("…").hasSizeLessThan(32);
        assertThat(ApiKeySecrets.isWellFormed(personal.hint())).isFalse();
        assertThat(personal.toString()).doesNotContain(personal.secret());
        assertThat(ApiKeySecrets.mint(ApiKeyKind.PERSONAL).secret()).isNotEqualTo(personal.secret());
    }

    @Test
    @DisplayName("one changed character, a cut-off paste or an unknown prefix fails the checksum")
    void checksumRefusesLookalikes() {
        String secret = ApiKeySecrets.mint(ApiKeyKind.PERSONAL).secret();
        char flipped = secret.charAt(20) == 'a' ? 'b' : 'a';
        String typo = secret.substring(0, 20) + flipped + secret.substring(21);

        assertThat(ApiKeySecrets.isWellFormed(secret)).isTrue();
        assertThat(ApiKeySecrets.isWellFormed(typo)).isFalse();
        assertThat(ApiKeySecrets.isWellFormed(secret.substring(0, secret.length() - 1))).isFalse();
        assertThat(ApiKeySecrets.isWellFormed("uncava_xyz_" + secret.substring(11))).isFalse();
        assertThat(ApiKeySecrets.isWellFormed("uncava_svc_" + secret.substring(11))).isFalse();
        assertThat(ApiKeySecrets.isWellFormed(null)).isFalse();
    }
}
