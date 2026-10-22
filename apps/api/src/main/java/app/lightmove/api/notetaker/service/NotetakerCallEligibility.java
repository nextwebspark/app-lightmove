package app.lightmove.api.notetaker.service;

import app.lightmove.api.candidate.service.CandidateOutreachService;
import app.lightmove.api.notetaker.constant.NotetakerPlatform;
import app.lightmove.api.notetaker.model.NotetakerCall;
import app.lightmove.api.outreach.model.KeptMeeting;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * The one reading of which calls the bot joins, shared by the scheduler and the drawer so the two never disagree:
 * copies grouped by {@link NotetakerCallKeys}, then the platform, the three switches and do not contact, each
 * asked once for the whole batch.
 */
@Component
@RequiredArgsConstructor
class NotetakerCallEligibility {

    private final NotetakerSwitches switches;
    private final CandidateOutreachService people;
    private final ChainedMeetingRecorder recorders;

    List<NotetakerCall> callsOf(List<KeptMeeting> meetings) {
        Map<String, List<KeptMeeting>> grouped = new LinkedHashMap<>();
        for (KeptMeeting meeting : meetings) {
            if (meeting.joinUrl() != null) {
                grouped.computeIfAbsent(meeting.workspaceId() + "|" + callKeyOf(meeting), key -> new ArrayList<>())
                        .add(meeting);
            }
        }
        if (grouped.isEmpty()) {
            return List.of();
        }
        List<KeptMeeting> callCopies = grouped.values().stream().flatMap(List::stream).toList();
        Set<UUID> workspacesOff = switches.workspacesSwitchedOff(callCopies.stream()
                .map(KeptMeeting::workspaceId).distinct().toList());
        Set<UUID> withBackup = recorders.backupAllowedAmong(callCopies.stream()
                .map(KeptMeeting::workspaceId).distinct().toList());
        Set<String> optedOut = switches.optedOutAmong(callCopies);
        Set<String> usersOff = switches.usersSwitchedOff(callCopies);
        Set<UUID> doNotContact = doNotContactAmong(callCopies);

        List<NotetakerCall> calls = new ArrayList<>();
        for (List<KeptMeeting> copies : grouped.values()) {
            KeptMeeting first = copies.getFirst();
            NotetakerPlatform platform = NotetakerPlatform.of(first.joinUrl(), first.conferencingProvider())
                    .orElse(null);
            boolean joinable = recorders.joins(platform, withBackup.contains(first.workspaceId()))
                    && NotetakerPlatform.hostingAt(first.joinUrl()).equals(Optional.of(platform));
            calls.add(new NotetakerCall(callKeyOf(first), List.copyOf(copies), platform, joinable,
                    !workspacesOff.contains(first.workspaceId()),
                    copies.stream().noneMatch(copy -> optedOut.contains(NotetakerSwitches.eventKeyOf(copy))),
                    copies.stream()
                            .filter(copy -> !usersOff.contains(NotetakerSwitches.userKeyOf(copy.userId(),
                                    copy.workspaceId())))
                            .min(Comparator.comparing((KeptMeeting copy) -> copy.userId().toString())),
                    copies.stream().anyMatch(copy -> doNotContact.contains(copy.personId()))));
        }
        return calls;
    }

    static String callKeyOf(KeptMeeting meeting) {
        return NotetakerCallKeys.of(meeting.joinUrl(), meeting.startsAt());
    }

    private Set<UUID> doNotContactAmong(List<KeptMeeting> copies) {
        Map<UUID, List<UUID>> peopleByWorkspace = copies.stream().collect(Collectors.groupingBy(
                KeptMeeting::workspaceId, Collectors.mapping(KeptMeeting::personId, Collectors.toList())));
        return peopleByWorkspace.entrySet().stream()
                .flatMap(entry -> people.doNotContactAmong(entry.getKey(), entry.getValue().stream().distinct()
                        .toList()).stream())
                .collect(Collectors.toSet());
    }
}
