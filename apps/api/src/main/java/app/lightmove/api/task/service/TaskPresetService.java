package app.lightmove.api.task.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.task.constant.TaskKind;
import app.lightmove.api.task.dto.CreateTaskPresetRequest;
import app.lightmove.api.task.dto.TaskPresetResponse;
import app.lightmove.api.task.dto.TaskPresetSuggestionResponse;
import app.lightmove.api.task.dto.UpdateTaskPresetRequest;
import app.lightmove.api.task.model.TaskPreset;
import app.lightmove.api.task.repository.TaskPresetRepository;
import app.lightmove.api.task.repository.TaskPresetUsage;
import app.lightmove.api.task.repository.TaskRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Clock;
import java.time.Duration;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The workspace's next-action presets. Every staff member reads them in the modal; adding, renaming, ordering
 * and retiring are an admin's, from Settings. A preset is never deleted: a task filed from it keeps it.
 */
@Service
@RequiredArgsConstructor
public class TaskPresetService {

    private static final String PRESET_TARGET = "task_preset";
    private static final Duration TYPED_LOOKBACK = Duration.ofDays(90);

    /** What every workspace starts from — V133 seeded the workspaces that existed then. */
    private static final List<StarterPreset> STARTERS = List.of(
            new StarterPreset("Call to qualify interest", TaskKind.CALL, 1),
            new StarterPreset("Send follow-up email", TaskKind.EMAIL, 2),
            new StarterPreset("Schedule a call", TaskKind.MEETING, 1),
            new StarterPreset("Reference check", TaskKind.TODO, 5),
            new StarterPreset("Share profile with hiring manager", TaskKind.TODO, 1),
            new StarterPreset("Request CV", TaskKind.EMAIL, 2),
            new StarterPreset("Debrief after meeting", TaskKind.FOLLOW_UP, 0),
            new StarterPreset("Re-engage later", TaskKind.FOLLOW_UP, 30));

    private final TaskPresetRepository presets;
    private final TaskRepository tasks;
    private final AuditService audit;
    private final Clock clock;

