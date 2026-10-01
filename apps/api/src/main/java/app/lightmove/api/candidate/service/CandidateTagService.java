package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.CandidateTagColour;
import app.lightmove.api.candidate.dto.CandidateTagResponse;
import app.lightmove.api.candidate.dto.CreateCandidateTagRequest;
import app.lightmove.api.candidate.dto.UpdateCandidateTagRequest;
import app.lightmove.api.candidate.model.CandidateTag;
import app.lightmove.api.candidate.model.CandidateTagUsage;
import app.lightmove.api.candidate.repository.CandidateTagRepository;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A workspace's tag catalog. Any staff member may add a tag from a person's drawer; renaming,
 * recolouring and retiring are an admin's, from Settings. A tag is never deleted: retiring one stops it
 * being offered, while the people who hold it keep it and their timeline still names it.
 */
@Service
@RequiredArgsConstructor
public class CandidateTagService {

    private static final String TAG_TARGET = "candidate_tag";

    /** What every workspace starts from — V98 seeded the workspaces that existed then. */
    private static final List<StarterTag> STARTERS = List.of(
            new StarterTag("Open to work", CandidateTagColour.GREEN),
            new StarterTag("Open to relocate", CandidateTagColour.ACCENT),
            new StarterTag("Passive", CandidateTagColour.NEUTRAL),
            new StarterTag("Referral", CandidateTagColour.VIOLET),
            new StarterTag("Prior placement", CandidateTagColour.ADJACENT),
            new StarterTag("Interviewed before", CandidateTagColour.INFERRED));

    private final CandidateTagRepository tags;
    private final AuditService audit;

    /** Every tag, retired ones included, with how many people hold each. Seeds a catalog never seeded. */
    @Transactional
    public List<CandidateTagResponse> catalog(UUID workspaceId) {
        if (!tags.existsByWorkspaceId(workspaceId)) {
            STARTERS.forEach(starter -> tags.insertIfAbsent(workspaceId, starter.label(), starter.colour().name()));
        }
        Map<UUID, Long> holders = tags.usageOf(workspaceId).stream()
                .collect(Collectors.toMap(CandidateTagUsage::getTagId, CandidateTagUsage::getHolders));
        return tags.findByWorkspaceIdOrderByLabelAsc(workspaceId).stream()
                .map(tag -> toDto(tag, holders.getOrDefault(tag.getId(), 0L)))
                .toList();
    }

    @Transactional
    public CandidateTagResponse create(UUID userId, UUID workspaceId, CreateCandidateTagRequest request,
                                       HttpServletRequest httpRequest) {
        String label = request.label().strip();
        tags.findByLabel(workspaceId, label).filter(CandidateTag::isRetired).ifPresent(retired -> {
            throw ApiException.of(ErrorCode.CANDIDATE_TAG_RETIRED);
        });
        refuseTaken(workspaceId, label, null);
        CandidateTagColour colour = ApiValueEnum.parse(CandidateTagColour.class, request.colour(),
                CandidateTagColour.NEUTRAL, "tag colour");
        CandidateTag tag = tags.save(CandidateTag.created(workspaceId, label, colour, userId));
        audit.event(ProjectEventType.CANDIDATE_TAG_CREATED).actor(userId).workspace(workspaceId)
                .target(TAG_TARGET, tag.getId()).from(httpRequest)
                .record();
        return toDto(tag, 0);
    }

    @Transactional
    public CandidateTagResponse update(UUID userId, UUID workspaceId, UUID tagId, UpdateCandidateTagRequest request,
                                       HttpServletRequest httpRequest) {
        CandidateTag tag = tags.requireInWorkspace(tagId, workspaceId);
        if (request.label() != null) {
            String label = request.label().strip();
            if (label.isEmpty()) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "label", "A tag needs a name");
            }
            refuseTaken(workspaceId, label, tagId);
            tag.rename(label);
        }
        if (request.colour() != null) {
            tag.recolour(ApiValueEnum.require(CandidateTagColour.class, request.colour(), "tag colour"));
        }
        if (Boolean.TRUE.equals(request.retired())) {
            tag.retire();
        } else if (Boolean.FALSE.equals(request.retired())) {
            tag.restore();
        }
        audit.event(ProjectEventType.CANDIDATE_TAG_UPDATED).actor(userId).workspace(workspaceId)
                .target(TAG_TARGET, tagId).from(httpRequest)
                .detailIfPresent("renamed", request.label() == null ? null : "true")
                .detailIfPresent("colour", request.colour())
                .detailIfPresent("retired", request.retired())
                .record();
        long holders = tags.usageOf(workspaceId).stream()
                .filter(usage -> usage.getTagId().equals(tagId))
                .mapToLong(CandidateTagUsage::getHolders).sum();
        return toDto(tag, holders);
    }

    /** The tags to put on someone: every one the workspace's, and none of them retired. */
    @Transactional(readOnly = true)
    public List<CandidateTag> requireOfferable(UUID workspaceId, Collection<UUID> tagIds) {
        List<CandidateTag> found = requireOwned(workspaceId, tagIds);
        if (found.stream().anyMatch(CandidateTag::isRetired)) {
            throw ApiException.of(ErrorCode.CANDIDATE_TAG_RETIRED);
        }
        return found;
    }

    /** The tags to take off someone: every one the workspace's; a retired tag may still be removed. */
    @Transactional(readOnly = true)
    public List<CandidateTag> requireOwned(UUID workspaceId, Collection<UUID> tagIds) {
        List<UUID> distinct = tagIds.stream().distinct().toList();
        List<CandidateTag> found = tags.findByWorkspaceIdAndIdIn(workspaceId, distinct);
        if (found.size() != distinct.size()) {
            throw ApiException.of(ErrorCode.NOT_FOUND);
        }
        return found;
    }

    private void refuseTaken(UUID workspaceId, String label, UUID renamedId) {
        tags.findByLabel(workspaceId, label)
                .filter(existing -> !existing.getId().equals(renamedId))
                .ifPresent(existing -> {
                    throw ApiException.of(ErrorCode.CANDIDATE_TAG_EXISTS);
                });
    }

    private static CandidateTagResponse toDto(CandidateTag tag, long holders) {
        return new CandidateTagResponse(tag.getId(), tag.getLabel(), tag.getColour().value(), tag.isRetired(),
                holders);
    }

    private record StarterTag(String label, CandidateTagColour colour) {}
}
