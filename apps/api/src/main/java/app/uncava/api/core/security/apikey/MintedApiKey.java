package app.uncava.api.core.security.apikey;

/** A key just made: the secret goes to its maker once, the hash and the hint to the row. */
public record MintedApiKey(String secret, String hash, String hint) {

    @Override
    public String toString() {
        return "MintedApiKey[hint=" + hint + "]";
    }
}
