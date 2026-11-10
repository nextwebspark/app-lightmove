package app.lightmove.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.TestKeyedHashers;
import app.lightmove.api.candidate.constant.SuppressionKind;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Pinned vectors. An entry can never be re-hashed — the list holds no raw key — so a change to how a slug or an
 * address is normalised, or to what is hashed, silently stops matching everyone already on every list. These fail
 * first; such a change needs a migration path, not a new expected value.
 */
class SuppressionKeyTest {

    private static final UUID WORKSPACE = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final String PINNED_EMAIL_HASH = "38fa59c47d7a6e53c25e60282b2699d68a1d70859699b1c53e221467a541f40d";
    private static final String PINNED_PROFILE_HASH = "da4740fde601a7a3cd38bcda2a67793aa73a22514a3b20147439cb411bbd4b5d";

    @Test
    @DisplayName("a profile link is keyed on its lower-cased slug, whatever host, case, slash or query it carries")
    void profileSlugsArePinned() {
        assertThat(SuppressionKey.ofProfileUrlOrNull("https://www.linkedin.com/in/Noor-Haddad/"))
                .isEqualTo(new SuppressionKey(SuppressionKind.PROFILE_SLUG, "noor-haddad"));
        assertThat(SuppressionKey.ofProfileUrlOrNull("https://ae.linkedin.com/in/noor-haddad?trk=public_profile"))
                .isEqualTo(new SuppressionKey(SuppressionKind.PROFILE_SLUG, "noor-haddad"));
        assertThat(SuppressionKey.ofProfileUrlOrNull("https://linkedin.com/in/NOOR-HADDAD"))
                .isEqualTo(new SuppressionKey(SuppressionKind.PROFILE_SLUG, "noor-haddad"));
        assertThat(SuppressionKey.ofProfileSlug(" Noor-Haddad "))
                .isEqualTo(new SuppressionKey(SuppressionKind.PROFILE_SLUG, "noor-haddad"));
        assertThat(SuppressionKey.ofProfileUrlOrNull("https://www.linkedin.com/company/aldar/")).isNull();
    }

    @Test
    @DisplayName("an address is keyed on its trimmed, lower-cased ledger key")
    void emailsArePinned() {
        assertThat(SuppressionKey.ofEmailOrNull("  Noor@Example.COM "))
                .isEqualTo(new SuppressionKey(SuppressionKind.EMAIL, "noor@example.com"));
        assertThat(SuppressionKey.ofEmailOrNull("   ")).isNull();
        assertThat(SuppressionKey.ofEmailOrNull(null)).isNull();
    }

    @Test
    @DisplayName("what is hashed, and its hash under the dev keyset, are pinned")
    void hashInputAndHashArePinned() {
        SuppressionKey email = SuppressionKey.ofEmailOrNull("Noor@Example.com");
        SuppressionKey profile = SuppressionKey.ofProfileUrlOrNull("https://www.linkedin.com/in/noor-haddad/");

        assertThat(email.hashInput(WORKSPACE)).isEqualTo("00000000-0000-0000-0000-000000000001:EMAIL:noor@example.com");
        assertThat(profile.hashInput(WORKSPACE))
                .isEqualTo("00000000-0000-0000-0000-000000000001:PROFILE_SLUG:noor-haddad");
        assertThat(TestKeyedHashers.dev().hash(email.hashInput(WORKSPACE)).hex()).isEqualTo(PINNED_EMAIL_HASH);
        assertThat(TestKeyedHashers.dev().hash(profile.hashInput(WORKSPACE)).hex()).isEqualTo(PINNED_PROFILE_HASH);
    }

    @Test
    @DisplayName("a key never prints the address or slug it holds")
    void keyIsRedacted() {
        assertThat(SuppressionKey.ofEmailOrNull("noor@example.com").toString()).doesNotContain("noor");
    }
}