    /** Every preset, retired ones included, in the catalog's order. Seeds a catalog never seeded. */
    @Transactional
    public List<TaskPresetResponse> catalog(UUID workspaceId) {
        if (!presets.existsByWorkspaceId(workspaceId)) {
            for (int index = 0; index < STARTERS.size(); index++) {
                StarterPreset starter = STARTERS.get(index);
                presets.insertIfAbsent(workspaceId, starter.label(), starter.kind().name(), starter.dueOffsetDays(),
                        index + 1);
            }
        }
        Map<UUID, Long> usage = usageOf(workspaceId);
        return presets.findByWorkspaceIdOrderByPositionAscLabelAsc(workspaceId).stream()
                .map(preset -> toDto(preset, usage.getOrDefault(preset.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<TaskPresetSuggestionResponse> suggestions(UUID workspaceId) {
        return tasks.typedTitles(workspaceId, clock.instant().minus(TYPED_LOOKBACK)).stream()
                .map(typed -> new TaskPresetSuggestionResponse(typed.getLabel(), typed.getUses()))
                .toList();
    }

    @Transactional
    public TaskPresetResponse create(UUID userId, UUID workspaceId, CreateTaskPresetRequest request,
                                     HttpServletRequest httpRequest) {
        String label = request.label().strip();
        refuseTaken(workspaceId, label, null);
        TaskPreset preset = presets.save(TaskPreset.created(workspaceId, label, kindOf(request.kind()),
                request.dueOffsetDays(), presets.lastPositionOf(workspaceId) + 1, userId));
        audit.event(ProjectEventType.TASK_PRESET_CREATED).actor(userId).workspace(workspaceId)
                .target(PRESET_TARGET, preset.getId()).from(httpRequest)
                .record();
        return toDto(preset, 0);
    }

    @Transactional
    public TaskPresetResponse update(UUID userId, UUID workspaceId, UUID presetId, UpdateTaskPresetRequest request,
                                     HttpServletRequest httpRequest) {
        TaskPreset preset = presets.requireInWorkspace(presetId, workspaceId);
        if (request.label() != null) {
            String label = request.label().strip();
            if (label.isEmpty()) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "label", "A preset needs a name");
            }
            refuseTaken(workspaceId, label, presetId);
            preset.rename(label);
        }
        if (request.kind() != null) {
            preset.rekind(kindOf(request.kind()));
        }
        if (Boolean.TRUE.equals(request.clearDueOffset())) {
            preset.defaultDueIn(null);
        } else if (request.dueOffsetDays() != null) {
            preset.defaultDueIn(request.dueOffsetDays());
        }
        if (Boolean.TRUE.equals(request.retired())) {
            preset.retire();
        } else if (Boolean.FALSE.equals(request.retired())) {
            preset.restore();
        }
        audit.event(ProjectEventType.TASK_PRESET_UPDATED).actor(userId).workspace(workspaceId)
                .target(PRESET_TARGET, presetId).from(httpRequest)
                .detailIfPresent("renamed", request.label() == null ? null : "true")
                .detailIfPresent("retired", request.retired())
                .record();
        return toDto(preset, usageOf(workspaceId).getOrDefault(presetId, 0L));
    }

    /** Every live preset in its new order, retired ones after them as they were; any other set is refused. */
    @Transactional
    public List<TaskPresetResponse> reorder(UUID userId, UUID workspaceId, List<UUID> presetIds,
                                            HttpServletRequest httpRequest) {
        List<TaskPreset> catalog = presets.findByWorkspaceIdOrderByPositionAscLabelAsc(workspaceId);
        List<TaskPreset> live = catalog.stream().filter(preset -> !preset.isRetired()).toList();
        Map<UUID, TaskPreset> byId = live.stream().collect(Collectors.toMap(TaskPreset::getId, preset -> preset));
        if (presetIds.size() != live.size() || !byId.keySet().equals(new HashSet<>(presetIds))) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "That order does not match the presets",
                    "Reorder named " + presetIds.size() + " of " + live.size() + " live presets");
        }
        int position = 1;
        for (UUID presetId : presetIds) {
            byId.get(presetId).moveTo(position++);
        }
        for (TaskPreset retired : catalog.stream().filter(TaskPreset::isRetired).toList()) {
            retired.moveTo(position++);
        }
        audit.event(ProjectEventType.TASK_PRESETS_REORDERED).actor(userId).workspace(workspaceId)
                .from(httpRequest)
                .record();
        Map<UUID, Long> usage = usageOf(workspaceId);
        return catalog.stream()
                .sorted(Comparator.comparingInt(TaskPreset::getPosition))
                .map(preset -> toDto(preset, usage.getOrDefault(preset.getId(), 0L)))
                .toList();
    }

    /** A retired preset keeps its name: restoring it is the way to use the name again. */
    private void refuseTaken(UUID workspaceId, String label, UUID renamedId) {
        presets.findByLabel(workspaceId, label)
                .filter(existing -> !existing.getId().equals(renamedId))
                .ifPresent(existing -> {
                    throw ApiException.of(ErrorCode.TASK_PRESET_EXISTS);
                });
    }

    private Map<UUID, Long> usageOf(UUID workspaceId) {
        return tasks.usageOfPresets(workspaceId).stream()
                .collect(Collectors.toMap(TaskPresetUsage::getPresetId, TaskPresetUsage::getUses));
    }

    private static TaskKind kindOf(String kind) {
        return ApiValueEnum.require(TaskKind.class, kind, "task type");
    }

    private static TaskPresetResponse toDto(TaskPreset preset, long usage) {
        return new TaskPresetResponse(preset.getId(), preset.getLabel(), preset.getKind().value(),
                preset.getDueOffsetDays(), preset.getPosition(), preset.isRetired(), preset.getRetiredAt(), usage);
    }

    private record StarterPreset(String label, TaskKind kind, int dueOffsetDays) {}
}
