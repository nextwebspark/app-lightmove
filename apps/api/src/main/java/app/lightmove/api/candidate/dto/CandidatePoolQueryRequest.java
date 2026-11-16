package app.lightmove.api.candidate.dto;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.PoolContactFilter;
import app.lightmove.api.candidate.constant.PoolExperienceBand;
import app.lightmove.api.candidate.constant.PoolPresence;
import app.lightmove.api.candidate.constant.PoolRecencyBand;
import app.lightmove.api.candidate.constant.PoolSortDirection;
import app.lightmove.api.candidate.constant.PoolSortField;
import app.lightmove.api.candidate.constant.PoolView;
import app.lightmove.api.candidate.constant.TagMatch;
import app.lightmove.api.candidate.model.PoolCriteria;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.util.MultiValueMap;

/**
 * The Candidates page's query string, shared by the list and the export. Every list parameter repeats;
 * {@code owner} is a user id or {@code nobody}, {@code seniority} a tier or {@code none}. Read from the raw
 * parameters, never bound: Spring's binder splits a lone value on commas, which would cut a country such as
 * "Korea, Republic of" in two. An unknown token is a 400, never a filter quietly dropped.
 */
public record CandidatePoolQueryRequest(
        String q,
        String view,
        String name,
        String title,
        String company,
        List<String> tag,
        String tagMatch,
        List<String> position,
        List<String> status,
        List<String> pipelineStage,
        List<String> owner,
        List<String> country,
        List<String> linkedin,
        List<String> contact,
        List<String> experience,
        List<String> activity,
        List<String> seniority,
        List<String> source,
        List<String> added,
        List<String> doNotContact,
        String sort,
        String direction
) {

    private static final String NOBODY = "nobody";
    private static final String NO_SENIORITY = "none";

    public static CandidatePoolQueryRequest from(MultiValueMap<String, String> params) {
        return new CandidatePoolQueryRequest(params.getFirst("q"), params.getFirst("view"), params.getFirst("name"),
                params.getFirst("title"), params.getFirst("company"), params.get("tag"), params.getFirst("tagMatch"),
                params.get("position"), params.get("status"), params.get("pipelineStage"), params.get("owner"),
                params.get("country"), params.get("linkedin"), params.get("contact"), params.get("experience"),
                params.get("activity"), params.get("seniority"), params.get("source"), params.get("added"),
                params.get("doNotContact"), params.getFirst("sort"), params.getFirst("direction"));
    }

    public PoolCriteria toCriteria() {
        List<String> owners = tokens(owner);
        List<String> tiers = tokens(seniority);
        PoolSortField sortField = ApiValueEnum.parse(PoolSortField.class, sort, PoolSortField.ACTIVITY, "sort");
        PoolSortDirection sortDirection = ApiValueEnum.parse(PoolSortDirection.class, direction,
                sortField == PoolSortField.ACTIVITY ? PoolSortDirection.DESC : PoolSortDirection.ASC, "direction");
        return PoolCriteria.builder()
                .query(q)
                .view(ApiValueEnum.parse(PoolView.class, view, PoolView.ALL, "view"))
                .name(name)
                .title(title)
                .company(company)
                .tagIds(ids(tag, "tag", "Pick a tag from the list"))
                .tagMatch(ApiValueEnum.parse(TagMatch.class, tagMatch, TagMatch.ANY, "tag match"))
                .projectIds(ids(position, "position", "Pick a position from the list"))
                .statuses(status)
                .pipelineStages(pipelineStage)
                .unowned(owners.stream().anyMatch(NOBODY::equalsIgnoreCase))
                .ownerUserIds(ids(owners.stream().filter(token -> !NOBODY.equalsIgnoreCase(token)).toList(),
                        "owner", "Pick an owner from the list"))
                .countries(country)
                .linkedin(enums(PoolPresence.class, linkedin, "LinkedIn filter"))
                .contacts(enums(PoolContactFilter.class, contact, "contact filter"))
                .experience(enums(PoolExperienceBand.class, experience, "experience band"))
                .activity(enums(PoolRecencyBand.class, activity, "activity window"))
                .seniorityUnrecorded(tiers.stream().anyMatch(NO_SENIORITY::equalsIgnoreCase))
                .seniorities(enums(Seniority.class,
                        tiers.stream().filter(token -> !NO_SENIORITY.equalsIgnoreCase(token)).toList(), "seniority"))
                .sources(enums(CandidateSource.class, source, "source"))
                .added(enums(PoolRecencyBand.class, added, "added window"))
                .doNotContact(enums(PoolPresence.class, doNotContact, "do-not-contact filter"))
                .sort(sortField)
                .ascending(sortDirection == PoolSortDirection.ASC)
                .build();
    }

    private static List<String> tokens(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(Objects::nonNull).map(String::strip).filter(token -> !token.isEmpty()).toList();
    }

    private static List<UUID> ids(List<String> values, String field, String message) {
        return tokens(values).stream().map(token -> {
            try {
                return UUID.fromString(token);
            } catch (IllegalArgumentException notAnId) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, field, message);
            }
        }).toList();
    }

    private static <E extends Enum<E> & ApiValueEnum> Set<E> enums(Class<E> type, List<String> values, String label) {
        return tokens(values).stream().map(token -> ApiValueEnum.require(type, token, label))
                .collect(Collectors.toUnmodifiableSet());
    }
}
