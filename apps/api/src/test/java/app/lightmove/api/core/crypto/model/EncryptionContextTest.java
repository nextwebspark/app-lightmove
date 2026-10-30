package app.lightmove.api.core.crypto.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EncryptionContextTest {

    @Test
    @DisplayName("a workspace secret's associated data is what it was before user-owned secrets existed")
    void workspaceContextIsUnchanged() {
        UUID workspaceId = UUID.fromString("4b7e2f9a-0c1d-4e5f-8a9b-0c1d2e3f4a5b");

        assertThat(new String(new EncryptionContext(workspaceId, "zoom-refresh-token:x").associatedData(),
                StandardCharsets.UTF_8))
                .isEqualTo("lightmove:zoom-refresh-token:x:4b7e2f9a-0c1d-4e5f-8a9b-0c1d2e3f4a5b");
    }

    @Test
    @DisplayName("a user's secret is bound to the user under a purpose no workspace secret can carry")
    void userContextIsApart() {
        UUID id = UUID.randomUUID();

        assertThat(EncryptionContext.ofUser(id, "mfa-totp-secret").associatedData())
                .isNotEqualTo(new EncryptionContext(id, "mfa-totp-secret").associatedData());
        assertThat(new String(EncryptionContext.ofUser(id, "mfa-totp-secret").associatedData(), StandardCharsets.UTF_8))
                .isEqualTo("lightmove:user:mfa-totp-secret:" + id);
    }
}
