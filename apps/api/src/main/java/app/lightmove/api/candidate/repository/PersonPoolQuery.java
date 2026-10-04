package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.constant.PoolView;
import app.lightmove.api.candidate.model.PoolCriteria;
import app.lightmove.api.candidate.model.PoolViewCounts;
import app.lightmove.api.core.text.service.LikePatterns;
import app.lightmove.api.project.constant.ProjectStage;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The Candidates page's read of the pool: which of the workspace's people match, in what order, and how
 * many each quick view holds. Answers ids only; the rows are loaded through the entities.
 *
 * <p>The search is a plain scan over one workspace's people, as V33 chose for the universe: a pool is
 * hundreds to a few thousand rows, and a trigram index needs an extension the runtime role cannot
 * create. Every fragment below is fixed text; every value is bound.
 */
@Repository
@RequiredArgsConstructor
public class PersonPoolQuery {

    /** A position still being worked: one whose stage is not {@link ProjectStage#isDone}. */
    private static final String IN_ACTIVE_POSITION = """
            EXISTS (SELECT 1 FROM app_lm_project_candidate m JOIN app_lm_project pr ON pr.id = m.project_id
                    WHERE m.person_id = p.id AND pr.stage NOT IN (:doneStages))""";

    private static final List<String> DONE_STAGES = Arrays.stream(ProjectStage.values())
            .filter(ProjectStage::isDone).map(Enum::name).toList();

    private static final String IN_NO_POSITION =
            "NOT EXISTS (SELECT 1 FROM app_lm_project_candidate m WHERE m.person_id = p.id)";

    private static final String OWNED_BY_CALLER = "p.owner_user_id = :caller";

    private final NamedParameterJdbcTemplate jdbc;

    public List<UUID> page(UUID workspaceId, UUID callerId, PoolCriteria criteria, int offset, int limit) {
        Map<String, Object> params = paramsOf(workspaceId, callerId);
        String where = whereOf(criteria, params, true);
        params.put("limit", limit);
        params.put("offset", offset);
        return jdbc.queryForList("SELECT p.id FROM app_lm_person p WHERE " + where
                + " ORDER BY " + orderOf(criteria) + " LIMIT :limit OFFSET :offset", params, UUID.class);
    }

    /** Every match, unpaged, up to {@code cap} + 1 so the caller can tell a full answer from a cut one. */
    public List<UUID> all(UUID workspaceId, UUID callerId, PoolCriteria criteria, int cap) {
        return page(workspaceId, callerId, criteria, 0, cap + 1);
    }

    public long count(UUID workspaceId, UUID callerId, PoolCriteria criteria) {
        Map<String, Object> params = paramsOf(workspaceId, callerId);
        Long found = jdbc.queryForObject("SELECT count(*) FROM app_lm_person p WHERE "
                + whereOf(criteria, params, true), params, Long.class);
        return found == null ? 0 : found;
    }

    /** Each quick view's size under every other filter, so a chip says what pressing it would show. */
    public PoolViewCounts viewCounts(UUID workspaceId, UUID callerId, PoolCriteria criteria) {
        Map<String, Object> params = paramsOf(workspaceId, callerId);
        String where = whereOf(criteria, params, false);
        String sql = "SELECT count(*) AS everyone,"
                + " count(*) FILTER (WHERE " + OWNED_BY_CALLER + ") AS mine,"
                + " count(*) FILTER (WHERE " + IN_ACTIVE_POSITION + ") AS active,"
                + " count(*) FILTER (WHERE " + IN_NO_POSITION + ") AS unplaced,"
                + " (SELECT count(*) FROM app_lm_person q WHERE q.workspace_id = :workspaceId) AS pool"
                + " FROM app_lm_person p WHERE " + where;
        return jdbc.queryForObject(sql, params, (row, index) -> new PoolViewCounts(row.getLong("everyone"),
                row.getLong("mine"), row.getLong("active"), row.getLong("unplaced"), row.getLong("pool")));
    }

