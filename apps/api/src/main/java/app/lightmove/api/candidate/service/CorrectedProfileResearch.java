package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.constant.ContactSource;
import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateProfileCorrectedEvent;
import app.lightmove.api.core.text.service.LinkedInUrls;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * A researched person whose address a researcher corrected, in the drawer, to another human's: the new profile is
 * researched, and its answer replaces the old research only once it lands, so a typo or a failed lookup loses nothing.
 * A spreadsheet's address asks for nothing: an import's enrichment is its own tick box.
 */
@Component
@RequiredArgsConstructor
class CorrectedProfileResearch {

    private final ApplicationEventPublisher events;

    void researchIfCorrected(Candidate candidate, boolean wasResearched, String previousUrl, ContactSource door) {
        String correctedUrl = candidate.getPerson().getLinkedinUrl();
        if (door == ContactSource.MANUAL && wasResearched && LinkedInUrls.namesAnotherPerson(previousUrl, correctedUrl)) {
            events.publishEvent(new CandidateProfileCorrectedEvent(candidate.getId(), candidate.getProjectId(),
                    correctedUrl));
        }
    }
}
