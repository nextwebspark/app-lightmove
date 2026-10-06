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

    /** {@code triageCompanyIds} wins over {@code unmapped}; a blank {@code nameQuery} matches everyone. */
    public static Specification<Candidate> of(UUID projectId, @Nullable CandidateStatus status,
                                              @Nullable Collection<UUID> triageCompanyIds, boolean unmapped,
                                              String nameQuery) {
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
            if (!nameQuery.isBlank()) {
                Join<Candidate, Person> person = root.join("person");
                where.add(cb.like(cb.lower(person.get("fullName")),
                        "%" + escapeLike(nameQuery.toLowerCase(Locale.ROOT)) + "%", '\\'));
            }
            return cb.and(where.toArray(Predicate[]::new));
        };
    }

    private static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
