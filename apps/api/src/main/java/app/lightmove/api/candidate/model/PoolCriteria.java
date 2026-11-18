package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.PoolContactFilter;
import app.lightmove.api.candidate.constant.PoolExperienceBand;
import app.lightmove.api.candidate.constant.PoolPresence;
import app.lightmove.api.candidate.constant.PoolRecencyBand;
import app.lightmove.api.candidate.constant.PoolSortDirection;
import app.lightmove.api.candidate.constant.PoolSortField;
import app.lightmove.api.candidate.constant.PoolView;
import app.lightmove.api.candidate.constant.TagMatch;
import app.lightmove.api.core.constant.ApiValueEnum;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.Builder;
import org.springframework.util.MultiValueMap;

/**
 * What the Candidates page asks of the pool. Every part is optional and they combine with AND; the values
 * within one part combine with OR. {@code statuses} and {@code pipelineStages} are one part — the grid's
 * Status column — so a person matches at any of the statuses or in any of the stages, on one mapping on one
 * of {@code projectIds} when any is named, any mapping otherwise. {@code unowned} adds people nobody owns to
 * {@code ownerUserIds}; {@code seniorityUnrecorded} adds people with no seniority to {@code seniorities}.
 */
@Builder(toBuilder = true)
public record PoolCriteria(
        String query,
        PoolView view,
        String name,
        String title,
        String company,
        List<UUID> tagIds,
        TagMatch tagMatch,
        List<UUID> projectIds,
        List<String> statuses,
        List<String> pipelineStages,
        List<UUID> ownerUserIds,
        boolean unowned,
        List<String> countries,
        Set<PoolPresence> linkedin,
        Set<PoolContactFilter> contacts,
        Set<PoolExperienceBand> experience,
        Set<PoolRecencyBand> activity,
        Set<Seniority> seniorities,
        boolean seniorityUnrecorded,
        Set<CandidateSource> sources,
        Set<PoolRecencyBand> added,
        Set<PoolPresence> doNotContact,
        PoolSortField sort,
        boolean ascending
) {

    private static final Pattern PIPELINE_KEY = Pattern.compile("^[a-z][A-Za-z0-9]{0,39}$");
    private static final String NOBODY = "nobody";
    private static final String NO_SENIORITY = "none";

    public PoolCriteria {
        view = view == null ? PoolView.ALL : view;
        query = blankToNull(query);
        name = blankToNull(name);
        title = blankToNull(title);
        company = blankToNull(company);
        // Distinct, because "all of" counts the tags matched against how many were asked for.
        tagIds = distinct(tagIds);
        tagMatch = tagMatch == null ? TagMatch.ANY : tagMatch;
        projectIds = distinct(projectIds);
        statuses = pipelineKeys(statuses, "status");
        pipelineStages = pipelineKeys(pipelineStages, "pipeline stage");
        ownerUserIds = distinct(ownerUserIds);
        countries = countries == null ? List.of() : countries.stream()
                .filter(country -> country != null && !country.isBlank()).map(String::strip).distinct().toList();
        linkedin = setOf(linkedin);
        contacts = setOf(contacts);
        experience = setOf(experience);
        activity = setOf(activity);
        seniorities = setOf(seniorities);
        sources = setOf(sources);
        added = setOf(added);
        doNotContact = setOf(doNotContact);
        sort = sort == null ? PoolSortField.ACTIVITY : sort;
    }

    /**
     * The Candidates page's query string, shared by the list and the export. Every list parameter repeats;
     * {@code owner} is a user id or {@code nobody}, {@code seniority} a tier or {@code none}. Read from the raw
     * parameters, never bound: Spring's binder splits a lone value on commas, which would cut a country such as
     * "Korea, Republic of" in two. An unknown token is a 400, never a filter quietly dropped.
     */
    public static PoolCriteria read(MultiValueMap<String, String> params) {
        List<String> owners = tokens(params.get("owner"));
        List<String> tiers = tokens(params.get("seniority"));
        Set<PoolRecencyBand> added = enums(PoolRecencyBand.class, params.get("added"), "added window");
        if (added.contains(PoolRecencyBand.NEVER)) {
            // Everyone was added at some moment, so "never" would only ever answer an empty page.
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "That is not a valid added window",
                    "Unknown added window: never");
        }
        PoolSortField sortField = ApiValueEnum.parse(PoolSortField.class, params.getFirst("sort"),
                PoolSortField.ACTIVITY, "sort");
        PoolSortDirection sortDirection = ApiValueEnum.parse(PoolSortDirection.class, params.getFirst("direction"),
                sortField == PoolSortField.ACTIVITY ? PoolSortDirection.DESC : PoolSortDirection.ASC, "direction");
        return PoolCriteria.builder()
                .query(params.getFirst("q"))
                .view(ApiValueEnum.parse(PoolView.class, params.getFirst("view"), PoolView.ALL, "view"))
                .name(params.getFirst("name"))
                .title(params.getFirst("title"))
                .company(params.getFirst("company"))
                .tagIds(ids(params.get("tag"), "tag", "Pick a tag from the list"))
                .tagMatch(ApiValueEnum.parse(TagMatch.class, params.getFirst("tagMatch"), TagMatch.ANY, "tag match"))
                .projectIds(ids(params.get("position"), "position", "Pick a position from the list"))
                .statuses(params.get("status"))
                .pipelineStages(params.get("pipelineStage"))
                .unowned(owners.stream().anyMatch(NOBODY::equalsIgnoreCase))
                .ownerUserIds(ids(owners.stream().filter(token -> !NOBODY.equalsIgnoreCase(token)).toList(),
                        "owner", "Pick an owner from the list"))
                .countries(params.get("country"))
                .linkedin(enums(PoolPresence.class, params.get("linkedin"), "LinkedIn filter"))
                .contacts(enums(PoolContactFilter.class, params.get("contact"), "contact filter"))
                .experience(enums(PoolExperienceBand.class, params.get("experience"), "experience band"))
                .activity(enums(PoolRecencyBand.class, params.get("activity"), "activity window"))
                .seniorityUnrecorded(tiers.stream().anyMatch(NO_SENIORITY::equalsIgnoreCase))
                .seniorities(enums(Seniority.class,
                        tiers.stream().filter(token -> !NO_SENIORITY.equalsIgnoreCase(token)).toList(), "seniority"))
                .sources(enums(CandidateSource.class, params.get("source"), "source"))
                .added(added)
                .doNotContact(enums(PoolPresence.class, params.get("doNotContact"), "do-not-contact filter"))
                .sort(sortField)
                .ascending(sortDirection == PoolSortDirection.ASC)
                .build();
    }

    /** The whole pool, newest activity first — what an export of a selection is ordered by. */
    public static PoolCriteria everyone() {
        return PoolCriteria.builder().build();
    }

    /** Whether any filter on a person's mappings is asked for, which then all read the same mapping. */
    public boolean filtersMappings() {
        return !projectIds.isEmpty() || !statuses.isEmpty() || !pipelineStages.isEmpty();
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

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static <T> List<T> distinct(List<T> values) {
        return values == null ? List.of() : List.copyOf(new LinkedHashSet<>(values));
    }

    private static <T> Set<T> setOf(Set<T> values) {
        return values == null ? Set.of() : Set.copyOf(values);
    }

    /** Keys of the workspace's catalog, where a key the workspace does not hold simply matches nobody. */
    private static List<String> pipelineKeys(List<String> keys, String field) {
        if (keys == null) {
            return List.of();
        }
        return keys.stream().filter(key -> key != null && !key.isBlank()).map(String::strip).map(key -> {
            if (!PIPELINE_KEY.matcher(key).matches()) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown " + field + ": " + key);
            }
            return key;
        }).distinct().toList();
    }
}
