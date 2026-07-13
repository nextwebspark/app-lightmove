package app.lightmove.api.auth.domain;

/** How an identity was established. {@code LOCAL} means an email plus a password we hashed ourselves. */
public enum AuthProvider {
    LOCAL,
    GOOGLE
}
