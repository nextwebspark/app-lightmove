package app.lightmove.api.candidate.service;

import app.lightmove.api.candidate.model.Candidate;
import app.lightmove.api.candidate.model.CandidateCapturedEvent;
import app.lightmove.api.candidate.repository.PersonPhotoRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/** A profile corrected to another person's: the old one's photo goes, and the new one is researched as a capture is. */
@Component
@RequiredArgsConstructor
class CorrectedProfileResearch {

    private final PersonPhotoRepository photos;
    private final ApplicationEventPublisher events;

    void researchAgain(Candidate candidate, String linkedinUrl) {
        photos.findByPersonId(candidate.getPerson().getId()).ifPresent(photos::delete);
        events.publishEvent(new CandidateCapturedEvent(candidate.getId(), candidate.getProjectId(), linkedinUrl));
    }
}
