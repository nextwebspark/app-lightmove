package app.lightmove.api;

import app.lightmove.api.core.crypto.service.TinkKeyedHasher;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.prf.PrfConfig;
import com.google.crypto.tink.prf.PredefinedPrfParameters;
import java.security.GeneralSecurityException;

/** The dev-only suppression keyset that application-test.yml and ops/dev/api.sh also carry. It protects nothing real. */
public final class TestKeyedHashers {

    public static final int DEV_KEY_ID = 31013455;

    public static final String DEV_KEYSET = """
            {"primaryKeyId":31013455,"key":[{"keyData":{"typeUrl":"type.googleapis.com/google.crypto.tink.HmacPrfKey",\
            "value":"EgIIAxogNW7n0Y4IR7YVIIa+pBH3RFlacGOjmxrNWT/yv57Gs9E=","keyMaterialType":"SYMMETRIC"},\
            "status":"ENABLED","keyId":31013455,"outputPrefixType":"RAW"}]}""";

    private TestKeyedHashers() {}

    public static TinkKeyedHasher dev() {
        return of(DEV_KEYSET);
    }

    public static TinkKeyedHasher of(String keysetJson) {
        try {
            return TinkKeyedHasher.fromKeyset(keysetJson);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** A fresh keyset of one HMAC-SHA-256 PRF key. */
    public static String newKeyset() {
        try {
            PrfConfig.register();
            return serialised(KeysetHandle.generateNew(PredefinedPrfParameters.HMAC_SHA256_PRF));
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** The given keyset with a new key added and promoted, as a rotation leaves it. */
    public static String rotated(String keysetJson) {
        try {
            PrfConfig.register();
            KeysetHandle current = TinkJsonProtoKeysetFormat.parseKeyset(keysetJson, InsecureSecretKeyAccess.get());
            KeysetHandle rotated = KeysetHandle.newBuilder(current)
                    .addEntry(KeysetHandle.generateEntryFromParameters(PredefinedPrfParameters.HMAC_SHA256_PRF)
                            .withRandomId()
                            .makePrimary())
                    .build();
            return serialised(rotated);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static String serialised(KeysetHandle keyset) throws GeneralSecurityException {
        return TinkJsonProtoKeysetFormat.serializeKeyset(keyset, InsecureSecretKeyAccess.get());
    }
}
