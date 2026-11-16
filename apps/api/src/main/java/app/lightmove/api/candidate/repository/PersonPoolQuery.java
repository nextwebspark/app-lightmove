package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.constant.PoolExperienceBand;
import app.lightmove.api.candidate.constant.PoolRecencyBand;
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
 * The Candidates page's pool read: matching ids, order and quick-view counts. A plain scan per workspace (V33's
 * reasoning: no trigram extension for the runtime role); every fragment is fixed text, every value bound.
 */
@Repository
@RequiredArgsConstructor
public class PersonPoolQuery {

    /** A position still being worked: not archived, and its stage not {@link ProjectStage#isDone}. */
    private static final String IN_ACTIVE_POSITION = """
            EXISTS (SELECT 1 FROM app_lm_project_candidate m JOIN app_lm_project pr ON pr.id = m.project_id
                    WHERE m.person_id = p.id AND pr.stage NOT IN (:doneStages) AND pr.archived_at IS NULL)""";

    private static final List<String> DONE_STAGES = Arrays.stream(ProjectStage.values())
            .filter(ProjectStage::isDone).map(Enum::name).toList();

    /** On no position anyone can see: someone mapped only to archived ones counts as in none. */
    private static final String IN_NO_POSITION = """
            NOT EXISTS (SELECT 1 FROM app_lm_project_candidate m JOIN app_lm_project pr ON pr.id = m.project_id
                        WHERE m.person_id = p.id AND pr.archived_at IS NULL)""";

    private static final String OWNED_BY_CALLER = "p.owner_user_id = :caller";

    /** Art. 14's month: held longer than that and never told the firm holds their data. */
    private static final String NOT_NOTIFIED =
            "p.privacy_notified_at IS NULL AND p.created_at < now() - interval '30 days'";

    /** Asked for consent, the request still open (#1267). */
    private static final String CONSENT_ASKED = """
            EXISTS (SELECT 1 FROM app_lm_person_consent k
                    WHERE k.person_id = p.id AND k.status = 'REQUESTED' AND k.request_expires_at > now())""";

    private static final String CONSENT_ENDING = """
            EXISTS (SELECT 1 FROM app_lm_person_consent k
                    WHERE k.person_id = p.id AND k.status = 'GIVEN'
                      AND k.valid_until > now() AND k.valid_until <= now() + interval '30 days')""";

    private static final String CONSENT_REFUSED = """
            EXISTS (SELECT 1 FROM app_lm_person_consent k
                    WHERE k.person_id = p.id AND k.status IN ('DECLINED', 'WITHDRAWN'))""";

    /**
     * The employer the Company column draws ({@code PersonEmployerResolver.employerOf}): the company the newest
     * position naming one recorded, else the first career post naming one. Searching mappings alone missed
     * everyone whose company came from research — the row said "Aldar", the search for it found nobody.
     */
    /** Research's employer: the first career post naming a company. */
    private static final String CAREER_EMPLOYER = """
            (SELECT c.entry ->> 'company'
             FROM jsonb_array_elements(CASE WHEN jsonb_typeof(p.profile -> 'career') = 'array'
                                            THEN p.profile -> 'career' ELSE '[]'::jsonb END)
                  WITH ORDINALITY AS c(entry, ord)
             WHERE btrim(coalesce(c.entry ->> 'company', '')) <> ''
             ORDER BY c.ord LIMIT 1)""";

    /**
     * The employer the Company column draws ({@code PersonEmployerResolver.employerOf}): the company the newest
     * position naming one recorded, else {@link #CAREER_EMPLOYER}. Searching mappings alone missed everyone whose
     * company came from research — the row said "Mubadala", the search for it found nobody. The free-text search
     * needs only the career half, since any mapping's company already matches it.
     */
    private static final String EMPLOYER = """
            COALESCE((SELECT m.company_name FROM app_lm_project_candidate m
                      WHERE m.person_id = p.id AND btrim(coalesce(m.company_name, '')) <> ''
                      ORDER BY m.created_at DESC, m.id DESC LIMIT 1),
                     %s)""".formatted(CAREER_EMPLOYER);

    private static final String HOLDS_CONTACT =
            "EXISTS (SELECT 1 FROM app_lm_person_contact k WHERE k.person_id = p.id AND k.channel IN (%s))";

    private static final String LATEST_ACTIVITY_AT =
            "(SELECT max(a.occurred_at) FROM app_lm_person_activity a WHERE a.person_id = p.id)";

