package app.lightmove.api.core.crypto.service;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestKeyedHashers;
import app.lightmove.api.core.crypto.model.KeyedHash;
import app.lightmove.api.core.security.token.Tokens;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class TinkKeyedHasherTest {

    @Test
    @DisplayName("the same value hashes alike under one keyset, unlike under another, and never as its plain SHA-256")
    void hashIsKeyed() {
        KeyedHasher dev = TestKeyedHashers.dev();
        KeyedHasher other = TestKeyedHashers.of(TestKeyedHashers.newKeyset());

        KeyedHash hash = dev.hash("noor@example.com");

        assertThat(hash.hex()).matches("[0-9a-f]{64}").isEqualTo(dev.hash("noor@example.com").hex());
        assertThat(hash.keyId()).isEqualTo(TestKeyedHashers.DEV_KEY_ID);
        assertThat(hash.hex()).isNotEqualTo(other.hash("noor@example.com").hex())
                .isNotEqualTo(Tokens.hash("noor@example.com"));
    }

    @Test
    @DisplayName("after a rotation a value hashes under the new primary, and its hash under the old key is still offered")
    void rotationKeepsTheOldKeyReadable() {
        KeyedHasher before = TestKeyedHashers.dev();
        KeyedHasher after = TestKeyedHashers.of(TestKeyedHashers.rotated(TestKeyedHashers.DEV_KEYSET));

        KeyedHash written = before.hash("noor-haddad");

        assertThat(after.hash("noor-haddad").keyId()).isNotEqualTo(TestKeyedHashers.DEV_KEY_ID);
        assertThat(after.hashUnderEveryKey("noor-haddad")).hasSize(2).contains(written);
        assertThat(after.keyIds()).hasSize(2).contains(TestKeyedHashers.DEV_KEY_ID);
    }

    @Test
    @DisplayName("with no keyset nothing is offered and every lookup matches nothing")
    void unconfiguredOffersNothing() {
        KeyedHasher none = new UnconfiguredKeyedHasher();

        assertThat(none.isAvailable()).isFalse();
        assertThat(none.hashUnderEveryKey("noor@example.com")).isEmpty();
        assertThat(none.keyIds()).isEmpty();
    }
}
