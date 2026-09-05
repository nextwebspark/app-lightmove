package app.lightmove.api.customcolumn.service;

import app.lightmove.api.core.audit.constant.ProjectEventType;
import app.lightmove.api.core.audit.service.AuditService;
import app.lightmove.api.core.config.CustomColumnSettings;
import app.lightmove.api.core.config.LightMoveProperties;
import app.lightmove.api.core.error.constant.ErrorCode;
import app.lightmove.api.core.error.model.ApiException;
import app.lightmove.api.customcolumn.constant.CustomColumnTarget;
import app.lightmove.api.customcolumn.constant.CustomColumnType;
import app.lightmove.api.customcolumn.dto.CustomColumnDto;
import app.lightmove.api.customcolumn.dto.CustomColumnsResponse;
import app.lightmove.api.customcolumn.dto.DefineCustomColumnRequest;
import app.lightmove.api.customcolumn.dto.ReorderCustomColumnsRequest;
import app.lightmove.api.customcolumn.dto.UpdateCustomColumnRequest;
import app.lightmove.api.customcolumn.model.CustomFieldValues;
import app.lightmove.api.customcolumn.model.ProjectCustomColumn;
import app.lightmove.api.customcolumn.repository.ProjectCustomColumnRepository;
import app.lightmove.api.project.repository.ProjectRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A mandate's extra grid columns: defining them, editing them, ordering them, and — the method every
 * other feature actually calls — deciding what a row is allowed to store in them.
 *
 * <p>{@link #applyTo} is the gate. The values live in an open jsonb bag, so without it any caller
 * could write any key into a row and the "columns" would be whatever happened to be in the map. It
 * drops keys the project has not defined, checks each value against its column's declared type, and
 * merges over what the row already held — which is also what makes a partial edit possible on a
 * column set the caller may only know half of.
 *
 * <p>Deleting a column removes the definition and <b>not</b> the values under it. The rows keep them,
 * unrendered, so a column deleted by mistake comes back with its data when it is defined again under
 * the same name. A sweep that erased them would make one misclick unrecoverable, for no benefit
 * beyond a slightly smaller document.
 */
@Service
public class CustomColumnService {

    private final ProjectCustomColumnRepository columns;
    private final ProjectRepository projects;
    private final AuditService audit;
    private final CustomColumnSettings settings;

    // Hand-written rather than @RequiredArgsConstructor: it derives the settings branch from the
    // properties root rather than taking it, which is the one case the Lombok rule exempts.
    public CustomColumnService(ProjectCustomColumnRepository columns, ProjectRepository projects,
                               AuditService audit, LightMoveProperties properties) {
        this.columns = columns;
        this.projects = projects;
        this.audit = audit;
        this.settings = properties.customColumn();
    }

    @Transactional(readOnly = true)
    public CustomColumnsResponse list(UUID workspaceId, UUID projectId) {
        requireProject(projectId, workspaceId);
        return new CustomColumnsResponse(
                columns.findByProjectIdOrderByTargetAscDisplayOrderAscLabelAsc(projectId).stream()
                        .map(CustomColumnService::toDto)
                        .toList());
    }

    /** The definitions one grid needs, for a caller that already knows the project is in scope. */
    @Transactional(readOnly = true)
    public List<CustomColumnDto> columnsOf(UUID projectId, CustomColumnTarget target) {
        return columns.findByProjectIdAndTargetOrderByDisplayOrderAscLabelAsc(projectId, target).stream()
                .map(CustomColumnService::toDto)
                .toList();
    }

    @Transactional
    public CustomColumnDto define(UUID userId, UUID workspaceId, UUID projectId,
                                  DefineCustomColumnRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        CustomColumnTarget target = requireTarget(request.target());
        CustomColumnType dataType = resolveType(request.dataType());

        ProjectCustomColumn defined = defineWithin(projectId, userId, target, request.label(), dataType);

        audit.event(ProjectEventType.CUSTOM_COLUMN_DEFINED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("customColumnId", defined.getId().toString())
                .detail("target", target.name())
                .detail("fieldKey", defined.getFieldKey())
                .record();
        return toDto(defined);
    }

    /**
     * Defines a column if the project has not got one by that name, and answers the existing one if it
     * has. The import's entry point, and the reason importing the same file twice does not mint a
     * second Ethnicity column: matching is on the <i>label</i>, case-insensitively, because that is
     * what a user reading two identical headers would call the same column.
     *
     * <p>{@code @Transactional} in its own right, unlike the rest of this class's writes, because the
     * import that calls it deliberately runs without an outer transaction — see
     * {@code ProjectImportService}. A column has to be committed before the row loop can write values
     * under it.
     */
    @Transactional
    public CustomColumnDto defineIfAbsent(UUID projectId, UUID userId, CustomColumnTarget target,
                                          String label, CustomColumnType dataType) {
        return existingByLabel(projectId, target, label)
                .map(CustomColumnService::toDto)
                .orElseGet(() -> toDto(defineWithin(projectId, userId, target, label, dataType)));
    }

    @Transactional
    public CustomColumnDto update(UUID userId, UUID workspaceId, UUID projectId, UUID columnId,
                                  UpdateCustomColumnRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        ProjectCustomColumn column = require(projectId, columnId);

        if (request.label() != null) {
            String label = requireLabel(request.label());
            // Only when it actually changes: re-saving a column under its own name is an ordinary
            // no-op edit, and refusing it would make the drawer's save button a trap.
            if (!label.equalsIgnoreCase(column.getLabel())
                    && columns.existsByProjectIdAndTargetAndLabelIgnoreCase(projectId, column.getTarget(), label)) {
                throw ApiException.of(ErrorCode.CUSTOM_COLUMN_NAME_TAKEN);
            }
            column.rename(label);
        }
        if (request.dataType() != null) {
            column.retype(requireType(request.dataType()));
        }
        if (request.hidden() != null) {
            if (request.hidden()) {
                column.hide();
            } else {
                column.show();
            }
        }

        audit.event(ProjectEventType.CUSTOM_COLUMN_UPDATED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("customColumnId", columnId.toString())
                .record();
        return toDto(column);
    }

    /**
     * Applies a whole new order. Every id must belong to this project and to one grid: reordering the
     * company columns cannot be allowed to renumber the candidate ones behind them, and an id from
     * another mandate is a scope error rather than something to skip past.
     *
     * <p>The list must also be that grid's <b>whole</b> set, each column once. Only the ids sent are
     * renumbered, so a short list — a stale tab that read the columns before a third was added, or one
     * carrying a duplicate — leaves the columns it omitted on their old positions, now colliding with
     * the ones it did move. Nothing in the schema catches that: {@code display_order} is not unique,
     * because two columns sharing a position is a display quirk rather than corrupt data. Refused here
     * instead, since a caller sending a partial order is asking for something it cannot mean.
     */
    @Transactional
    public CustomColumnsResponse reorder(UUID userId, UUID workspaceId, UUID projectId,
                                         ReorderCustomColumnsRequest request, HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);

        List<ProjectCustomColumn> ordered = new ArrayList<>();
        for (String rawId : request.columnIds()) {
            ordered.add(require(projectId, requireUuid(rawId)));
        }
        if (ordered.stream().map(ProjectCustomColumn::getTarget).distinct().count() > 1) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED,
                    "reorder mixed COMPANY and CANDIDATE columns in one request");
        }
        if (!ordered.isEmpty()) {
            CustomColumnTarget target = ordered.getFirst().getTarget();
            long distinctIds = ordered.stream().map(ProjectCustomColumn::getId).distinct().count();
            long held = columns.countByProjectIdAndTarget(projectId, target);
            if (distinctIds != ordered.size() || distinctIds != held) {
                throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "reorder must list each of this grid's " + held + " columns exactly once");
            }
        }
        for (int position = 0; position < ordered.size(); position++) {
            ordered.get(position).moveTo(position);
        }

        audit.event(ProjectEventType.CUSTOM_COLUMN_REORDERED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("count", String.valueOf(ordered.size()))
                .record();
        return list(workspaceId, projectId);
    }

    @Transactional
    public void remove(UUID userId, UUID workspaceId, UUID projectId, UUID columnId,
                       HttpServletRequest httpRequest) {
        requireProject(projectId, workspaceId);
        ProjectCustomColumn column = require(projectId, columnId);
        String fieldKey = column.getFieldKey();

        // The definition only. See the class doc: the rows keep their values, so defining the column
        // again under the same name brings the data back rather than starting from blank cells.
        columns.delete(column);

        audit.event(ProjectEventType.CUSTOM_COLUMN_REMOVED)
                .actor(userId).workspace(workspaceId).target("project", projectId).from(httpRequest)
                .detail("customColumnId", columnId.toString())
                .detail("fieldKey", fieldKey)
                .record();
    }

    /**
     * What a row may store, given what it already stores and what a caller has just sent.
     *
     * <p>Three rules, and each of them is load-bearing:
     *
     * <ul>
     *   <li><b>A null map changes nothing.</b> A client that does not know about custom columns — an
     *       older SPA, the extension, a script — must be able to save a row without wiping the
     *       columns it never rendered.
     *   <li><b>Only defined keys survive.</b> The bag is open, so this is the only thing standing
     *       between it and arbitrary caller-chosen keys.
     *   <li><b>A blank value clears that column, an absent key leaves it alone.</b> The same
     *       distinction {@code UpdateTriageCompanyRequest} draws between an empty note and a null one,
     *       for the same reason: emptying a cell has to be expressible.
     * </ul>
     */
    public CustomFieldValues applyTo(UUID projectId, CustomColumnTarget target,
                                     CustomFieldValues existing, Map<String, String> incoming) {
        CustomFieldValues current = existing == null ? CustomFieldValues.empty() : existing;
        if (incoming == null) {
            return current;
        }
        Map<String, CustomColumnType> defined =
                columns.findByProjectIdAndTargetOrderByDisplayOrderAscLabelAsc(projectId, target).stream()
                        .collect(Collectors.toMap(ProjectCustomColumn::getFieldKey,
                                ProjectCustomColumn::getDataType));

        Map<String, String> merged = new LinkedHashMap<>(current.asMap());
        incoming.forEach((fieldKey, value) -> {
            CustomColumnType type = defined.get(fieldKey);
            if (type == null) {
                return;
            }
            if (value == null || value.isBlank()) {
                merged.remove(fieldKey);
                return;
            }
            merged.put(fieldKey, coerce(type, value.trim(), fieldKey));
        });
        return CustomFieldValues.of(merged);
    }

    /**
     * Checks a value against its column's type and answers the form to store.
     *
     * <p>Only the canonical forms are rewritten — a boolean to {@code true}/{@code false}, a number
     * with its grouping commas taken out — because those are the two a spreadsheet reliably mangles
     * and the two a cell renderer has to be able to trust. Everything else is stored verbatim: this is
     * a validator, not a formatter, and a date the user typed the way their region writes dates is not
     * ours to rewrite.
     */
    private static String coerce(CustomColumnType type, String value, String fieldKey) {
        return switch (type) {
            case TEXT, DATE -> value;
            case NUMBER -> {
                String withoutGrouping = value.replace(",", "").replace(" ", "");
                try {
                    Double.parseDouble(withoutGrouping);
                } catch (NumberFormatException e) {
                    throw new ApiException(ErrorCode.VALIDATION_FAILED,
                            "custom column " + fieldKey + " is a number and got " + value);
                }
                yield withoutGrouping;
            }
            case BOOLEAN -> switch (value.toLowerCase(Locale.ROOT)) {
                case "true", "yes", "y", "1" -> "true";
                case "false", "no", "n", "0" -> "false";
                default -> throw new ApiException(ErrorCode.VALIDATION_FAILED,
                        "custom column " + fieldKey + " is a yes/no and got " + value);
            };
        };
    }

    private ProjectCustomColumn defineWithin(UUID projectId, UUID userId, CustomColumnTarget target,
                                             String rawLabel, CustomColumnType dataType) {
        String label = requireLabel(rawLabel);
        if (columns.existsByProjectIdAndTargetAndLabelIgnoreCase(projectId, target, label)) {
            throw ApiException.of(ErrorCode.CUSTOM_COLUMN_NAME_TAKEN);
        }
        if (columns.countByProjectId(projectId) >= settings.maxPerProject()) {
            // The ceiling is configured, not request input, so naming it is what turns a refusal into
            // something the caller can act on — remove a column, or import fewer.
            throw ApiException.userFacing(ErrorCode.CUSTOM_COLUMN_LIMIT_REACHED,
                    "This mandate already has its " + settings.maxPerProject() + " custom columns.");
        }

        List<ProjectCustomColumn> siblings =
                columns.findByProjectIdAndTargetOrderByDisplayOrderAscLabelAsc(projectId, target);
        Set<String> takenKeys = siblings.stream()
                .map(ProjectCustomColumn::getFieldKey)
                .collect(Collectors.toCollection(HashSet::new));
        String fieldKey = CustomColumnKeys.uniqueWithin(CustomColumnKeys.slug(label), takenKeys);
        int displayOrder = siblings.stream()
                .mapToInt(ProjectCustomColumn::getDisplayOrder)
                .max()
                .orElse(-1) + 1;

        return columns.save(ProjectCustomColumn.defined(
                projectId, userId, target, fieldKey, label, dataType, displayOrder));
    }

    private Optional<ProjectCustomColumn> existingByLabel(UUID projectId, CustomColumnTarget target,
                                                          String label) {
        String wanted = label == null ? "" : label.trim();
        return columns.findByProjectIdAndTargetOrderByDisplayOrderAscLabelAsc(projectId, target).stream()
                .filter(column -> column.getLabel().equalsIgnoreCase(wanted))
                .findFirst();
    }

    private ProjectCustomColumn require(UUID projectId, UUID columnId) {
        return columns.findByIdAndProjectId(columnId, projectId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private void requireProject(UUID projectId, UUID workspaceId) {
        projects.findByIdAndWorkspaceId(projectId, workspaceId)
                .orElseThrow(() -> ApiException.of(ErrorCode.NOT_FOUND));
    }

    private static String requireLabel(String rawLabel) {
        String label = rawLabel == null ? "" : rawLabel.trim();
        if (label.isEmpty()) {
            throw ApiException.userFacing(ErrorCode.VALIDATION_FAILED, "Give the column a name");
        }
        return label;
    }

    private static CustomColumnTarget requireTarget(String value) {
        CustomColumnTarget target = CustomColumnTarget.fromValue(value);
        if (target == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "unknown custom column target " + value);
        }
        return target;
    }

    /** An absent type is text, which is what an unmapped spreadsheet column is until told otherwise. */
    private static CustomColumnType resolveType(String value) {
        return value == null || value.isBlank() ? CustomColumnType.TEXT : requireType(value);
    }

    private static CustomColumnType requireType(String value) {
        CustomColumnType type = CustomColumnType.fromValue(value);
        if (type == null) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "unknown custom column type " + value);
        }
        return type;
    }

    private static UUID requireUuid(String value) {
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new ApiException(ErrorCode.VALIDATION_FAILED, "not a custom column id: " + value);
        }
    }

    private static CustomColumnDto toDto(ProjectCustomColumn column) {
        return new CustomColumnDto(
                column.getId().toString(),
                column.getTarget().value(),
                column.getFieldKey(),
                column.getLabel(),
                column.getDataType().value(),
                column.getDisplayOrder(),
                column.isHidden());
    }
}
