package app.lightmove.api.notetaker.service;

import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.rbac.WorkspaceAccess;
import app.lightmove.api.core.security.repository.UserRepository;
import app.lightmove.api.notetaker.model.SpeakerVoice;
import app.lightmove.api.workspace.model.WorkspaceMember;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
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
 * whoever sent the bot — and which are the executive's, since a third person on the call (a colleague from outside
 * the workspace, a hiring manager) is neither. Decided here so no model has to guess from a display name, and so nobody
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
     * The voices on a call whose words are the executive's, decided once for the panel and the coach alike. A voice is
     * theirs when it carries their whole name ("Dr. Hakan Demir"), two or more words of it ("Hakan Demir" for "Hakan
     * Murat Demir"), or one word of it no other voice on the call shares — a "Mohammed" beside a "Mohammed Hassan" is
     * nobody's. Where no voice is named so, the one voice not the team's is theirs, since an executive often joins
     * under a device's name; with several such voices nobody is told apart, and none is.
     */
    static Set<String> executiveVoicesOf(String executiveName, Collection<SpeakerVoice> voices, Set<String> team) {
        List<SpeakerVoice> others = voices.stream()
                .filter(voice -> voice.speaker() != null && !isTeam(team, voice.speaker(), voice.named()))
                .collect(Collectors.toMap(SpeakerVoice::speaker, voice -> voice, (first, second) -> first,
                        LinkedHashMap::new))
                .values().stream().toList();
        Set<String> executive = wordsOf(executiveName);
        Set<String> named = others.stream()
                .filter(voice -> voice.named() && isNamedAs(executive, wordsOf(voice.speaker()), others, voice))
                .map(SpeakerVoice::speaker)
                .collect(Collectors.toSet());
        if (!named.isEmpty()) {
            return named;
        }
        return others.size() == 1 ? Set.of(others.getFirst().speaker()) : Set.of();
    }

    private static boolean isNamedAs(Set<String> executive, Set<String> voice, List<SpeakerVoice> others,
                                     SpeakerVoice speaking) {
        if (executive.isEmpty() || voice.isEmpty()) {
            return false;
        }
        if (voice.containsAll(executive) || (voice.size() > 1 && executive.containsAll(voice))) {
            return true;
        }
        String word = voice.iterator().next();
        return voice.size() == 1 && executive.contains(word) && others.stream()
                .filter(other -> !other.speaker().equals(speaking.speaker()))
                .noneMatch(other -> wordsOf(other.speaker()).contains(word));
    }

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
