package app.lightmove.api.position.service;

import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.position.dto.OrgNodeDto;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

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

    /**
     * Drops the seats nobody filled in — but only the ones nothing reports to.
     *
     * <p>Both V39 and this package's docs promise that a seat with neither a title nor a name clears
     * itself, which is what makes the placeholders V39 creates from the old direct-report count
     * disappear once somebody actually works on the chart.
     *
     * <p><b>Only leaves.</b> Removing an unnamed seat that has children would leave every one of them
     * pointing at a parent that is no longer in the chart — the exact state
     * {@link #requireParentsResolve} exists to refuse, reached by the back door because filtering
     * happens after validation. An unnamed manager with named reports under it is a chart somebody is
     * halfway through drawing, and it keeps its box until they empty it.
     */
    static List<OrgNodeDto> withoutUnnamedLeaves(List<OrgNodeDto> chart) {
        Set<UUID> parents = chart.stream()
                .map(OrgNodeDto::parentNodeId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        return chart.stream()
                .filter(node -> node.mandateSeat()
                        || node.title() != null
                        || node.name() != null
                        || parents.contains(node.nodeId()))
                .toList();
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
        Set<UUID> ids = chart.stream().map(OrgNodeDto::nodeId).collect(Collectors.toSet());
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
                .collect(Collectors.toMap(OrgNodeDto::nodeId, Function.identity()));
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
