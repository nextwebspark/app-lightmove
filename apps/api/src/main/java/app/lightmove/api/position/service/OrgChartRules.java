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
 * The cross-element rules that make a submitted org chart a tree, which Bean Validation on a flat list
 * cannot express. Every message is a fixed sentence: nothing here interpolates what the caller sent.
 */
final class OrgChartRules {

    private OrgChartRules() {
    }

    /**
     * Drops seats with neither title nor name, but only leaves: this runs after validation, so removing
     * an unnamed parent would orphan its children — the state {@link #requireParentsResolve} refuses.
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
