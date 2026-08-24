package app.lightmove.api.core.security.constant;

/** The shape of the machine a session runs on, so the client can pick an icon for it. */
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
