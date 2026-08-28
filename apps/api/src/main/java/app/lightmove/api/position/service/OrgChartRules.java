package app.lightmove.api.position.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.position.dto.OrgNodeDto;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

/**
 * What makes a submitted org chart a chart rather than a bag of boxes.
 *
 * <p>These are relationships between elements — one seat is the mandate's, every parent resolves,
 * nothing is its own ancestor — which Bean Validation on a flat list cannot express, so they are
 * checked here before anything is stored. A chart that broke any of them would render as either
 * nothing at all or an infinite loop, so it is refused rather than persisted and drawn.
 *
 * <p>Every message is a fixed sentence: nothing here interpolates what the caller sent.
 */
final class OrgChartRules {

    private OrgChartRules() {
    }

    static void validate(List<OrgNodeDto> chart) {
        requireUniqueIds(chart);
        requireExactlyOneMandateSeat(chart);
        requireParentsResolve(chart);
        requireNoCycles(chart);
    }

    private static void requireUniqueIds(List<OrgNodeDto> chart) {
        if (chart.stream().map(OrgNodeDto::nodeId).distinct().count() != chart.size()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "Two seats in the chart share an id");
        }
    }

    private static void requireExactlyOneMandateSeat(List<OrgNodeDto> chart) {
        long seats = chart.stream().filter(OrgNodeDto::mandateSeat).count();
        if (seats != 1) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "The chart must contain exactly one seat for this role");
        }
    }

    private static void requireParentsResolve(List<OrgNodeDto> chart) {
        Set<UUID> ids = chart.stream().map(OrgNodeDto::nodeId).collect(HashSet::new, Set::add, Set::addAll);
        boolean dangling = chart.stream()
                .map(OrgNodeDto::parentNodeId)
                .anyMatch(parent -> parent != null && !ids.contains(parent));
        if (dangling) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                    "A seat in the chart reports to one that is not in it");
        }
    }

    /**
     * Walks each seat up to a root. A cycle would make the chart undrawable and any later traversal
     * of it — the manager, the direct reports, a layout pass — run forever.
     */
    private static void requireNoCycles(List<OrgNodeDto> chart) {
        Map<UUID, OrgNodeDto> byId = chart.stream()
                .collect(java.util.stream.Collectors.toMap(OrgNodeDto::nodeId, Function.identity()));
        for (OrgNodeDto start : chart) {
            Set<UUID> walked = new HashSet<>();
            OrgNodeDto node = start;
            while (node != null && node.parentNodeId() != null) {
                if (!walked.add(node.nodeId())) {
                    throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                            "The chart loops back on itself");
                }
                node = byId.get(node.parentNodeId());
            }
        }
    }
}
