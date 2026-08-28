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

    /**
     * LightMove Capture, the browser extension. Not a machine shape at all, and that is the point: the
     * extension's requests carry the host browser's User-Agent, so without a kind of its own its
     * session would appear in the list as a second, indistinguishable copy of the browser it lives in.
     */
    EXTENSION,

    UNKNOWN
}
