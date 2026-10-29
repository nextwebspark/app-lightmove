package app.lightmove.api.candidate.model;

import static org.assertj.core.api.Assertions.assertThat;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("An executive's own Companies stage (#903)")
class CandidateTriageStatusTest {

    private static final CandidateDetails DETAILS = new CandidateDetails("Khaled Al Beirouty", null, null, null,
            null, null, null, null, null, null, null, null, null, null, null, null, null);

    @Test
    void starts_at_the_stage_of_the_company_it_is_filed_at() {
        Candidate candidate = filedAt(UUID.randomUUID(), TriageCompanyStatus.DECLINED);

        assertThat(candidate.getTriageStatus()).isEqualTo(TriageCompanyStatus.DECLINED);
    }

    @Test
    void moves_alone_and_says_whether_anything_changed() {
        Candidate candidate = filedAt(UUID.randomUUID(), TriageCompanyStatus.IN_UNIVERSE);

        assertThat(candidate.moveToStage(TriageCompanyStatus.DECLINED)).isTrue();
        assertThat(candidate.moveToStage(TriageCompanyStatus.DECLINED)).isFalse();
        assertThat(candidate.getTriageStatus()).isEqualTo(TriageCompanyStatus.DECLINED);
    }

    @Test
    void keeps_their_own_stage_while_staying_at_the_same_company() {
        UUID companyId = UUID.randomUUID();
        Candidate candidate = filedAt(companyId, TriageCompanyStatus.IN_UNIVERSE);
        candidate.moveToStage(TriageCompanyStatus.DECLINED);

        candidate.remapTo(companyId, TriageCompanyStatus.IN_UNIVERSE);

        assertThat(candidate.getTriageStatus()).isEqualTo(TriageCompanyStatus.DECLINED);
    }

    @Test
    void takes_the_stage_of_a_new_company() {
        Candidate candidate = filedAt(UUID.randomUUID(), TriageCompanyStatus.IN_UNIVERSE);
        candidate.moveToStage(TriageCompanyStatus.DECLINED);

        candidate.remapTo(UUID.randomUUID(), TriageCompanyStatus.IN_UNIVERSE);

        assertThat(candidate.getTriageStatus()).isEqualTo(TriageCompanyStatus.IN_UNIVERSE);
    }

    private static Candidate filedAt(UUID triageCompanyId, TriageCompanyStatus companyStatus) {
        Person person = Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.MANUAL, DETAILS);
        return Candidate.mapped(UUID.randomUUID(), UUID.randomUUID(), triageCompanyId, companyStatus, person,
                CandidateSource.MANUAL, PipelineTestStatuses.identified(), DETAILS);
    }
}
