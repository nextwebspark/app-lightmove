package app.lightmove.api.core.security.constant;

/**
 * The shape of the machine a session runs on, so the client can pick an icon for it.
 *
 * <p>Mirrored by hand in {@code apps/web/src/features/auth/api/types.ts}, which indexes an icon map on
 * it. Adding a member here without widening that union renders the row with no icon and compiles
 * clean on both sides — which is how EXTENSION shipped broken in review.
 */
public enum DeviceKind {
    DESKTOP,
    MOBILE,
    TABLET,

    /** LightMove Capture — not a machine shape, because its requests carry the host browser's own. */
    EXTENSION,

    UNKNOWN
}
