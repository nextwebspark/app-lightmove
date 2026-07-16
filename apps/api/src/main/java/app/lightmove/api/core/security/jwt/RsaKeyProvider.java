package app.lightmove.api.core.security.jwt;
import app.lightmove.api.core.security.token.Tokens;

import app.lightmove.api.core.config.LightMoveProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;
import java.util.Set;
import java.nio.file.attribute.PosixFilePermission;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

/**
 * Supplies the RSA keypair that signs and verifies access tokens.
 *
 * <p>RS256 rather than HS256, because the two have different trust models. An HMAC secret both signs
 * and verifies, so anything that can check a token can also forge one. With RSA only the private key
 * signs; the public key can be handed to any future service — or published at a JWKS endpoint — and
 * it can verify our tokens without being able to mint them.
 *
 * <p>In development, a keypair is generated on first run and written to {@code .keys/} (gitignored).
 * That is a convenience, not a design: it means a fresh clone starts with no setup. Production must
 * supply real keys via {@code JWT_PRIVATE_KEY_LOCATION} — pointed at a Secret Manager mount — because
 * a generated key would be different on every instance and every restart, silently invalidating every
 * token in flight the moment the service scaled or redeployed.
 */
@Slf4j
public class RsaKeyProvider {

    private static final String ALGORITHM = "RSA";
    private static final int KEY_SIZE = 2048;

    private final RSAPublicKey publicKey;
    private final RSAPrivateKey privateKey;

    /**
     * @param mayGenerate whether a missing keypair is allowed to be conjured. True only in dev and
     *                    test. See {@link #requireKeysOrGenerate}.
     */
    public RsaKeyProvider(LightMoveProperties.Auth.Jwt config, ResourceLoader resourceLoader,
                          boolean mayGenerate) {
        Resource privateResource = resourceLoader.getResource(config.privateKeyLocation());
        Resource publicResource = resourceLoader.getResource(config.publicKeyLocation());

        if (!privateResource.exists() || !publicResource.exists()) {
            requireKeysOrGenerate(config, mayGenerate);

            KeyPair generated = generateAndPersist(config);
            this.publicKey = (RSAPublicKey) generated.getPublic();
            this.privateKey = (RSAPrivateKey) generated.getPrivate();
            return;
        }

        this.privateKey = readPrivateKey(privateResource);
        this.publicKey = readPublicKey(publicResource);
        log.info("Loaded JWT signing keys from {}", config.privateKeyLocation());
    }

    /**
     * Refuses to boot on a generated key outside development.
     *
     * <p>Silently generating one is the worst possible failure here, because nothing looks wrong. The
     * service starts, signs tokens, and works — until it restarts or a second instance comes up with a
     * different key, at which point every token in flight is rejected and every user is signed out, for
     * reasons that point nowhere near a missing file. A misconfigured secret mount is an operational
     * mistake; making it a silent one turns it into an outage nobody can read.
     *
     * <p>So: in prod, a missing key is a startup failure. Loud, immediate, and pointing at the cause.
     */
    private static void requireKeysOrGenerate(LightMoveProperties.Auth.Jwt config, boolean mayGenerate) {
        if (!mayGenerate) {
            throw new IllegalStateException(
                    "No JWT signing keys at %s / %s. Refusing to generate a throwaway keypair outside "
                            .formatted(config.privateKeyLocation(), config.publicKeyLocation())
                            + "dev/test: it would be different on every instance and every restart, "
                            + "invalidating every token the moment this service scales or redeploys. "
                            + "Point JWT_PRIVATE_KEY_LOCATION and JWT_PUBLIC_KEY_LOCATION at real keys.");
        }
        log.warn("No JWT keys found — generating a development keypair at {}. Never do this in production.",
                config.privateKeyLocation());
    }

    public RSAPublicKey publicKey() {
        return publicKey;
    }

    public RSAPrivateKey privateKey() {
        return privateKey;
    }

    private KeyPair generateAndPersist(LightMoveProperties.Auth.Jwt config) {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance(ALGORITHM);
            generator.initialize(KEY_SIZE);
            KeyPair pair = generator.generateKeyPair();

            Path privatePath = toWritablePath(config.privateKeyLocation());
            Path publicPath = toWritablePath(config.publicKeyLocation());

            if (privatePath == null || publicPath == null) {
                // A classpath: or a read-only location. We can still run on an in-memory key; every
                // restart just invalidates outstanding tokens, which is tolerable in a test and
                // unacceptable in production — hence the warning.
                log.warn("JWT keys are not at a writable file: location — generated in memory. "
                        + "Tokens will not survive a restart.");
                return pair;
            }

            Files.createDirectories(privatePath.getParent());
            writePem(privatePath, "PRIVATE KEY", pair.getPrivate().getEncoded());
            writePem(publicPath, "PUBLIC KEY", pair.getPublic().getEncoded());
            restrictToOwner(privatePath);

            log.warn("No JWT keypair found — generated one at {}. Development only: production must "
                    + "supply keys via JWT_PRIVATE_KEY_LOCATION.", privatePath);
            return pair;

        } catch (NoSuchAlgorithmException | IOException ex) {
            throw new IllegalStateException("Could not generate a JWT keypair", ex);
        }
    }

    private static RSAPrivateKey readPrivateKey(Resource resource) {
        byte[] der = decodePem(read(resource));
        try {
            return (RSAPrivateKey) KeyFactory.getInstance(ALGORITHM)
                    .generatePrivate(new PKCS8EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("JWT private key is not a valid PKCS#8 RSA key", ex);
        }
    }

    private static RSAPublicKey readPublicKey(Resource resource) {
        byte[] der = decodePem(read(resource));
        try {
            return (RSAPublicKey) KeyFactory.getInstance(ALGORITHM)
                    .generatePublic(new X509EncodedKeySpec(der));
        } catch (NoSuchAlgorithmException | InvalidKeySpecException ex) {
            throw new IllegalStateException("JWT public key is not a valid X.509 RSA key", ex);
        }
    }

    private static String read(Resource resource) {
        try (var stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Could not read JWT key at " + resource, ex);
        }
    }

    /** Strips the PEM armour and whitespace, leaving the base64 DER body. */
    private static byte[] decodePem(String pem) {
        String body = pem.replaceAll("-----BEGIN [^-]+-----", "")
                .replaceAll("-----END [^-]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(body);
    }

    private static void writePem(Path path, String label, byte[] der) throws IOException {
        String base64 = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(der);
        String pem = "-----BEGIN %s-----\n%s\n-----END %s-----\n".formatted(label, base64, label);
        Files.writeString(path, pem, StandardCharsets.UTF_8);
    }

    /** 0600. A private key readable by every account on the host is not private. */
    private static void restrictToOwner(Path path) {
        try {
            Files.setPosixFilePermissions(path, Set.of(
                    PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
        } catch (IOException | UnsupportedOperationException ex) {
            // Windows has no POSIX permissions. Worth saying out loud rather than pretending we set them.
            log.warn("Could not restrict permissions on {}", path);
        }
    }

    /** Resolves a {@code file:} location to a path we can write; null for anything else. */
    private static Path toWritablePath(String location) {
        if (!location.startsWith("file:")) {
            return null;
        }
        return Path.of(location.substring("file:".length()));
    }
}