    /** The countries the pool's people are recorded in, for the filter's list. */
    public List<String> countries(UUID workspaceId) {
        return jdbc.queryForList("""
                SELECT DISTINCT location_country FROM app_lm_person
                WHERE workspace_id = :workspaceId AND location_country IS NOT NULL AND btrim(location_country) <> ''
                ORDER BY location_country
                """, Map.of("workspaceId", workspaceId), String.class);
    }

    private static Map<String, Object> paramsOf(UUID workspaceId, UUID callerId) {
        Map<String, Object> params = new HashMap<>();
        params.put("workspaceId", workspaceId);
        params.put("caller", callerId);
        params.put("doneStages", DONE_STAGES);
        return params;
    }

    private static String whereOf(PoolCriteria criteria, Map<String, Object> params, boolean withView) {
        List<String> clauses = new ArrayList<>();
        clauses.add("p.workspace_id = :workspaceId");
        if (criteria.query() != null) {
            params.put("like", "%" + LikePatterns.escape(criteria.query()) + "%");
            params.put("emailLike", "%" + LikePatterns.escape(criteria.query().toLowerCase(Locale.ROOT)) + "%");
            clauses.add("""
                    (p.full_name ILIKE :like OR p.title ILIKE :like
                     OR EXISTS (SELECT 1 FROM app_lm_project_candidate m
                                WHERE m.person_id = p.id AND m.company_name ILIKE :like)
                     OR EXISTS (SELECT 1 FROM app_lm_person_contact k
                                WHERE k.person_id = p.id AND k.channel = 'EMAIL' AND k.value_key LIKE :emailLike))""");
        }
        if (!criteria.tagIds().isEmpty()) {
            params.put("tagIds", criteria.tagIds());
            params.put("tagCount", criteria.tagIds().size());
            String holders = "SELECT count(*) FROM app_lm_person_tag t WHERE t.person_id = p.id AND t.tag_id IN (:tagIds)";
            clauses.add(switch (criteria.tagMatch()) {
                case ANY -> "(" + holders + ") > 0";
                case ALL -> "(" + holders + ") = :tagCount";
                case NONE -> "(" + holders + ") = 0";
            });
        }
        if (criteria.projectId() != null || criteria.status() != null) {
            StringBuilder mapping = new StringBuilder(
                    "EXISTS (SELECT 1 FROM app_lm_project_candidate m WHERE m.person_id = p.id");
            if (criteria.projectId() != null) {
                params.put("projectId", criteria.projectId());
                mapping.append(" AND m.project_id = :projectId");
            }
            if (criteria.status() != null) {
                params.put("status", criteria.status().name());
                mapping.append(" AND m.status = :status");
            }
            clauses.add(mapping.append(")").toString());
        }
        if (criteria.unowned()) {
            clauses.add("p.owner_user_id IS NULL");
        } else if (criteria.ownerUserId() != null) {
            params.put("owner", criteria.ownerUserId());
            clauses.add("p.owner_user_id = :owner");
        }
        if (criteria.country() != null) {
            params.put("country", criteria.country());
            clauses.add("lower(p.location_country) = lower(:country)");
        }
        if (withView && criteria.view() != PoolView.ALL) {
            clauses.add(switch (criteria.view()) {
                case MINE -> OWNED_BY_CALLER;
                case ACTIVE -> IN_ACTIVE_POSITION;
                case UNPLACED -> IN_NO_POSITION;
                case ALL -> "true";
            });
        }
        return String.join(" AND ", clauses);
    }

    private static String orderOf(PoolCriteria criteria) {
        String direction = criteria.ascending() ? "ASC" : "DESC";
        String key = switch (criteria.sort()) {
            case NAME -> "lower(p.full_name) " + direction;
            case LOCATION -> "lower(p.location_country) " + direction + " NULLS LAST, "
                    + "lower(p.location_city) " + direction + " NULLS LAST";
            case POSITIONS -> "(SELECT count(*) FROM app_lm_project_candidate m WHERE m.person_id = p.id) "
                    + direction;
            case ACTIVITY -> "(SELECT max(a.id) FROM app_lm_person_activity a WHERE a.person_id = p.id) "
                    + direction + " NULLS LAST";
        };
        return key + ", p.id";
    }
}
