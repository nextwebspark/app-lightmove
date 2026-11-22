package app.lightmove.api.dashboard.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.dashboard.constant.DashboardScope;
import app.lightmove.api.dashboard.constant.HomeTileKind;
import app.lightmove.api.dashboard.dto.DashboardWidgetDto;
import app.lightmove.api.dashboard.dto.HomeLayoutDto;
import app.lightmove.api.dashboard.dto.HomeLayoutResponse;
import app.lightmove.api.dashboard.dto.HomeTileDto;
import app.lightmove.api.dashboard.model.DashboardWidget;
import app.lightmove.api.dashboard.model.HomeLayout;
import app.lightmove.api.dashboard.model.HomeTile;
import app.lightmove.api.dashboard.model.WidgetLayout;
import app.lightmove.api.dashboard.repository.HomeLayoutRepository;
import app.lightmove.api.outreach.constant.ConversationGroup;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A member's own Home in the workspace: absent until they first save, then their tiles and Needs you groups.
 * Home's charts are workspace library charts, checked as a workspace dashboard's are ({@code DashboardWidgetValidator}).
 */
@Service
@RequiredArgsConstructor
public class HomeLayoutService {

    private static final String ONE_PER_MEMBER = "app_lm_home_layout_member_uq";

    private final HomeLayoutRepository layouts;
    private final DashboardWidgetValidator widgets;

    @Transactional(readOnly = true)
    public HomeLayoutResponse get(UUID userId, UUID workspaceId) {
        return new HomeLayoutResponse(layouts.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(HomeLayoutService::toDto).orElse(null));
    }

    /**
     * Saves over the layout the request was opened at, and only that one: another window's save since then, or a
     * reset, is {@code HOME_LAYOUT_STALE} rather than silently overwritten. A request opened on the default sends no
     * version and may only found the row.
     */
    @Transactional
    public HomeLayoutResponse save(UUID userId, UUID workspaceId, HomeLayoutDto request) {
        Optional<HomeLayout> stored = layouts.findByWorkspaceIdAndUserId(workspaceId, userId);
        requireOpenedAt(stored, request.version());
        HomeLayout layout = stored.orElseGet(() -> HomeLayout.startFor(workspaceId, userId));
        layout.record(tilesOf(request.tiles(), layout.charts()), needsOf(request.needs()));
        try {
            return new HomeLayoutResponse(toDto(layouts.saveAndFlush(layout)));
        } catch (ObjectOptimisticLockingFailureException raced) {
            throw ApiException.of(ErrorCode.HOME_LAYOUT_STALE);
        } catch (DataIntegrityViolationException violation) {
            // Two windows founding the row at once; any other constraint is a real fault and keeps its own answer.
            if (String.valueOf(violation.getMostSpecificCause().getMessage()).contains(ONE_PER_MEMBER)) {
                throw ApiException.of(ErrorCode.HOME_LAYOUT_STALE);
            }
            throw violation;
        }
    }

    /**
     * Back to the default everyone starts from, which keeps following the default as it changes. Like a save, only
     * over the layout the request was opened at: a window opened on the default, or on an older save, sending the
     * default back would otherwise delete a layout another window saved since. With no layout stored the person is
     * already on the default, so there is nothing stale to refuse, whatever version was sent.
     */
    @Transactional
    public void reset(UUID userId, UUID workspaceId, Long openedVersion) {
        Optional<HomeLayout> stored = layouts.findByWorkspaceIdAndUserId(workspaceId, userId);
        if (stored.isEmpty()) {
            return;
        }
        requireOpenedAt(stored, openedVersion);
        try {
            layouts.delete(stored.get());
            layouts.flush();
        } catch (ObjectOptimisticLockingFailureException raced) {
            throw ApiException.of(ErrorCode.HOME_LAYOUT_STALE);
        }
    }

    /** A write goes over the layout it was opened at and no other: null opened the default, so no row may exist. */
    private static void requireOpenedAt(Optional<HomeLayout> stored, Long openedVersion) {
        if (!Objects.equals(stored.map(HomeLayout::getVersion).orElse(null), openedVersion)) {
            throw ApiException.of(ErrorCode.HOME_LAYOUT_STALE);
        }
    }

    private List<HomeTile> tilesOf(List<HomeTileDto> sent, List<DashboardWidget> storedCharts) {
        List<HomeTileKind> kinds = sent.stream()
                .map(tile -> ApiValueEnum.require(HomeTileKind.class, tile.kind(), "Home tile"))
                .toList();
        Set<HomeTileKind> sections = EnumSet.noneOf(HomeTileKind.class);
        List<DashboardWidgetDto> charts = new ArrayList<>();
        for (int index = 0; index < sent.size(); index++) {
            HomeTileDto tile = sent.get(index);
            HomeTileKind kind = kinds.get(index);
            if (kind.isChart()) {
                charts.add(new DashboardWidgetDto(tile.queryId(), tile.chart(), tile.options(), tile.title(),
                        tile.layout(), null));
                continue;
            }
            if (!sections.add(kind)) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Home holds one " + kind + " tile");
            }
            requireReadable(kind, tile.layout());
        }
        if (!sections.contains(HomeTileKind.NEEDS)) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Needs you is always on Home");
        }
        if (charts.size() > HomeLayout.MAX_CHARTS) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Home holds up to " + HomeLayout.MAX_CHARTS + " charts");
        }
        List<DashboardWidget> checked = widgets.checked(DashboardScope.WORKSPACE, charts, storedCharts);
        if (checked.size() != charts.size()) {
            throw new IllegalStateException("The chart check must answer one chart for each chart sent");
        }
        List<HomeTile> tiles = new ArrayList<>(sent.size());
        int chartIndex = 0;
        for (int index = 0; index < sent.size(); index++) {
            HomeTileKind kind = kinds.get(index);
            tiles.add(kind.isChart()
                    ? HomeTile.chart(checked.get(chartIndex++))
                    : HomeTile.section(kind, sent.get(index).layout()));
        }
        return tiles;
    }

    private static void requireReadable(HomeTileKind kind, WidgetLayout layout) {
        if (!layout.fitsTheGrid()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Tile " + kind + " is off the grid");
        }
        if (layout.w() < kind.minWidth() || layout.h() < kind.minHeight()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "Tile " + kind + " is at least " + kind.minWidth() + " × " + kind.minHeight());
        }
    }

    private static Map<String, Boolean> needsOf(Map<String, Boolean> sent) {
        Map<String, Boolean> needs = new LinkedHashMap<>();
        for (ConversationGroup group : ConversationGroup.values()) {
            Boolean shown = sent == null ? null : sent.get(group.name());
            needs.put(group.name(), shown == null || shown);
        }
        if (sent != null && !needs.keySet().containsAll(sent.keySet())) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Needs you has no such group");
        }
        return needs;
    }

    private static HomeLayoutDto toDto(HomeLayout layout) {
        return new HomeLayoutDto(layout.getTiles().stream().map(HomeTileDto::from).toList(), layout.getNeeds(),
                layout.getVersion());
    }
}
