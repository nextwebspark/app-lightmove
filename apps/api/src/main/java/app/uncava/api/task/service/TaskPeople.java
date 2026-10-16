package app.uncava.api.task.service;

import app.uncava.api.core.security.model.User;
import app.uncava.api.core.security.repository.UserRepository;
import app.uncava.api.task.dto.TaskPersonDto;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The teammates a list of tasks names, read in one query. */
@Component
@RequiredArgsConstructor
public class TaskPeople {

    private final UserRepository users;

    public Map<UUID, User> usersOf(Collection<UUID> userIds) {
        var ids = userIds.stream().filter(Objects::nonNull).distinct().toList();
        return ids.isEmpty() ? Map.of() : users.findAllById(ids).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
    }

    public static TaskPersonDto personOf(UUID userId, Map<UUID, User> known) {
        if (userId == null) {
            return null;
        }
        User user = known.get(userId);
        return new TaskPersonDto(userId, user == null ? null : user.getFullName(),
                user == null ? null : user.getAvatarUrl());
    }
}
