package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.PoolContactFilter;
import app.lightmove.api.candidate.constant.PoolExperienceBand;
import app.lightmove.api.candidate.constant.PoolPresence;
import app.lightmove.api.candidate.constant.PoolRecencyBand;
import app.lightmove.api.candidate.constant.PoolSortField;
import app.lightmove.api.candidate.constant.PoolView;
import app.lightmove.api.candidate.constant.TagMatch;
import app.lightmove.api.common.constant.Seniority;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.Builder;

/**
 * What the Candidates page asks of the pool. Every part is optional and they combine with AND; the values
 * within one part combine with OR. {@code statuses} and {@code pipelineStages} read a mapping on one of
 * {@code projectIds} when any is named, any mapping otherwise. {@code unowned} adds people nobody owns to
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

    /** The whole pool, newest activity first — what an export of a selection is ordered by. */
    public static PoolCriteria everyone() {
        return PoolCriteria.builder().build();
    }

    /** Whether any filter on a person's mappings is asked for, which then all read the same mapping. */
    public boolean filtersMappings() {
        return !projectIds.isEmpty() || !statuses.isEmpty() || !pipelineStages.isEmpty();
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
