package app.lightmove.api.candidate.model;

import app.lightmove.api.candidate.constant.PoolSortDirection;
import app.lightmove.api.candidate.constant.PoolSortField;
import app.lightmove.api.candidate.constant.PoolView;
import app.lightmove.api.candidate.constant.TagMatch;
import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * What the Candidates page asks of the pool. Every part is optional and they combine with AND.
 * {@code status} (a status key) and {@code pipelineStage} (a stage key) read the mapping on {@code projectId}
 * when one is named, any mapping otherwise;
 * {@code unowned} asks for people nobody owns and wins over {@code ownerUserId}.
 */
public record PoolCriteria(
        String query,
        PoolView view,
        List<UUID> tagIds,
        TagMatch tagMatch,
        UUID projectId,
        String status,
        String pipelineStage,
        UUID ownerUserId,
        boolean unowned,
        String country,
        PoolSortField sort,
        boolean ascending
) {

    private static final String NOBODY = "nobody";

    private static final Pattern PIPELINE_KEY = Pattern.compile("^[a-z][A-Za-z0-9]{0,39}$");

    public PoolCriteria {
        view = view == null ? PoolView.ALL : view;
        // Distinct, because "all of" counts the tags matched against how many were asked for.
        tagIds = tagIds == null ? List.of() : List.copyOf(new LinkedHashSet<>(tagIds));
        tagMatch = tagMatch == null ? TagMatch.ANY : tagMatch;
        sort = sort == null ? PoolSortField.ACTIVITY : sort;
        query = query == null || query.isBlank() ? null : query.strip();
        country = country == null || country.isBlank() ? null : country.strip();
        status = pipelineKeyOrNull(status, "status");
        pipelineStage = pipelineKeyOrNull(pipelineStage, "pipeline stage");
    }

    /**
     * The criteria the page's query string spells: wire tokens for the enums, {@code "nobody"} or a user
     * id for the owner. An unknown token is a 400, never a filter quietly dropped.
     */
    public static PoolCriteria read(String query, String view, List<UUID> tagIds, String tagMatch, UUID projectId,
                                    String status, String pipelineStage, String owner, String country, String sort,
                                    String direction) {
        boolean unowned = NOBODY.equalsIgnoreCase(owner == null ? "" : owner.strip());
        UUID ownerUserId = null;
        if (!unowned && owner != null && !owner.isBlank()) {
            try {
                ownerUserId = UUID.fromString(owner.strip());
            } catch (IllegalArgumentException notAnId) {
                throw ApiException.withField(ErrorCode.VALIDATION_FAILED, "owner", "Pick an owner from the list");
            }
        }
        PoolSortField sortField = ApiValueEnum.parse(PoolSortField.class, sort, PoolSortField.ACTIVITY, "sort");
        PoolSortDirection sortDirection = ApiValueEnum.parse(PoolSortDirection.class, direction,
                sortField == PoolSortField.ACTIVITY ? PoolSortDirection.DESC : PoolSortDirection.ASC, "direction");
        boolean ascending = sortDirection == PoolSortDirection.ASC;
        return new PoolCriteria(query, ApiValueEnum.parse(PoolView.class, view, PoolView.ALL, "view"), tagIds,
                ApiValueEnum.parse(TagMatch.class, tagMatch, TagMatch.ANY, "tag match"), projectId,
                status, pipelineStage, ownerUserId, unowned, country,
                sortField, ascending);
    }

    /** The whole pool, newest activity first — what an export of a selection is ordered by. */
    public static PoolCriteria everyone() {
        return new PoolCriteria(null, PoolView.ALL, List.of(), TagMatch.ANY, null, null, null, null, false, null,
                PoolSortField.ACTIVITY, false);
    }

    /** A key of the workspace's catalog, which a key the workspace does not hold simply matches nobody. */
    private static String pipelineKeyOrNull(String key, String field) {
        if (key == null || key.isBlank()) {
            return null;
        }
        if (!PIPELINE_KEY.matcher(key.strip()).matches()) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "Unknown " + field + ": " + key);
        }
        return key.strip();
    }
}
