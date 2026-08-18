package app.lightmove.api.core.security.model;

import app.lightmove.api.core.security.constant.DeviceKind;

/** A User-Agent string reduced to what Settings → Active sessions shows: "macOS — Safari". */
public record DeviceDescription(DeviceKind kind, String label) {

    private static final DeviceDescription UNKNOWN = new DeviceDescription(DeviceKind.UNKNOWN, "Unknown device");

    public static DeviceDescription unknown() {
        return UNKNOWN;
    }
}
