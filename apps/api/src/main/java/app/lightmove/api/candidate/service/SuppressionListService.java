package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.SuppressionReason;
import app.lightmove.api.candidate.dto.AddSuppressionRequest;
import app.lightmove.api.candidate.dto.AddedByResponse;
import app.lightmove.api.candidate.dto.SuppressionResponse;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.SuppressionAddition;
import app.lightmove.api.candidate.model.SuppressionKey;
import app.lightmove.api.candidate.model.WorkspaceSuppression;
import app.lightmove.api.candidate.repository.WorkspaceSuppressionRepository;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.crypto.model.KeyedHash;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Writes the workspace's suppression list: an admin's entries and lifts, audited, and the erasure's entries. */
@Service
@RequiredArgsConstructor
public class SuppressionListService {

    private static final String SUPPRESSION_TARGET = "suppression";

    private final WorkspaceSuppressionRepository suppressions;
    private final SuppressionEntries entries;
    private final UserRepository users;
    private final AuditService audit;

    /** Puts keys on the list; one already there, under any key of the keyset, keeps its first reason. */
    @Transactional
    public int suppress(UUID workspaceId, UUID userId, Collection<SuppressionKey> keys, SuppressionReason reason,
                        String note) {
        Set<SuppressionKey> distinctKeys = new LinkedHashSet<>(keys);
        Map<SuppressionKey, WorkspaceSuppression> held = entries.entriesFor(workspaceId, distinctKeys);
        int added = 0;
        for (SuppressionKey key : distinctKeys) {
            if (held.containsKey(key)) {
                continue;
            }
            KeyedHash hash = entries.hashToStore(workspaceId, key);
            added += suppressions.insertIfAbsent(workspaceId, key.kind().name(), hash.hex(), hash.keyId(),
                    reason.name(), note, userId);
        }
        return added;
    }

    /** Suppresses the person's profile and every address they hold; answers how many keys were new. */
    @Transactional
    public int suppressPerson(Person person, UUID userId, SuppressionReason reason) {
        List<SuppressionKey> keys = new ArrayList<>();
        SuppressionKey profile = SuppressionKey.ofProfileUrlOrNull(person.getLinkedinUrl());
        if (profile != null) {
            keys.add(profile);
        }
        person.emailContacts().stream()
                .map(contact -> SuppressionKey.ofEmailOrNull(contact.getValue()))
                .filter(Objects::nonNull)
                .forEach(keys::add);
        return suppress(person.getWorkspaceId(), userId, keys, reason, null);
    }

    @Transactional(readOnly = true)
    public List<SuppressionResponse> list(UUID workspaceId) {
        return toResponses(suppressions.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId));
    }

    /** An admin's entry: an objection, or someone the firm was asked never to approach. */
    @Transactional
    public SuppressionAddition add(UUID userId, UUID workspaceId, AddSuppressionRequest request,
                                   HttpServletRequest httpRequest) {
        SuppressionReason reason = ApiValueEnum.parse(SuppressionReason.class, request.reason(),
                SuppressionReason.MANUAL, "reason");
        if (reason == SuppressionReason.ERASED) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "reason",
                    "An erased person is added to the list by the erasure itself");
        }
        List<SuppressionKey> keys = keysOf(request);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        int added = suppress(workspaceId, userId, keys, reason, note);

        audit.event(ProjectEventType.PERSON_SUPPRESSION_ADDED).actor(userId).workspace(workspaceId)
                .target(SUPPRESSION_TARGET, null).from(httpRequest)
                .detail("kinds", keys.stream().map(key -> key.kind().value()).toList())
                .detail("reason", reason.value())
                .detail("added", added)
                .record();
        List<WorkspaceSuppression> rows = List.copyOf(entries.entriesFor(workspaceId, keys).values());
        return new SuppressionAddition(toResponses(rows), added);
    }

    /** Lifts an entry: the person may be filed again from then on. */
    @Transactional
    public void remove(UUID userId, UUID workspaceId, UUID suppressionId, HttpServletRequest httpRequest) {
        WorkspaceSuppression suppression = suppressions.findByIdAndWorkspaceId(suppressionId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
        suppressions.delete(suppression);
        audit.event(ProjectEventType.PERSON_SUPPRESSION_REMOVED).actor(userId).workspace(workspaceId)
                .target(SUPPRESSION_TARGET, suppressionId).from(httpRequest)
                .detail("kind", suppression.getKind().value())
                .detail("reason", suppression.getReason().value())
                .record();
    }

    private static List<SuppressionKey> keysOf(AddSuppressionRequest request) {
        List<SuppressionKey> keys = new ArrayList<>();
        if (request.linkedinUrl() != null && !request.linkedinUrl().isBlank()) {
            SuppressionKey profile = SuppressionKey.ofProfileUrlOrNull(withScheme(request.linkedinUrl().strip()));
            if (profile == null) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "linkedinUrl",
                        "Enter a LinkedIn profile link, like linkedin.com/in/name");
            }
            keys.add(profile);
        }
        SuppressionKey email = SuppressionKey.ofEmailOrNull(request.email());
        if (email != null) {
            keys.add(email);
        }
        if (keys.isEmpty()) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "linkedinUrl",
                    "Enter a LinkedIn profile link or an email address");
        }
        return keys;
    }

    /** A profile link pasted without its scheme ({@code linkedin.com/in/…}) still names the profile. */
    private static String withScheme(String url) {
        return url.contains("://") ? url : "https://" + url;
    }

    private List<SuppressionResponse> toResponses(List<WorkspaceSuppression> rows) {
        Set<UUID> adderIds = rows.stream().map(WorkspaceSuppression::getCreatedBy).filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<UUID, User> adders = users.findAllById(adderIds).stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return rows.stream()
                .map(row -> new SuppressionResponse(row.getId(), row.getKind().value(), row.getReason().value(),
                        row.getNote(), adderOf(adders.get(row.getCreatedBy())), row.getCreatedAt()))
                .toList();
    }

    private static AddedByResponse adderOf(User adder) {
        return adder == null ? null : new AddedByResponse(adder.getId(), adder.getFullName(), adder.getAvatarUrl());
    }
}
