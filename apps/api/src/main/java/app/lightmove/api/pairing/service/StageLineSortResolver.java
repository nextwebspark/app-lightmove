package app.lightmove.api.pairing.service;

import app.lightmove.api.common.constant.ApiValueEnum;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.customcolumn.service.CustomColumnService;
import app.lightmove.api.pairing.constant.StageLineSortField;
import app.lightmove.api.pairing.model.StageLineSort;
import app.lightmove.api.strategy.constant.SortDirection;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** The grid's sort tokens read against the allowlist: a staff-only column for staff, a custom one only the mandate's. */
@Component
@RequiredArgsConstructor
public class StageLineSortResolver {

    private static final String CUSTOM_SORT_PREFIX = "custom:";

    private final CustomColumnService customColumns;

    /** Newest first when the request names nothing, as the grid opens. */
    public StageLineSort resolve(UUID workspaceId, UUID projectId, String token, String directionToken,
                                 boolean staff) {
        boolean descending = ApiValueEnum.parse(SortDirection.class, directionToken, SortDirection.DESC,
                "sort direction") == SortDirection.DESC;
        if (token != null && token.startsWith(CUSTOM_SORT_PREFIX)) {
            return new StageLineSort(null, requireCustomColumn(workspaceId, projectId, token), descending);
        }
        StageLineSortField field = ApiValueEnum.parse(StageLineSortField.class, token, StageLineSortField.ADDED,
                "sort field");
        if (field.staffOnly() && !staff) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "That is not a valid sort field",
                    "Sort field held to staff: " + token);
        }
        return new StageLineSort(field, null, descending);
    }

    private CustomColumnDto requireCustomColumn(UUID workspaceId, UUID projectId, String token) {
        String gridId = token.substring(CUSTOM_SORT_PREFIX.length());
        return customColumns.list(workspaceId, projectId).columns().stream()
                .filter(column -> !column.hidden() && gridId.equals(column.target() + ":" + column.fieldKey()))
                .findFirst()
                .orElseThrow(() -> ApiException.userFacing(ErrorCode.VALIDATION_FAILED,
                        "That is not a valid sort field", "Unknown custom sort column: " + token));
    }
}
