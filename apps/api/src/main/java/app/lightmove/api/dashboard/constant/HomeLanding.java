package app.lightmove.api.dashboard.constant;

import app.lightmove.api.common.constant.ApiValueEnum;

/** Where a member lands after signing in to this workspace. */
public enum HomeLanding implements ApiValueEnum {
    HOME,
    POSITIONS;

    @Override
    public String value() {
        return name();
    }
}
