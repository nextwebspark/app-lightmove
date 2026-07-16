package app.lightmove.api.core.security.constant;

/** How an identity was established. {@code LOCAL} means an email plus a password we hashed ourselves. */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
