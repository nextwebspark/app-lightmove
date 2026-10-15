package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCareerEntry;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PersonEmployer;
import app.lightmove.api.candidate.model.ResearchedEmployerMark;
import app.lightmove.api.triagecompany.model.CapturedCompanyDetails;
import app.lightmove.api.triagecompany.service.TriageCompanyReadService;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/** A person's employer: named, with its logo, on the Candidates page, and filed by a further mandate. */
@Service
@RequiredArgsConstructor
public class PersonEmployerResolver {

    private final TriageCompanyReadService triageCompanies;

    /**
     * What their most recent position recorded, else the first post of their career. Research's logo is
     * offered only when research named that same employer, so a stale one never sits beside a new name.
     */
    static PersonEmployer employerOf(Person person, List<Candidate> mappedOldestFirst) {
        ResearchedEmployerMark researched = person.getProfile().employer();
        for (int index = mappedOldestFirst.size() - 1; index >= 0; index--) {
            Candidate row = mappedOldestFirst.get(index);
            String recorded = row.getCompanyName();
            if (recorded != null && !recorded.isBlank()) {
                return new PersonEmployer(recorded, row.getTriageCompanyId(), researchedLogoOf(researched, recorded));
            }
        }
        return person.getProfile().career().stream()
                .map(CandidateCareerEntry::company).filter(Objects::nonNull).findFirst()
                .map(company -> new PersonEmployer(company, null, researchedLogoOf(researched, company)))
                .orElse(PersonEmployer.NONE);
    }

    /**
     * Where a further mandate files someone it does not research again: the employer their most recent
     * position recorded — through a company row naming it where one exists, whose LinkedIn page lets the
     * market match the same company again — else the employer research named. Never the career's first
     * post, which {@link #employerOf} may fall back to and which may be one they have left.
     */
    Optional<CapturedCompanyDetails> knownEmployerOf(UUID workspaceId, Person person,
                                                     List<Candidate> mappedOldestFirst) {
        ResearchedEmployerMark researched = person.getProfile().employer();
        List<Candidate> newestFirst = mappedOldestFirst.reversed();
        Optional<String> recorded = newestFirst.stream()
                .map(Candidate::getCompanyName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst();
        if (recorded.isEmpty()) {
            return Optional.ofNullable(researched)
                    .filter(mark -> mark.name() != null)
                    .map(mark -> CapturedCompanyDetails.employerNamed(mark.name(), null, mark.logoUrl()));
        }
        String name = recorded.get();
        return newestFirst.stream()
                .filter(row -> row.getTriageCompanyId() != null && name.equalsIgnoreCase(row.getCompanyName()))
                .findFirst()
                .map(row -> triageCompanies.get(workspaceId, row.getProjectId(), row.getTriageCompanyId()))
                .map(company -> CapturedCompanyDetails.employerNamed(company.companyName(),
                        company.companyLinkedinUrl(), company.logoUrl()))
                .or(() -> Optional.of(CapturedCompanyDetails.employerNamed(name, null,
                        researchedLogoOf(researched, name))));
    }

    /** Keyed by person id: the position's company row's logo, else research's; a person with neither is absent. */
    Map<UUID, String> logosOf(UUID workspaceId, Map<UUID, PersonEmployer> employers) {
        Map<UUID, String> filed = triageCompanies.logoUrlsOf(workspaceId, employers.values().stream()
                .map(PersonEmployer::triageCompanyId).filter(Objects::nonNull).distinct().toList());
        return employers.entrySet().stream()
                .filter(entry -> logoOf(entry.getValue(), filed) != null)
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> logoOf(entry.getValue(), filed)));
    }

    private static String logoOf(PersonEmployer employer, Map<UUID, String> filed) {
        String onRow = employer.triageCompanyId() == null ? null : filed.get(employer.triageCompanyId());
        return onRow != null ? onRow : employer.researchedLogoUrl();
    }

    private static String researchedLogoOf(ResearchedEmployerMark researched, String employer) {
        return researched != null && researched.names(employer) ? researched.logoUrl() : null;
    }
}
