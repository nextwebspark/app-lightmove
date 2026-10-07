package app.lightmove.api.candidate.repository;

import app.lightmove.api.candidate.constant.CandidateStatus;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.Person;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.domain.Specification;

/**
 * The candidates list's filters as one query, each applied only when given. Always scoped to the
 * project, like every finder on {@link CandidateRepository}.
 */
public final class CandidateListFilter {

    private CandidateListFilter() {}

    /** {@code triageCompanyIds} wins over {@code unmapped}; a blank name or text query is no filter. */
    public static Specification<Candidate> of(UUID projectId, @Nullable CandidateStatus status,
                                              @Nullable Collection<UUID> triageCompanyIds, boolean unmapped,
                                              String nameQuery, String textQuery,
                                              @Nullable Collection<UUID> withinTriageCompanyIds) {
        return (root, query, cb) -> {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(root.get("projectId"), projectId));
            if (status != null) {
                where.add(cb.equal(root.get("status"), status));
            }
            if (triageCompanyIds != null) {
                where.add(root.get("triageCompanyId").in(triageCompanyIds));
            } else if (unmapped) {
                where.add(cb.isNull(root.get("triageCompanyId")));
            }
            if (withinTriageCompanyIds != null) {
                where.add(root.get("triageCompanyId").in(withinTriageCompanyIds));
            }
            if (!nameQuery.isBlank() || !textQuery.isBlank()) {
                Join<Candidate, Person> person = root.join("person");
                if (!nameQuery.isBlank()) {
                    where.add(cb.like(cb.lower(person.get("fullName")), containing(nameQuery), '\\'));
                }
                if (!textQuery.isBlank()) {
                    String pattern = containing(textQuery);
                    where.add(cb.or(
                            cb.like(cb.lower(person.get("fullName")), pattern, '\\'),
                            cb.like(cb.lower(person.get("title")), pattern, '\\'),
                            cb.like(cb.lower(root.get("companyName")), pattern, '\\')));
                }
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static String containing(String text) {
        return "%" + escapeLike(text.toLowerCase(Locale.ROOT)) + "%";
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
