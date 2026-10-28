package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Which voices on a call are the team's: a named speaker whose name is an active staff member's, or the name of
 * whoever sent the bot — and which are the executive's, since a third person on the call (a colleague from outside the
 * workspace, a hiring manager) is neither. Decided here so no model has to guess from a display name, and so nobody
 * else's words count as the executive's evidence or wear their face.
 */
@Component
@RequiredArgsConstructor
class TeamVoices {

    private final WorkspaceAccess workspaceAccess;
    private final UserRepository users;

    Set<String> namesOf(UUID workspaceId, Collection<UUID> alsoUserIds) {
        List<UUID> userIds = Stream.concat(
                        workspaceAccess.activeStaff(workspaceId).stream().map(WorkspaceMember::getUserId),
                        alsoUserIds.stream())
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        return users.findAllById(userIds).stream()
                .map(User::getFullName)
                .map(TeamVoices::nameKey)
                .filter(name -> !name.isEmpty())
                .collect(Collectors.toSet());
    }

    static boolean isTeam(Set<String> team, String speaker, boolean named) {
        return named && team.contains(nameKey(speaker));
    }

    /**
     * Whether a voice is named as the executive: every word of one name is a word of the other, so "Hakan" and
     * "Hakan Demir" are one person and a colleague of theirs on the call is not. Never a shared word alone — two
     * Mohammeds are two people.
     */
    static boolean isNamedAs(String executiveName, String speaker, boolean named) {
        if (!named) {
            return false;
        }
        Set<String> executive = wordsOf(executiveName);
        Set<String> voice = wordsOf(speaker);
        return !executive.isEmpty() && !voice.isEmpty()
                && (executive.containsAll(voice) || voice.containsAll(executive));
    }

    /**
     * The voices whose words are the executive's: those named as them, and where none is — an executive often joins
     * under a device's name — every voice not the team's.
     */
    static Set<String> executiveVoicesOf(String executiveName, Collection<SpeakerVoice> otherVoices) {
        Set<String> named = otherVoices.stream()
                .filter(voice -> isNamedAs(executiveName, voice.speaker(), voice.named()))
                .map(SpeakerVoice::speaker)
                .collect(Collectors.toSet());
        if (!named.isEmpty()) {
            return named;
        }
        return otherVoices.stream().map(SpeakerVoice::speaker).filter(Objects::nonNull).collect(Collectors.toSet());
    }

    /** A voice heard on the call, as the transcript names it. */
    record SpeakerVoice(String speaker, boolean named) {}

    private static Set<String> wordsOf(String name) {
        if (name == null) {
            return Set.of();
        }
        return Arrays.stream(name.toLowerCase(Locale.ROOT).split("[^\\p{L}\\p{N}]+"))
                .filter(word -> !word.isEmpty())
                .collect(Collectors.toSet());
    }

    static String nameKey(String name) {
        return name == null ? "" : name.strip().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }
}