    /** Board first, as the seniority pickers list the tiers. */
    private static final String SENIORITY_RANK = """
            CASE p.seniority_level WHEN 'BOARD' THEN 0 WHEN 'C_SUITE' THEN 1 WHEN 'N_MINUS_1' THEN 2
                                   WHEN 'N_MINUS_2' THEN 3 WHEN 'N_MINUS_3' THEN 4 END""";

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
                + " count(*) FILTER (WHERE " + NOT_NOTIFIED + ") AS not_notified,"
                + " count(*) FILTER (WHERE " + CONSENT_ASKED + ") AS consent_asked,"
                + " count(*) FILTER (WHERE " + CONSENT_ENDING + ") AS consent_ending,"
                + " count(*) FILTER (WHERE " + CONSENT_REFUSED + ") AS consent_refused,"
                + " (SELECT count(*) FROM app_lm_person q WHERE q.workspace_id = :workspaceId) AS pool"
                + " FROM app_lm_person p WHERE " + where;
        return jdbc.queryForObject(sql, params, (row, index) -> new PoolViewCounts(row.getLong("everyone"),
                row.getLong("mine"), row.getLong("active"), row.getLong("unplaced"), row.getLong("not_notified"),
                row.getLong("consent_asked"), row.getLong("consent_ending"), row.getLong("consent_refused"),
                row.getLong("pool")));
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
            params.put("like", likeOf(criteria.query()));
            params.put("emailLike", likeOf(criteria.query().toLowerCase(Locale.ROOT)));
            clauses.add("""
                    (p.full_name ILIKE :like OR p.title ILIKE :like OR %s ILIKE :like
                     OR EXISTS (SELECT 1 FROM app_lm_project_candidate m
                                WHERE m.person_id = p.id AND m.company_name ILIKE :like)
                     OR EXISTS (SELECT 1 FROM app_lm_person_contact k
                                WHERE k.person_id = p.id AND k.channel = 'EMAIL' AND k.value_key LIKE :emailLike))"""
                    .formatted(CAREER_EMPLOYER));
        }
        if (criteria.name() != null) {
            params.put("nameLike", likeOf(criteria.name()));
            clauses.add("p.full_name ILIKE :nameLike");
        }
        if (criteria.title() != null) {
            params.put("titleLike", likeOf(criteria.title()));
            clauses.add("p.title ILIKE :titleLike");
        }
        if (criteria.company() != null) {
            params.put("companyLike", likeOf(criteria.company()));
            clauses.add(EMPLOYER + " ILIKE :companyLike");
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
        if (criteria.filtersMappings()) {
            clauses.add(mappingClause(criteria, params));
        }
        anyOf(clauses, ownerClauses(criteria, params));
        if (!criteria.countries().isEmpty()) {
            params.put("countries", criteria.countries().stream().map(c -> c.toLowerCase(Locale.ROOT)).toList());
            clauses.add("lower(p.location_country) IN (:countries)");
        }
        anyOf(clauses, criteria.linkedin().stream().map(presence -> switch (presence) {
            case YES -> "btrim(coalesce(p.linkedin_url, '')) <> ''";
            case NO -> "btrim(coalesce(p.linkedin_url, '')) = ''";
        }).toList());
        anyOf(clauses, criteria.contacts().stream().map(contact -> switch (contact) {
            case EMAIL -> HOLDS_CONTACT.formatted("'EMAIL'");
            case PHONE -> HOLDS_CONTACT.formatted("'PHONE'");
            case NONE -> "NOT " + HOLDS_CONTACT.formatted("'EMAIL', 'PHONE'");
        }).toList());
        anyOf(clauses, criteria.experience().stream().map(PersonPoolQuery::experienceClause).toList());
        anyOf(clauses, criteria.activity().stream().map(band -> recencyClause(LATEST_ACTIVITY_AT, band)).toList());
        anyOf(clauses, seniorityClauses(criteria, params));
        if (!criteria.sources().isEmpty()) {
            params.put("sources", criteria.sources().stream().map(Enum::name).toList());
            clauses.add("p.source IN (:sources)");
        }
        anyOf(clauses, criteria.added().stream().map(band -> recencyClause("p.created_at", band)).toList());
        anyOf(clauses, criteria.doNotContact().stream().map(presence -> switch (presence) {
            case YES -> "p.do_not_contact";
            case NO -> "NOT p.do_not_contact";
        }).toList());
        if (withView && criteria.view() != PoolView.ALL) {
            clauses.add(switch (criteria.view()) {
                case MINE -> OWNED_BY_CALLER;
                case ACTIVE -> IN_ACTIVE_POSITION;
                case UNPLACED -> IN_NO_POSITION;
                case NOT_NOTIFIED -> NOT_NOTIFIED;
                case CONSENT_ASKED -> CONSENT_ASKED;
                case CONSENT_ENDING -> CONSENT_ENDING;
                case CONSENT_REFUSED -> CONSENT_REFUSED;
                case ALL -> "true";
            });
        }
        return String.join(" AND ", clauses);
    }

    /** One mapping on one of the named positions (any, when none is) at any of the statuses or stages asked for. */
    private static String mappingClause(PoolCriteria criteria, Map<String, Object> params) {
        StringBuilder mapping = new StringBuilder(
                "EXISTS (SELECT 1 FROM app_lm_project_candidate m WHERE m.person_id = p.id");
        if (!criteria.projectIds().isEmpty()) {
            params.put("projectIds", criteria.projectIds());
            mapping.append(" AND m.project_id IN (:projectIds)");
        }
        List<String> statusSets = new ArrayList<>();
        if (!criteria.statuses().isEmpty()) {
            params.put("statusKeys", criteria.statuses());
            statusSets.add("SELECT s.id FROM app_lm_workspace_candidate_status s"
                    + " WHERE s.workspace_id = :workspaceId AND s.key IN (:statusKeys)");
        }
        if (!criteria.pipelineStages().isEmpty()) {
            params.put("stageKeys", criteria.pipelineStages());
            statusSets.add("SELECT s.id FROM app_lm_workspace_candidate_status s"
                    + " JOIN app_lm_workspace_candidate_stage g ON g.id = s.stage_id"
                    + " WHERE s.workspace_id = :workspaceId AND g.key IN (:stageKeys)");
        }
        if (!statusSets.isEmpty()) {
            mapping.append(" AND m.status_id IN (").append(String.join(" UNION ", statusSets)).append(")");
        }
        return mapping.append(")").toString();
    }

    private static List<String> ownerClauses(PoolCriteria criteria, Map<String, Object> params) {
        List<String> owners = new ArrayList<>();
        if (!criteria.ownerUserIds().isEmpty()) {
            params.put("owners", criteria.ownerUserIds());
            owners.add("p.owner_user_id IN (:owners)");
        }
        if (criteria.unowned()) {
            owners.add("p.owner_user_id IS NULL");
        }
        return owners;
    }

    private static List<String> seniorityClauses(PoolCriteria criteria, Map<String, Object> params) {
        List<String> tiers = new ArrayList<>();
        if (!criteria.seniorities().isEmpty()) {
            params.put("seniorities", criteria.seniorities().stream().map(Enum::name).toList());
            tiers.add("p.seniority_level IN (:seniorities)");
        }
        if (criteria.seniorityUnrecorded()) {
            tiers.add("p.seniority_level IS NULL");
        }
        return tiers;
    }

    private static String experienceClause(PoolExperienceBand band) {
        if (band == PoolExperienceBand.UNKNOWN) {
            return "p.years_experience IS NULL";
        }
        List<String> bounds = new ArrayList<>();
        if (band.fromYears() != null) {
            bounds.add("p.years_experience >= " + band.fromYears());
        }
        if (band.belowYears() != null) {
            bounds.add("p.years_experience < " + band.belowYears());
        }
        return String.join(" AND ", bounds);
    }

    /** {@code column} inside the band's window; the days are the enum's own constants, never request input. */
    private static String recencyClause(String column, PoolRecencyBand band) {
        return switch (band) {
            case PAST_WEEK, PAST_MONTH, PAST_QUARTER ->
                    column + " >= now() - make_interval(days => " + band.windowDays() + ")";
            case OLDER -> column + " < now() - make_interval(days => " + PoolRecencyBand.PAST_QUARTER.windowDays() + ")";
            case NEVER -> column + " IS NULL";
        };
    }

    /** The values of one column filter, any of which keeps a person. */
    private static void anyOf(List<String> clauses, List<String> alternatives) {
        if (alternatives.size() == 1) {
            clauses.add(alternatives.getFirst());
        } else if (alternatives.size() > 1) {
            clauses.add("((" + String.join(") OR (", alternatives) + "))");
        }
    }

    private static String likeOf(String text) {
        return "%" + LikePatterns.escape(text) + "%";
    }

    private static String orderOf(PoolCriteria criteria) {
        String direction = criteria.ascending() ? "ASC" : "DESC";
        String key = switch (criteria.sort()) {
            case NAME -> "lower(p.full_name) " + direction;
            case TITLE -> "lower(p.title) " + direction + " NULLS LAST";
            case COMPANY -> "lower(" + EMPLOYER + ") " + direction + " NULLS LAST";
            case LOCATION -> "lower(p.location_country) " + direction + " NULLS LAST, "
                    + "lower(p.location_city) " + direction + " NULLS LAST";
            case EXPERIENCE -> "p.years_experience " + direction + " NULLS LAST";
            case SENIORITY -> SENIORITY_RANK + " " + direction + " NULLS LAST";
            case ADDED -> "p.created_at " + direction;
            case POSITIONS -> "(SELECT count(*) FROM app_lm_project_candidate m WHERE m.person_id = p.id) "
                    + direction;
            case ACTIVITY -> "(SELECT max(a.id) FROM app_lm_person_activity a WHERE a.person_id = p.id) "
                    + direction + " NULLS LAST";
        };
        return key + ", p.id";
    }
}
