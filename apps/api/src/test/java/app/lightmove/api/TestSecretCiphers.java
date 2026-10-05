package app.lightmove.api;

import app.lightmove.api.core.crypto.service.SecretCipher;
import app.lightmove.api.core.crypto.service.TinkSecretCipher;
import java.security.GeneralSecurityException;

/** The dev-only key-encryption key that application-test.yml and ops/dev/api.sh also carry. It protects nothing real. */
public final class TestSecretCiphers {

    public static final String DEV_KEYSET = """
            {"primaryKeyId":653769729,"key":[{"keyData":{"typeUrl":"type.googleapis.com/google.crypto.tink.AesGcmKey",\
            "value":"GiDdcDK+eD7jyE+NjJ55C7Ta4t9IgqMRKbdXgoG/+4KVfw==","keyMaterialType":"SYMMETRIC"},\
            "status":"ENABLED","keyId":653769729,"outputPrefixType":"TINK"}]}""";

    private TestSecretCiphers() {}

    public static SecretCipher dev() {
        try {
            return TinkSecretCipher.fromKeyset(DEV_KEYSET);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }
}
