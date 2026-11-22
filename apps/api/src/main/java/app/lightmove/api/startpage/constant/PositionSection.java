package app.lightmove.api.startpage.constant;

import app.lightmove.api.common.constant.ApiValueEnum;

/** The tab of a position a start page opens on. Outreach is staff work; every other tab is the seat's to read. */
public enum PositionSection implements ApiValueEnum {
    BRIEF,
    STRATEGY,
    UNIVERSE,
    OUTREACH,
    DASHBOARD;

    @Override
    public String value() {
        return name();
    }
}
