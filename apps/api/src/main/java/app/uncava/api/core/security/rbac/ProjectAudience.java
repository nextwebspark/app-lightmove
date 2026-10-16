package app.uncava.api.core.security.rbac;

/**
 * Who is reading a position's executives: staff working it, or a client seat reading it. Resolved once
 * where the caller is known ({@link ProjectAccess#audienceOf}) and handed down, so a read that cannot
 * name its reader does not compile rather than seeing everyone.
 */
public enum ProjectAudience {
    STAFF,
    CLIENT;

    /** A client seat never learns of an executive hidden from the client. */
    public boolean clientVisibleOnly() {
        return this == CLIENT;
    }
}
