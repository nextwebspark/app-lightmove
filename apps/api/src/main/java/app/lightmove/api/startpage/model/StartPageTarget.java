package app.lightmove.api.startpage.model;

import app.lightmove.api.startpage.constant.PositionSection;
import app.lightmove.api.startpage.constant.StartPageKind;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/** The page a start page names: a dashboard's id for DASHBOARD, a position's id and tab for POSITION, else neither. */
public record StartPageTarget(
        StartPageKind kind,
        @Nullable UUID dashboardId,
        @Nullable UUID projectId,
        @Nullable PositionSection section
) {

    public static final StartPageTarget HOME = new StartPageTarget(StartPageKind.HOME, null, null, null);
}
