package app.lightmove.api.candidate.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import app.lightmove.api.candidate.constant.CandidateSource;
import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCompensation;
import app.lightmove.api.candidate.model.CandidateDetails;
import app.lightmove.api.candidate.model.CandidateProfile;
import app.lightmove.api.candidate.model.CandidateProfileCorrectedEvent;
import app.lightmove.api.candidate.model.Person;
import app.lightmove.api.candidate.model.PipelineTestStatuses;
import app.lightmove.api.triagecompany.constant.TriageCompanyStatus;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

/** Which saves of a corrected LinkedIn address ask for research: the drawer's, of a researched person, to another human. */
class CorrectedProfileResearchTest {

    private static final String OLD = "https://www.linkedin.com/in/jane-doe";
    private static final String CORRECTED = "https://www.linkedin.com/in/john-roe";

    private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
    private final CorrectedProfileResearch research = new CorrectedProfileResearch(events);

    @Test
    @DisplayName("the drawer correcting a researched person to another human asks for that profile's research")
    void theDrawersCorrectionAsks() {
        research.researchIfCorrected(at(CORRECTED), true, OLD, ContactSource.MANUAL);

        verify(events).publishEvent(any(CandidateProfileCorrectedEvent.class));
    }

    @Test
    @DisplayName("a spreadsheet's address, an unresearched person or a member-id address asks for nothing")
    void nothingElseAsks() {
        research.researchIfCorrected(at(CORRECTED), true, OLD, ContactSource.CSV);
        research.researchIfCorrected(at(CORRECTED), false, OLD, ContactSource.MANUAL);
        research.researchIfCorrected(at(CORRECTED), true, "https://www.linkedin.com/in/ACoAABcdEfGhIjKlMnOpQrSt",
                ContactSource.MANUAL);
        research.researchIfCorrected(at(OLD + "/?trk=public"), true, OLD, ContactSource.MANUAL);

        verify(events, never()).publishEvent(any(Object.class));
    }

    private static Candidate at(String linkedinUrl) {
        CandidateDetails details = new CandidateDetails("Jane Doe", null, null, null, null, null, linkedinUrl,
                null, null, null, null, null, null, null, CandidateCompensation.unknown(), CandidateProfile.empty(),
                null);
        Person person = Person.founded(UUID.randomUUID(), UUID.randomUUID(), CandidateSource.MANUAL, details);
        return Candidate.mapped(UUID.randomUUID(), UUID.randomUUID(), null, TriageCompanyStatus.IN_UNIVERSE, person,
                CandidateSource.MANUAL, PipelineTestStatuses.identified(), details);
    }
}
