package app.lightmove.api.startpage.constant;

import app.lightmove.api.common.constant.ApiValueEnum;

/** Where a member's Uncava opens in a workspace. HOME is the default and is never stored. */
public enum StartPageKind implements ApiValueEnum {
    HOME,
    POSITIONS,
    CANDIDATES,
    TASKS,
    DASHBOARD,
    POSITION;

    /** The pages a pure client is never shown, and so may never open on. */
    public boolean isStaffOnly() {
        return this == CANDIDATES || this == TASKS || this == DASHBOARD;
    }

    @Override
    public String value() {
        return name();
    }
}
