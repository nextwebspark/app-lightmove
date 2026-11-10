package app.lightmove.api.core.crypto.service;

import app.lightmove.api.core.crypto.model.KeyedHash;
import com.google.crypto.tink.InsecureSecretKeyAccess;
import com.google.crypto.tink.KeysetHandle;
import com.google.crypto.tink.RegistryConfiguration;
import com.google.crypto.tink.TinkJsonProtoKeysetFormat;
import com.google.crypto.tink.prf.Prf;
import com.google.crypto.tink.prf.PrfConfig;
import com.google.crypto.tink.prf.PrfSet;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;

/**
 * HMAC-SHA-256 under a Tink PRF keyset held in Secret Manager. Rotating it means adding a key and promoting it:
 * new rows are written under the new primary, and the old key stays in the keyset for as long as rows use it.
 */
@RequiredArgsConstructor(access = AccessLevel.PRIVATE)
public class TinkKeyedHasher implements KeyedHasher {

    private static final int OUTPUT_BYTES = 32;

    private final PrfSet prfs;

    /** @param keysetJson a Tink JSON keyset of HMAC-SHA-256 PRF keys */
    public static TinkKeyedHasher fromKeyset(String keysetJson) throws GeneralSecurityException {
        PrfConfig.register();
        KeysetHandle keyset = TinkJsonProtoKeysetFormat.parseKeyset(keysetJson, InsecureSecretKeyAccess.get());
        return new TinkKeyedHasher(keyset.getPrimitive(RegistryConfiguration.get(), PrfSet.class));
    }

    @Override
    public boolean isAvailable() {
        return true;
    }

    @Override
    public KeyedHash hash(String value) {
        try {
            return hashUnder(prfs.getPrimaryId(), prfs.getPrfs().get(prfs.getPrimaryId()), value);
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("A value could not be hashed", failure);
        }
    }

    @Override
    public List<KeyedHash> hashUnderEveryKey(String value) {
        try {
            List<KeyedHash> hashes = new ArrayList<>();
            for (Map.Entry<Integer, Prf> key : prfs.getPrfs().entrySet()) {
                hashes.add(hashUnder(key.getKey(), key.getValue(), value));
            }
            return hashes;
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("A value could not be hashed", failure);
        }
    }

    @Override
    public Set<Integer> keyIds() {
        try {
            return Set.copyOf(prfs.getPrfs().keySet());
        } catch (GeneralSecurityException failure) {
            throw new IllegalStateException("The keyset could not be read", failure);
        }
    }

    private static KeyedHash hashUnder(int keyId, Prf prf, String value) throws GeneralSecurityException {
        byte[] output = prf.compute(value.getBytes(StandardCharsets.UTF_8), OUTPUT_BYTES);
        return new KeyedHash(keyId, HexFormat.of().formatHex(output));
    }
}
