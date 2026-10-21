package app.lightmove.api.task.service;

import app.lightmove.api.candidate.dto.CandidateNextActionDto;
import app.lightmove.api.candidate.dto.CandidateOpenTaskDto;
import app.lightmove.api.candidate.dto.NextActionAssigneeDto;
import app.lightmove.api.candidate.service.NextActionLookup;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.task.constant.TaskStatus;
import app.lightmove.api.task.model.Task;
import app.lightmove.api.task.repository.TaskRepository;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Answers the Pipeline page's next action for {@code candidate}: each executive's earliest open task. */
@Component
@RequiredArgsConstructor
class NextActionLookupAdapter implements NextActionLookup {

    private final TaskRepository tasks;
    private final TaskPeople taskPeople;

    @Override
    public Map<UUID, CandidateNextActionDto> nextActionsOf(UUID projectId, Collection<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<Task>> open = tasks.findOfCandidates(projectId, candidateIds, TaskStatus.OPEN).stream()
                .collect(Collectors.groupingBy(Task::getCandidateId));
        Map<UUID, User> named = taskPeople.usersOf(open.values().stream().flatMap(List::stream)
                .flatMap(task -> Stream.of(task.getAssigneeUserId(), task.getCreatedBy()))
                .toList());
        return open.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> {
            Task first = entry.getValue().stream().sorted(TaskOrder.SOONEST_FIRST).findFirst().orElseThrow();
            User assignee = named.get(first.getAssigneeUserId());
            User setter = first.getCreatedBy() == null ? null : named.get(first.getCreatedBy());
            return new CandidateNextActionDto(first.getId(), first.getTitle(), first.getKind().value(),
                    first.getDueAt(), new NextActionAssigneeDto(first.getAssigneeUserId(),
                            assignee == null ? null : assignee.getFullName(),
                            assignee == null ? null : assignee.getAvatarUrl()),
                    entry.getValue().size(), setter == null ? null : setter.getFullName());
        }));
    }

    @Override
    public Map<UUID, List<CandidateOpenTaskDto>> openTasksOf(UUID projectId, Collection<UUID> candidateIds) {
        if (candidateIds.isEmpty()) {
            return Map.of();
        }
        List<Task> open = tasks.findOfCandidates(projectId, candidateIds, TaskStatus.OPEN);
        Map<UUID, User> assignees = taskPeople.usersOf(open.stream().map(Task::getAssigneeUserId).toList());
        return open.stream()
                .sorted(TaskOrder.SOONEST_FIRST)
                .collect(Collectors.groupingBy(Task::getCandidateId, LinkedHashMap::new,
                        Collectors.mapping(task -> openTaskOf(task, assignees.get(task.getAssigneeUserId())),
                                Collectors.toList())));
    }

    private static CandidateOpenTaskDto openTaskOf(Task task, User assignee) {
        return new CandidateOpenTaskDto(task.getId(), task.getTitle(), task.getKind().value(),
                task.getRule() == null ? null : task.getRule().value(), task.getDueAt(),
                new NextActionAssigneeDto(task.getAssigneeUserId(), assignee == null ? null : assignee.getFullName(),
                        assignee == null ? null : assignee.getAvatarUrl()));
    }
}
