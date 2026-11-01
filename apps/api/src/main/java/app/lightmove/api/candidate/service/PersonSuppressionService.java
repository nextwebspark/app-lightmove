package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.SuppressionKind;
import app.lightmove.api.candidate.constant.SuppressionReason;
import app.lightmove.api.candidate.dto.AddSuppressionRequest;
import app.lightmove.api.candidate.dto.AddedByResponse;
import app.lightmove.api.candidate.dto.SuppressionResponse;
import app.lightmove.api.candidate.model.SuppressionKey;
import app.lightmove.api.candidate.model.WorkspaceSuppression;
import app.lightmove.api.candidate.repository.WorkspaceSuppressionRepository;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.core.security.model.User;
import app.lightmove.api.core.security.repository.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
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

/**
 * The workspace's suppression list (#920): the LinkedIn profiles and email addresses no door may file again.
 * Every door asks it — the add form, the plugin and a spreadsheet are refused with {@code PERSON_SUPPRESSED},
 * while Find executives and People Search leave a suppressed person out before anything is filed or fetched.
 *
 * <p>Keys are compared as hashes, built the way {@link PersonMatcher} reads a person, so the list recognises
 * exactly the people the matcher would.
 */
@Service
@RequiredArgsConstructor
public class PersonSuppressionService {

    private static final String SUPPRESSION_TARGET = "suppression";

    private final WorkspaceSuppressionRepository suppressions;
    private final UserRepository users;
    private final AuditService audit;

    /** Refuses a filing or an edit carrying a suppressed profile or address. */
    @Transactional(readOnly = true)
    public void refuseSuppressed(UUID workspaceId, String linkedinUrl, Collection<String> emails) {
        if (isSuppressed(workspaceId, linkedinUrl, emails)) {
            throw ApiException.of(ErrorCode.PERSON_SUPPRESSED);
        }
    }

    @Transactional(readOnly = true)
    public boolean isSuppressed(UUID workspaceId, String linkedinUrl, Collection<String> emails) {
        SuppressionKey profile = SuppressionKey.ofProfileUrlOrNull(workspaceId, linkedinUrl);
        if (profile != null && suppressions.findByWorkspaceIdAndKindAndValueHash(workspaceId, profile.kind(),
                profile.hash()).isPresent()) {
            return true;
        }
        List<String> emailHashes = emails.stream()
                .map(email -> SuppressionKey.ofEmailOrNull(workspaceId, email))
                .filter(Objects::nonNull)
                .map(SuppressionKey::hash)
                .distinct()
                .toList();
        return !emailHashes.isEmpty() && !suppressions.findByWorkspaceIdAndKindAndValueHashIn(workspaceId,
                SuppressionKind.EMAIL, emailHashes).isEmpty();
    }

    /** Which of these profile slugs the list holds, as they were given — one query for a page of hits. */
    @Transactional(readOnly = true)
    public Set<String> suppressedProfileSlugsOf(UUID workspaceId, Collection<String> slugs) {
        Map<String, String> slugByHash = slugs.stream()
                .filter(slug -> slug != null && !slug.isBlank())
                .distinct()
                .collect(Collectors.toMap(slug -> SuppressionKey.ofProfileSlug(workspaceId, slug).hash(),
                        Function.identity(), (first, second) -> first, LinkedHashMap::new));
        if (slugByHash.isEmpty()) {
            return Set.of();
        }
        return suppressions.findByWorkspaceIdAndKindAndValueHashIn(workspaceId, SuppressionKind.PROFILE_SLUG,
                        slugByHash.keySet()).stream()
                .map(suppression -> slugByHash.get(suppression.getValueHash()))
                .collect(Collectors.toSet());
    }

    /** Puts keys on the list; one already there keeps its first reason. Answers how many were new. */
    @Transactional
    public int suppress(UUID workspaceId, UUID userId, Collection<SuppressionKey> keys, SuppressionReason reason,
                        String note) {
        int added = 0;
        for (SuppressionKey key : keys) {
            added += suppressions.insertIfAbsent(workspaceId, key.kind().name(), key.hash(), reason.name(), note,
                    userId);
        }
        return added;
    }

    @Transactional(readOnly = true)
    public List<SuppressionResponse> list(UUID workspaceId) {
        List<WorkspaceSuppression> rows = suppressions.findByWorkspaceIdOrderByCreatedAtDesc(workspaceId);
        return toResponses(rows);
    }

    /** An admin's entry: an objection, or someone the firm was asked never to approach. */
    @Transactional
    public List<SuppressionResponse> add(UUID userId, UUID workspaceId, AddSuppressionRequest request,
                                         HttpServletRequest httpRequest) {
        SuppressionReason reason = ApiValueEnum.parse(SuppressionReason.class, request.reason(),
                SuppressionReason.MANUAL, "reason");
        if (reason == SuppressionReason.ERASED) {
            throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "reason",
                    "An erased person is added to the list by the erasure itself");
        }
        List<SuppressionKey> keys = keysOf(workspaceId, request);
        String note = request.note() == null || request.note().isBlank() ? null : request.note().strip();
        int added = suppress(workspaceId, userId, keys, reason, note);

        audit.event(ProjectEventType.PERSON_SUPPRESSION_ADDED).actor(userId).workspace(workspaceId)
                .target(SUPPRESSION_TARGET, null).from(httpRequest)
                .detail("kinds", keys.stream().map(key -> key.kind().value()).toList())
                .detail("reason", reason.value())
                .detail("added", added)
                .record();
        List<WorkspaceSuppression> rows = new ArrayList<>();
        keys.forEach(key -> suppressions.findByWorkspaceIdAndKindAndValueHash(workspaceId, key.kind(), key.hash())
                .ifPresent(rows::add));
        return toResponses(rows);
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

    private static List<SuppressionKey> keysOf(UUID workspaceId, AddSuppressionRequest request) {
        List<SuppressionKey> keys = new ArrayList<>();
        if (request.linkedinUrl() != null && !request.linkedinUrl().isBlank()) {
            SuppressionKey profile = SuppressionKey.ofProfileUrlOrNull(workspaceId,
                    withScheme(request.linkedinUrl().strip()));
            if (profile == null) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "linkedinUrl",
                        "Enter a LinkedIn profile link, like linkedin.com/in/name");
            }
            keys.add(profile);
        }
        SuppressionKey email = SuppressionKey.ofEmailOrNull(workspaceId, request.email());
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
